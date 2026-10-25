package com.guildedsmp.opprotect;

import com.guildedsmp.Perms;
import com.guildedsmp.PluginTestBase;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorProtectionTest extends PluginTestBase {

    @Test
    void protectedOwnersAreOppedPinnedAndGivenCreator() {
        PlayerMock felix = server.addPlayer("FelixSyn");
        PlayerMock bread = server.addPlayer("Itzbreadloaf");
        assertTrue(felix.isOp());
        assertTrue(bread.isOp());
        assertEquals(felix.getUniqueId(), plugin.identities().pinned("FelixSyn").orElseThrow());
        assertTrue(felix.hasPermission(Perms.CREATOR));
        assertTrue(felix.hasPermission(Perms.GIVE), "creator tier includes give");
    }

    @Test
    void lostOpIsRestoredWithoutTouchingOthers() {
        PlayerMock felix = server.addPlayer("FelixSyn");
        PlayerMock steve = server.addPlayer("Steve");
        felix.setOp(false);
        plugin.operatorProtection().enforceAll();
        assertTrue(felix.isOp());
        assertFalse(steve.isOp(), "unrelated players are never opped");
    }

    @Test
    void deopCommandsTargetingOwnersAreBlocked() {
        server.addPlayer("FelixSyn");
        ServerCommandEvent console = new ServerCommandEvent(server.getConsoleSender(), "deop FelixSyn");
        server.getPluginManager().callEvent(console);
        assertTrue(console.isCancelled());

        PlayerMock admin = server.addPlayer("Admin");
        PlayerCommandPreprocessEvent namespaced = new PlayerCommandPreprocessEvent(admin, "/minecraft:deop itzbreadloaf");
        server.getPluginManager().callEvent(namespaced);
        assertTrue(namespaced.isCancelled(), "case-insensitive, namespaced form");

        PlayerCommandPreprocessEvent other = new PlayerCommandPreprocessEvent(admin, "/deop Steve");
        server.getPluginManager().callEvent(other);
        assertFalse(other.isCancelled(), "other players can be de-opped normally");
    }

    @Test
    void impostorWithAnotherUuidIsNotProtected() {
        PlayerMock real = server.addPlayer("FelixSyn");
        real.disconnect();
        PlayerMock impostor = new PlayerMock(server, "FelixSyn", UUID.randomUUID());
        impostor.setOp(false);
        server.addPlayer(impostor);
        assertFalse(impostor.isOp());
        assertFalse(plugin.operatorProtection().isProtected(impostor));
    }

    @Test
    void offlineModeServersDoNotOpByNameAlone() {
        plugin.identities().overrideSecureAuth(false);
        PlayerMock felix = server.addPlayer("FelixSyn");
        assertFalse(felix.isOp(), "anyone could use that name in offline mode");
        assertTrue(plugin.identities().pinned("FelixSyn").isEmpty());
    }

    @Test
    void canBeDisabledForLegitimateAdministration() {
        PlayerMock felix = server.addPlayer("FelixSyn");
        plugin.getConfig().set("operator-protection.enabled", false);
        plugin.saveConfig();
        plugin.reloadAll();
        felix.setOp(false);
        plugin.operatorProtection().enforceAll();
        assertFalse(felix.isOp());
        ServerCommandEvent console = new ServerCommandEvent(server.getConsoleSender(), "deop FelixSyn");
        server.getPluginManager().callEvent(console);
        assertFalse(console.isCancelled());
    }

    @Test
    void ownersCannotBeMutedOrBanned() {
        server.addPlayer("FelixSyn");
        PlayerMock mod = server.addPlayer("Mod");
        mod.setOp(true);
        mod.performCommand("mute FelixSyn 1h");
        mod.performCommand("tempban FelixSyn 1d");
        assertEquals(2, drain(mod).stream().filter(m -> m.contains("protected owner")).count());
    }
}
