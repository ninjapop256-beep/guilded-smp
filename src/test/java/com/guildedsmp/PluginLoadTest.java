package com.guildedsmp;

import com.guildedsmp.items.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoadTest extends PluginTestBase {

    @Test
    void enablesWithoutConfigWarnings() {
        assertTrue(plugin.isEnabled());
        assertTrue(plugin.settings().warnings().isEmpty(), "default config warnings: " + plugin.settings().warnings());
    }

    @Test
    void everyCommandHasAnExecutorAndCompleter() {
        for (String name : List.of("tpa", "tpaccept", "tpdeny", "tpacancel", "home", "sethome", "delhome", "homes",
                "spawn", "spawnprotect", "mute", "unmute", "tempban", "unban", "guilded")) {
            PluginCommand cmd = plugin.getCommand(name);
            assertNotNull(cmd, name);
            assertSame(plugin, cmd.getPlugin());
            assertNotNull(cmd.getExecutor(), name + " executor");
            assertNotNull(cmd.getTabCompleter(), name + " completer");
        }
    }

    @Test
    void legendaryRecipesAreRegistered() {
        for (CustomItem item : CustomItem.legendaries()) {
            assertNotNull(Bukkit.getRecipe(new NamespacedKey(plugin, item.id)), item.id);
        }
    }
}
