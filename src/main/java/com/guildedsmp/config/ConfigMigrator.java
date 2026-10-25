package com.guildedsmp.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Adds keys that are new in this plugin version to an existing config.yml without
 * touching any value the server owner already set. A timestamped backup is written
 * before anything changes. User-owned maps (drop tables, home limits, recipe layouts)
 * are only filled in when the whole map is missing, so deliberate removals stick.
 */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 1;

    /** Sections whose children belong to the server owner once the section exists. */
    private static final List<String> USER_OWNED = List.of(
            "drops.table", "homes.limits", "recipes.inferno-blade", "recipes.frostbite", "recipes.void-reaper",
            "operator-protection.protected-players", "tablist.header-extra-lines");

    private ConfigMigrator() {
    }

    /** @return the list of keys that were added (empty when nothing changed). */
    public static List<String> migrate(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            plugin.saveDefaultConfig();
            return List.of();
        }
        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "config.yml could not be parsed. Fix the YAML error; "
                    + "built-in defaults are used until then and your file is left untouched.", e);
            return List.of();
        }
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) {
                return List.of();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Bundled config.yml is unreadable", e);
            return List.of();
        }

        List<String> added = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (isInsideUserOwned(key, current)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!current.isSet(key)) {
                current.set(key, defaults.get(key));
                current.setComments(key, defaults.getComments(key));
                added.add(key);
            }
        }
        int version = current.getInt("config-version", 0);
        if (added.isEmpty() && version >= CURRENT_VERSION) {
            return List.of();
        }
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.copy(file.toPath(), new File(file.getParentFile(), "config.yml.bak-" + stamp).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            current.set("config-version", Math.max(version, CURRENT_VERSION));
            current.save(file);
            if (!added.isEmpty()) {
                plugin.getLogger().info("Updated config.yml with " + added.size() + " new setting(s); a backup was saved.");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not update config.yml; existing settings were not changed.", e);
        }
        return added;
    }

    private static boolean isInsideUserOwned(String key, YamlConfiguration current) {
        for (String owned : USER_OWNED) {
            if (key.equals(owned) || key.startsWith(owned + ".")) {
                // Only skip when the owner already has this section (or list) in their file.
                return current.isSet(owned);
            }
        }
        return false;
    }
}
