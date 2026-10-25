package com.guildedsmp.identity;

import com.guildedsmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Maps trusted usernames to the account UUID that first used them on an authenticated server.
 * <p>
 * Usernames alone are not proof of identity: on an offline-mode server anyone can join as any
 * name. So a name is only "pinned" to a UUID when the server verifies accounts (online-mode, or a
 * proxy with verified forwarding). After pinning, only that UUID counts, even if the name changes.
 * An entry can also pin a UUID directly with {@code Name:uuid}.
 */
public final class IdentityRegistry {

    /** A trusted name, optionally with a UUID set in config. */
    public record Entry(String name, UUID configuredUuid) {
        public static Optional<Entry> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String s = raw.trim();
            UUID uuid = null;
            int colon = s.indexOf(':');
            if (colon > 0) {
                try {
                    uuid = UUID.fromString(s.substring(colon + 1).trim());
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
                s = s.substring(0, colon).trim();
            }
            if (!s.matches("[A-Za-z0-9_]{1,16}")) {
                return Optional.empty();
            }
            return Optional.of(new Entry(s, uuid));
        }
    }

    private final YamlStore store;
    private final Logger logger;
    private final Map<String, UUID> pins = new HashMap<>();
    private boolean readOnly;
    private Boolean secureAuthOverride;

    public IdentityRegistry(YamlStore store, Logger logger) {
        this.store = store;
        this.logger = logger;
        load();
    }

    private void load() {
        YamlConfiguration yaml = store.load();
        if (yaml == null) {
            readOnly = true;
            return;
        }
        ConfigurationSection sec = yaml.getConfigurationSection("pins");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                try {
                    pins.put(name.toLowerCase(Locale.ROOT), UUID.fromString(sec.getString(name, "")));
                } catch (IllegalArgumentException e) {
                    logger.warning("identities.yml: invalid UUID for " + name + "; ignored.");
                }
            }
        }
    }

    private void save() {
        if (readOnly) {
            logger.warning("identities.yml is unreadable, so new identity pins are kept in memory only.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, UUID> e : pins.entrySet()) {
            yaml.set("pins." + e.getKey(), e.getValue().toString());
        }
        store.saveAsync(yaml);
    }

    /** For tests only: force the authentication mode. */
    public void overrideSecureAuth(Boolean value) {
        this.secureAuthOverride = value;
    }

    /**
     * True when the server verifies player accounts: online-mode, or a proxy configured to
     * forward verified identities (Velocity modern forwarding or BungeeCord with online-mode).
     */
    // Paper exposes no non-deprecated way to read proxy settings; failures fall back to "offline".
    @SuppressWarnings({"deprecation", "removal"})
    public boolean secureAuthentication() {
        if (secureAuthOverride != null) {
            return secureAuthOverride;
        }
        if (Bukkit.getOnlineMode()) {
            return true;
        }
        try {
            YamlConfiguration paper = Bukkit.spigot().getPaperConfig();
            if (paper.getBoolean("proxies.velocity.enabled") && paper.getBoolean("proxies.velocity.online-mode")) {
                return true;
            }
            YamlConfiguration spigot = Bukkit.spigot().getSpigotConfig();
            if (spigot.getBoolean("settings.bungeecord") && paper.getBoolean("proxies.bungee-cord.online-mode", true)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Not available on this server implementation; treat as offline.
        }
        return false;
    }

    public Optional<UUID> pinned(String name) {
        return Optional.ofNullable(pins.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Checks whether the player is the trusted account for one of the given entries.
     * Pins the name to the player's UUID on first sight when authentication is secure
     * (or when {@code allowUnverified} is set).
     */
    public boolean matches(Player player, Collection<Entry> entries, boolean allowUnverified) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        for (Entry entry : entries) {
            if (entry.configuredUuid() != null) {
                if (entry.configuredUuid().equals(uuid)) {
                    return true;
                }
                continue;
            }
            UUID pin = pins.get(entry.name().toLowerCase(Locale.ROOT));
            if (pin != null) {
                if (pin.equals(uuid)) {
                    return true;
                }
                continue;
            }
            if (entry.name().equalsIgnoreCase(name)) {
                boolean secure = secureAuthentication();
                if (secure) {
                    pins.put(entry.name().toLowerCase(Locale.ROOT), uuid);
                    save();
                    logger.info("Pinned trusted name " + entry.name() + " to account " + uuid + ".");
                    return true;
                }
                return allowUnverified;
            }
        }
        return false;
    }

    /** Trusted accounts that are known by UUID (configured or pinned), for offline checks. */
    public Map<String, UUID> knownAccounts(Collection<Entry> entries) {
        Map<String, UUID> out = new HashMap<>();
        for (Entry entry : entries) {
            UUID uuid = entry.configuredUuid() != null ? entry.configuredUuid() : pins.get(entry.name().toLowerCase(Locale.ROOT));
            if (uuid != null) {
                out.put(entry.name(), uuid);
            }
        }
        return out;
    }

    /** True when the name (case-insensitive) is one of the entries, or the offline player is a trusted UUID. */
    public boolean isTrustedName(String name, Collection<Entry> entries) {
        for (Entry e : entries) {
            if (e.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean isTrustedAccount(OfflinePlayer player, Collection<Entry> entries) {
        UUID uuid = player.getUniqueId();
        if (knownAccounts(entries).containsValue(uuid)) {
            return true;
        }
        String name = player.getName();
        return name != null && isTrustedName(name, entries);
    }
}
