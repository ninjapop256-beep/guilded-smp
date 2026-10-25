package com.guildedsmp.rules;

import com.guildedsmp.PluginTestBase;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerCraftRulesTest extends PluginTestBase {

    @Test
    void anyoneMayCraftUntilTheSingleMaceExists() {
        PlayerMock steve = server.addPlayer("Steve");
        PlayerMock felix = server.addPlayer("FelixSyn");
        assertTrue(plugin.ownerRules().canCraftNow(steve), "ordinary players may craft");
        assertTrue(plugin.ownerRules().canCraftNow(felix));
        felix.performCommand("guilded mace limit 0");
        assertFalse(plugin.ownerRules().canCraftNow(steve));
        felix.performCommand("guilded mace limit 1");
        felix.performCommand("guilded mace reset");
        assertEquals(0, plugin.ownerRules().crafted());
        assertTrue(plugin.ownerRules().canCraftNow(steve));
    }

    @Test
    void nobodyElseCanDiscoverAnythingAboutIt() {
        PlayerMock steve = server.addPlayer("Steve");
        steve.setOp(true);
        drain(steve);
        steve.performCommand("guilded mace");
        List<String> said = drain(steve);
        assertEquals(1, said.size());
        assertTrue(said.get(0).contains("Unknown subcommand"), "same reply as any unknown subcommand");
        assertFalse(plugin.getCommand("guilded").tabComplete(steve, "guilded", new String[]{"m"}).contains("mace"));


        PlayerMock felix = server.addPlayer("FelixSyn");
        assertTrue(plugin.getCommand("guilded").tabComplete(felix, "guilded", new String[]{"m"}).contains("mace"));
    }

    @Test
    void playerFacingFilesNeverMentionIt() throws IOException {
        for (String resource : List.of("plugin.yml", "config.yml")) {
            try (InputStream in = plugin.getResource(resource)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                assertFalse(text.contains("mace"), resource);
            }
        }
        Path readme = Path.of("README.md");
        if (Files.exists(readme)) {
            assertFalse(Files.readString(readme).toLowerCase(Locale.ROOT).contains("mace"), "README.md");
        }
    }
}
