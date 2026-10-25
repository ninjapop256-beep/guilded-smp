package com.guildedsmp.teleport;

import com.guildedsmp.PluginTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpaTest extends PluginTestBase {

    private PlayerMock req;
    private PlayerMock target;

    @BeforeEach
    void players() {
        req = player("Req", 2000.5, 2000.5);
        target = player("Target", 3000.5, 3000.5);
    }

    @Test
    void acceptTeleportsRequesterAfterWarmup() {
        req.performCommand("tpa Target");
        assertTrue(said(drain(req), "Request sent to Target"));
        assertTrue(said(drain(target), "Req wants to teleport to you"));
        target.performCommand("tpaccept");
        assertTrue(said(drain(req), "accepted your request"));
        assertEquals(2000, req.getLocation().getBlockX(), "warm-up first");
        server.getScheduler().performTicks(3 * 20 + 1);
        assertEquals(3000, req.getLocation().getBlockX());
        assertEquals(3000, req.getLocation().getBlockZ());
    }

    @Test
    void denyAndCancel() {
        req.performCommand("tpa Target");
        drain(req);
        target.performCommand("tpdeny Req");
        assertTrue(said(drain(req), "denied your request"));
        assertTrue(plugin.tpa().incoming(target).isEmpty());

        PlayerMock other = player("Other", 2500.5, 2500.5);
        other.performCommand("tpa Target");
        other.performCommand("tpacancel");
        assertTrue(said(drain(other), "was cancelled"));
        assertTrue(said(drain(target), "cancelled their teleport request"));
        target.performCommand("tpaccept");
        assertTrue(said(drain(target), "no pending teleport requests"));
    }

    @Test
    void requestsExpire() {
        req.performCommand("tpa Target");
        drain(req);
        drain(target);
        server.getScheduler().performTicks(60 * 20 + 1);
        assertTrue(said(drain(req), "expired"));
        assertTrue(said(drain(target), "expired"));
        assertTrue(plugin.tpa().outgoing(req).isEmpty());
    }

    @Test
    void selfDuplicateConflictAndCooldown() {
        req.performCommand("tpa Req");
        assertTrue(said(drain(req), "yourself"));
        req.performCommand("tpa Target");
        req.performCommand("tpa Target");
        assertTrue(said(drain(req), "already have a pending request to Target"));
        PlayerMock other = player("Other", 2500.5, 2500.5);
        req.performCommand("tpa Other");
        assertTrue(said(drain(req), "/tpacancel first"));
        req.performCommand("tpacancel");
        req.performCommand("tpa Other");
        assertTrue(said(drain(req), "before sending another request"), "cooldown");
        drain(other);
    }

    @Test
    void combatCancelsRequestsAndBlocksNewOnes() {
        req.performCommand("tpa Target");
        PlayerMock enemy = player("Enemy", 3001.5, 3000.5);
        melee(enemy, target, 2.0);
        assertTrue(plugin.tpa().incoming(target).isEmpty(), "combat start cancels incoming requests");
        assertTrue(said(drain(req), "was cancelled"));
        PlayerMock late = player("Late", 2600.5, 2600.5);
        late.performCommand("tpa Target");
        assertTrue(said(drain(late), "in combat"));
    }

    @Test
    void acceptedTeleportAbortsIfTargetEntersCombatDuringWarmup() {
        req.performCommand("tpa Target");
        target.performCommand("tpaccept");
        PlayerMock enemy = player("Enemy", 3001.5, 3000.5);
        melee(enemy, target, 2.0);
        server.getScheduler().performTicks(3 * 20 + 1);
        assertEquals(2000, req.getLocation().getBlockX(), "target was re-validated at teleport time");
        assertFalse(plugin.teleports().hasPending(req));
    }

    @Test
    void requesterLeavingDropsTheRequest() {
        req.performCommand("tpa Target");
        req.disconnect();
        assertTrue(plugin.tpa().incoming(target).isEmpty());
    }
}
