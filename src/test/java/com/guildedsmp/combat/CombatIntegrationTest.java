package com.guildedsmp.combat;

import com.guildedsmp.PluginTestBase;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Combat tagging through the real event listeners, far away from spawn. */
class CombatIntegrationTest extends PluginTestBase {

    private PlayerMock alice;
    private PlayerMock bob;

    @BeforeEach
    void players() {
        alice = player("Alice", 1000.5, 1000.5);
        bob = player("Bob", 1001.5, 1000.5);
    }

    @Test
    void meleeHitTagsBothPlayers() {
        EntityDamageByEntityEvent e = melee(alice, bob, 4.0);
        assertFalse(e.isCancelled());
        assertTrue(plugin.combat().isTagged(alice));
        assertTrue(plugin.combat().isTagged(bob));
    }

    @Test
    @SuppressWarnings("removal")
    void playerArrowTagsTheShooter() {
        Arrow arrow = world.spawn(bob.getLocation().add(0, 1, -1), Arrow.class);
        arrow.setShooter(alice);
        EntityDamageByEntityEvent e = new EntityDamageByEntityEvent(arrow, bob, EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.ARROW).withCausingEntity(alice).withDirectEntity(arrow).build(), 5.0);
        server.getPluginManager().callEvent(e);
        assertTrue(plugin.combat().isTagged(alice));
        assertTrue(plugin.combat().isTagged(bob));
    }

    @Test
    void mobsZeroDamageSelfDamageAndCreativeDoNotTag() {
        Zombie zombie = world.spawn(new Location(world, 1002, 64, 1000), Zombie.class);
        melee(zombie, bob, 3.0);
        assertFalse(plugin.combat().isTagged(bob), "mob hit");

        melee(alice, bob, 0.0);
        assertFalse(plugin.combat().isTagged(bob), "zero damage");

        melee(alice, alice, 2.0);
        assertFalse(plugin.combat().isTagged(alice), "self damage");

        alice.setGameMode(GameMode.CREATIVE);
        melee(alice, bob, 2.0);
        assertFalse(plugin.combat().isTagged(bob), "creative attacker");
    }

    @Test
    void combatLoggingKillsTheTaggedPlayerOnce() {
        melee(alice, bob, 2.0);
        assertTrue(plugin.combat().isTagged(bob));
        bob.disconnect();
        assertTrue(bob.isDead() || bob.getHealth() <= 0.0, "combat logger should die");
        assertFalse(plugin.combat().isTagged(bob), "state cleared");
    }

    @Test
    void untaggedOrClearedPlayersAreNotPunished() {
        double hp = bob.getHealth();
        bob.disconnect();
        assertEquals(hp, bob.getHealth(), "untagged disconnect");

        melee(alice, alice, 1.0);
        PlayerMock carol = player("Carol", 1003.5, 1000.5);
        melee(alice, carol, 2.0);
        plugin.combat().clear(carol, false);
        double carolHp = carol.getHealth();
        carol.disconnect();
        assertEquals(carolHp, carol.getHealth(), "expired/cleared tag");
    }

    @Test
    void serverShutdownIsNeverCombatLogging() {
        melee(alice, bob, 2.0);
        double hp = bob.getHealth();
        server.stopping = true;
        bob.disconnect();
        assertEquals(hp, bob.getHealth());
    }

    @Test
    void blockedCommandsAndOwnTeleportsAreRefusedWhileTagged() {
        alice.performCommand("sethome");
        alice.setOp(true);
        alice.performCommand("spawn set");
        alice.setOp(false);
        // Leave the new spawn's protected zone before fighting.
        floor(5000, 5000);
        floor(5001, 5000);
        alice.teleport(new Location(world, 5000.5, 64, 5000.5));
        bob.teleport(new Location(world, 5001.5, 64, 5000.5));
        melee(alice, bob, 2.0);
        PlayerCommandPreprocessEvent warp = new PlayerCommandPreprocessEvent(alice, "/warp shop");
        server.getPluginManager().callEvent(warp);
        assertTrue(warp.isCancelled());

        alice.performCommand("home");
        alice.performCommand("spawn");
        alice.performCommand("tpa Bob");
        List<String> msgs = drain(alice);
        assertEquals(3, msgs.stream().filter(m -> m.contains("can't teleport while in combat")).count(),
                "home, spawn and tpa must all be refused, got " + msgs);
    }

    @Test
    void enteringCombatCancelsAPendingTeleport() {
        alice.setOp(true);
        alice.performCommand("spawn set"); // spawn at Alice's spot (1000, 64, 1000)
        alice.setOp(false);
        // Move both players far away from spawn and its protected zone.
        floor(5000, 5000);
        floor(5001, 5000);
        alice.teleport(new Location(world, 5000.5, 64, 5000.5));
        bob.teleport(new Location(world, 5001.5, 64, 5000.5));
        drain(alice);

        alice.performCommand("spawn");
        assertTrue(plugin.teleports().hasPending(alice), "warm-up started");
        melee(bob, alice, 2.0);
        assertFalse(plugin.teleports().hasPending(alice), "combat cancels the warm-up");
        server.getScheduler().performTicks(100);
        assertEquals(5000, alice.getLocation().getBlockX(), "must not have teleported");
        assertTrue(said(drain(alice), "you entered combat"));
    }
}
