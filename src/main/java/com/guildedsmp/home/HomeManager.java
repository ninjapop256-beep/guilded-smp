package com.guildedsmp.home;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.storage.StoredLocation;
import com.guildedsmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Homes per player, stored in data/homes.yml. All homes are kept in memory (they are small) and
 * written to disk after every change. Malformed entries are skipped on load but preserved in the
 * file until the owner changes their homes, so a bad edit never silently deletes data.
 */
public final class HomeManager {

    public static final String DEFAULT_NAME = "home";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

    public enum SetResult { CREATED, UPDATED, INVALID_NAME, LIMIT_REACHED }

    private final GuildedSMP plugin;
    private final YamlStore store;
    private final Map<UUID, Map<String, StoredLocation>> homes = new HashMap<>();
    private final Map<UUID, String> lastKnownNames = new HashMap<>();
    private YamlConfiguration raw;
    private boolean readOnly;

    public HomeManager(GuildedSMP plugin, YamlStore store) {
        this.plugin = plugin;
        this.store = store;
        load();
    }

    private void load() {
        homes.clear();
        raw = store.load();
        if (raw == null) {
            readOnly = true;
            raw = new YamlConfiguration();
            return;
        }
        for (String key : raw.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("homes.yml: '" + key + "' is not a player UUID; skipped.");
                continue;
            }
            ConfigurationSection player = raw.getConfigurationSection(key);
            if (player == null) {
                continue;
            }
            String name = player.getString("name");
            if (name != null) {
                lastKnownNames.put(id, name);
            }
            ConfigurationSection list = player.getConfigurationSection("homes");
            if (list == null) {
                continue;
            }
            Map<String, StoredLocation> map = new LinkedHashMap<>();
            for (String homeName : list.getKeys(false)) {
                StoredLocation loc = StoredLocation.read(list.getConfigurationSection(homeName));
                if (loc == null) {
                    plugin.getLogger().warning("homes.yml: home '" + homeName + "' of " + key + " is malformed; skipped.");
                    continue;
                }
                map.put(homeName.toLowerCase(Locale.ROOT), loc);
            }
            homes.put(id, map);
        }
    }

    public boolean isValidName(String name) {
        return name != null && name.length() <= plugin.settings().homes.maxNameLength()
                && NAME.matcher(name).matches();
    }

    public static String normalize(String name) {
        return name == null ? DEFAULT_NAME : name.toLowerCase(Locale.ROOT);
    }

    public Map<String, StoredLocation> homes(UUID player) {
        return Collections.unmodifiableMap(homes.getOrDefault(player, Map.of()));
    }

    public StoredLocation get(UUID player, String name) {
        return homes.getOrDefault(player, Map.of()).get(normalize(name));
    }

    /** The home limit from config: the highest of default and any guilded.homes.limit.&lt;key&gt; held. */
    public int limit(Player p) {
        if (p.hasPermission(Perms.HOMES_UNLIMITED)) {
            return Integer.MAX_VALUE;
        }
        int limit = plugin.settings().homes.defaultLimit();
        for (Map.Entry<String, Integer> e : plugin.settings().homes.permissionLimits().entrySet()) {
            if (p.hasPermission(Perms.HOMES_LIMIT_PREFIX + e.getKey())) {
                limit = Math.max(limit, e.getValue());
            }
        }
        return limit;
    }

    public SetResult set(Player p, String rawName, StoredLocation location) {
        String name = normalize(rawName);
        if (!isValidName(name)) {
            return SetResult.INVALID_NAME;
        }
        Map<String, StoredLocation> map = homes.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
        boolean exists = map.containsKey(name);
        if (!exists && map.size() >= limit(p)) {
            return SetResult.LIMIT_REACHED;
        }
        map.put(name, location);
        lastKnownNames.put(p.getUniqueId(), p.getName());
        persist(p.getUniqueId());
        return exists ? SetResult.UPDATED : SetResult.CREATED;
    }

    public boolean delete(Player p, String rawName) {
        Map<String, StoredLocation> map = homes.get(p.getUniqueId());
        if (map == null || map.remove(normalize(rawName)) == null) {
            return false;
        }
        persist(p.getUniqueId());
        return true;
    }

    private void persist(UUID id) {
        if (readOnly) {
            plugin.getLogger().severe("homes.yml could not be read at startup, so home changes are NOT being saved "
                    + "(to protect the original file). Fix or remove the .corrupt file and restart.");
            return;
        }
        String key = id.toString();
        raw.set(key, null);
        Map<String, StoredLocation> map = homes.get(id);
        if (map != null && !map.isEmpty()) {
            ConfigurationSection player = raw.createSection(key);
            player.set("name", lastKnownNames.get(id));
            ConfigurationSection list = player.createSection("homes");
            for (Map.Entry<String, StoredLocation> e : map.entrySet()) {
                e.getValue().write(list.createSection(e.getKey()));
            }
        }
        store.saveAsync(raw);
    }
}
