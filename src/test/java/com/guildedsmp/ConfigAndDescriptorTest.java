package com.guildedsmp;

import com.guildedsmp.config.ConfigMigrator;
import com.guildedsmp.config.Settings;
import com.guildedsmp.storage.IoExecutor;
import com.guildedsmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAndDescriptorTest extends PluginTestBase {

    private YamlConfiguration descriptor() throws Exception {
        try (var in = plugin.getResource("plugin.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void descriptorTargets262AndProtectsPowerfulNodes() throws Exception {
        YamlConfiguration yml = descriptor();
        assertEquals("26.2", yml.getString("api-version"));
        assertEquals("com.guildedsmp.GuildedSMP", yml.getString("main"));
        ConfigurationSection perms = yml.getConfigurationSection("permissions");
        assertEquals("false", perms.getString("guilded.creator.default"));
        assertEquals("false", perms.getString("guilded.give.default"));
        assertEquals("false", perms.getString("guilded.combat.bypass.default"));
        assertFalse(perms.getConfigurationSection("guilded.admin.children").contains("guilded.combat.bypass"),
                "the combat emergency override must be granted explicitly");
        assertEquals("true", perms.getString("guilded.player.default"));
        Permission give = server.getPluginManager().getPermission("guilded.give");
        assertNotNull(give);
        assertEquals(PermissionDefault.FALSE, give.getDefault());
    }

    @Test
    void migrationAddsNewKeysKeepsValuesAndBacksUp() throws Exception {
        File cfg = new File(plugin.getDataFolder(), "config.yml");
        Files.writeString(cfg.toPath(), "combat:\n  tag-duration-seconds: 45\ndrops:\n  table:\n    inferno_core:\n      BLAZE: 0.5\n");
        List<String> added = ConfigMigrator.migrate(plugin);
        assertTrue(added.contains("teleport.warmup-seconds"));
        YamlConfiguration after = YamlConfiguration.loadConfiguration(cfg);
        assertEquals(45, after.getInt("combat.tag-duration-seconds"), "owner's value kept");
        assertEquals(1, after.getConfigurationSection("drops.table").getKeys(false).size(),
                "owner's drop table is not refilled with entries they removed");
        assertEquals(ConfigMigrator.CURRENT_VERSION, after.getInt("config-version"));
        File[] backups = plugin.getDataFolder().listFiles((d, n) -> n.startsWith("config.yml.bak-"));
        assertNotNull(backups);
        assertTrue(backups.length >= 1);
    }

    @Test
    void invalidValuesFallBackWithWarnings() {
        YamlConfiguration bad = new YamlConfiguration();
        bad.set("combat.tag-duration-seconds", "lots");
        bad.set("spawn-protection.default-radius", -5);
        bad.set("combat.display", "SIDEWAYS");
        bad.set("legendaries.frostbite.slowness-chance", 4.0);
        bad.set("operator-protection.protected-players", List.of("FelixSyn", "not a name!"));
        Settings s = Settings.load(bad, Logger.getLogger("test"));
        assertEquals(20, s.combat.tagDurationSeconds());
        assertEquals(100, s.protection.defaultRadius());
        assertEquals(Settings.CombatDisplay.ACTION_BAR, s.combat.display());
        assertEquals(0.30, s.legendaries.frostbite().slownessChance());
        assertEquals(List.of("FelixSyn"), s.opProtection.protectedPlayers());
        assertEquals(5, s.warnings().size(), s.warnings().toString());
    }

    @Test
    void corruptDataFilesAreQuarantinedNotOverwritten() throws Exception {
        File dir = new File(plugin.getDataFolder(), "scratch");
        dir.mkdirs();
        File f = new File(dir, "broken.yml");
        Files.writeString(f.toPath(), "this: [is: not: valid");
        IoExecutor io = new IoExecutor(Logger.getLogger("test"));
        YamlStore store = new YamlStore(f, io, Logger.getLogger("test"));
        YamlConfiguration loaded = store.load();
        assertNotNull(loaded);
        assertTrue(loaded.getKeys(false).isEmpty());
        File[] quarantined = dir.listFiles((d, n) -> n.startsWith("broken.yml.corrupt-"));
        assertNotNull(quarantined);
        assertEquals(1, quarantined.length);
        assertEquals("this: [is: not: valid", Files.readString(quarantined[0].toPath()));

        YamlConfiguration fresh = new YamlConfiguration();
        fresh.set("ok", true);
        store.saveAsync(fresh);
        io.shutdown();
        assertTrue(YamlConfiguration.loadConfiguration(f).getBoolean("ok"), "atomic write completed");
        assertFalse(new File(dir, "broken.yml.tmp").exists(), "no temp file left behind");
    }
}
