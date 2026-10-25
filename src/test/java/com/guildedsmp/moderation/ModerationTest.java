package com.guildedsmp.moderation;

import com.guildedsmp.PluginTestBase;
import io.papermc.paper.ban.BanListType;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationTest extends PluginTestBase {

    private PlayerMock mod;
    private PlayerMock steve;

    @BeforeEach
    void setUp() {
        mod = player("Mod");
        mod.setOp(true);
        steve = player("Steve");
    }

    @Test
    void timedMuteBlocksChatAndMessageCommands() {
        mod.performCommand("mute Steve 10m spamming");
        assertTrue(said(drain(mod), "Muted Steve for 10m"));
        assertTrue(said(drain(steve), "Reason: spamming"));
        assertTrue(plugin.mutes().active(steve.getUniqueId()).isPresent());

        PlayerCommandPreprocessEvent msg = new PlayerCommandPreprocessEvent(steve, "/msg Mod hi");
        server.getPluginManager().callEvent(msg);
        assertTrue(msg.isCancelled());

        mod.performCommand("unmute Steve");
        assertTrue(plugin.mutes().active(steve.getUniqueId()).isEmpty());
    }

    @Test
    void regularPlayersCannotModerate() {
        PlayerMock alex = player("Alex");
        alex.performCommand("mute Steve");
        alex.performCommand("tempban Steve 1d");
        assertTrue(plugin.mutes().active(steve.getUniqueId()).isEmpty());
        assertFalse(Bukkit.getBanList(BanListType.PROFILE).isBanned(steve.getPlayerProfile()));
    }

    @Test
    void tempbanBansAndUnbanLifts() {
        mod.performCommand("tempban Steve 2d griefing");
        List<String> out = drain(mod);
        assertTrue(said(out, "Banned Steve for 2d"), out.toString());
        assertTrue(Bukkit.getBanList(BanListType.PROFILE).isBanned(steve.getPlayerProfile()));
        mod.performCommand("unban Steve");
        assertTrue(said(drain(mod), "Unbanned Steve"));
        assertFalse(Bukkit.getBanList(BanListType.PROFILE).isBanned(steve.getPlayerProfile()));
    }

    @Test
    void badDurationsAreRejected() {
        mod.performCommand("tempban Steve forever");
        assertTrue(said(drain(mod), "not a valid duration"));
        mod.performCommand("tempban Steve 400d");
        assertTrue(said(drain(mod), "longest allowed ban"));
        assertFalse(Bukkit.getBanList(BanListType.PROFILE).isBanned(steve.getPlayerProfile()));
    }
}
