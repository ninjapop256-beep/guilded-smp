package com.guildedsmp.moderation;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Mutes, stored in data/mutes.yml. The map is concurrent because chat events are asynchronous;
 * writes to disk are always scheduled from the main thread.
 */
public final class MuteManager {

    /** A mute. {@code until} is epoch millis, or -1 for permanent. */
    public record Mute(UUID player, String name, long until, String reason, String by) {
        public boolean permanent() {
            return until < 0;
        }
    }

    private final GuildedSMP plugin;
    private final YamlStore store;
    private final LongSupplier clock;
    private final Map<UUID, Mute> mutes = new ConcurrentHashMap<>();
    private boolean readOnly;

    public MuteManager(GuildedSMP plugin, YamlStore store) {
        this(plugin, store, System::currentTimeMillis);
    }

    public MuteManager(GuildedSMP plugin, YamlStore store, LongSupplier clock) {
        this.plugin = plugin;
        this.store = store;
        this.clock = clock;
        load();
    }

    private void load() {
        YamlConfiguration yaml = store.load();
        if (yaml == null) {
            readOnly = true;
            return;
        }
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                mutes.put(id, new Mute(id, s.getString("name", "?"), s.getLong("until", -1L),
                        s.getString("reason", ""), s.getString("by", "?")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("mutes.yml: '" + key + "' is not a UUID; skipped.");
            }
        }
    }

    private void save() {
        if (readOnly) {
            plugin.getLogger().severe("mutes.yml could not be read at startup; mute changes are not saved to protect it.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        long now = clock.getAsLong();
        for (Mute m : mutes.values()) {
            if (!m.permanent() && m.until() <= now) {
                continue;
            }
            ConfigurationSection s = yaml.createSection(m.player().toString());
            s.set("name", m.name());
            s.set("until", m.until());
            s.set("reason", m.reason());
            s.set("by", m.by());
        }
        store.saveAsync(yaml);
    }

    private void saveOnMain() {
        if (Bukkit.isPrimaryThread()) {
            save();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, this::save);
        }
    }

    /** Thread-safe. Expired mutes are dropped lazily. */
    public Optional<Mute> active(UUID player) {
        Mute m = mutes.get(player);
        if (m == null) {
            return Optional.empty();
        }
        if (!m.permanent() && m.until() <= clock.getAsLong()) {
            if (mutes.remove(player, m)) {
                saveOnMain();
            }
            return Optional.empty();
        }
        return Optional.of(m);
    }

    public void mute(UUID player, String name, long durationMillis, String reason, String by) {
        long until = durationMillis < 0 ? -1L : clock.getAsLong() + durationMillis;
        mutes.put(player, new Mute(player, name, until, reason == null ? "" : reason, by));
        save();
    }

    public boolean unmute(UUID player) {
        boolean had = mutes.remove(player) != null;
        if (had) {
            save();
        }
        return had;
    }

    public long remainingMillis(Mute m) {
        return m.permanent() ? -1L : Math.max(0, m.until() - clock.getAsLong());
    }
}
