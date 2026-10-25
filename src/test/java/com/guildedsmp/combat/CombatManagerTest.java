package com.guildedsmp.combat;

import com.guildedsmp.PluginTestBase;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Combat timer logic with a controllable clock. */
class CombatManagerTest extends PluginTestBase {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    @Test
    void tagsRefreshAndExpireWithAccurateCountdown() {
        CombatManager combat = new CombatManager(plugin, now::get);
        PlayerMock a = player("Alice");
        PlayerMock b = player("Bob");
        List<String> started = new ArrayList<>();
        combat.onCombatStart(p -> started.add(p.getName()));

        combat.tag(a, b);
        assertTrue(combat.isTagged(a) && combat.isTagged(b));
        assertEquals(20, combat.remainingSeconds(a.getUniqueId()));
        assertEquals(List.of("Alice", "Bob"), started);
        assertEquals("⚔ IN COMBAT — 20s", plain(a.nextActionBar()));

        now.addAndGet(7_500);
        combat.tick();
        assertEquals(13, combat.remainingSeconds(a.getUniqueId()));
        assertEquals("⚔ IN COMBAT — 13s", plain(a.nextActionBar()));

        combat.tag(a, b); // refresh
        assertEquals(20, combat.remainingSeconds(a.getUniqueId()));
        assertEquals(List.of("Alice", "Bob"), started, "refresh must not re-fire combat start");

        drain(a);
        now.addAndGet(20_001);
        combat.tick();
        assertFalse(combat.isTagged(a));
        assertFalse(combat.isTagged(b));
        assertEquals(0, combat.taggedCount());
        assertTrue(said(drain(a), "no longer in combat"));
    }

    @Test
    void bossBarIsShownOnceAndRemovedOnExpiry() {
        plugin.getConfig().set("combat.display", "BOSS_BAR");
        plugin.saveConfig();
        plugin.reloadAll();
        CombatManager combat = new CombatManager(plugin, now::get);
        PlayerMock a = player("Alice");
        PlayerMock b = player("Bob");
        combat.tag(a, b);
        combat.tick();
        combat.tag(a, b);
        assertEquals(1, count(a.activeBossBars()), "exactly one bar, even after refresh");
        now.addAndGet(21_000);
        combat.tick();
        assertEquals(0, count(a.activeBossBars()));
    }

    @Test
    void forgetAndClearRemoveState() {
        CombatManager combat = new CombatManager(plugin, now::get);
        PlayerMock a = player("Alice");
        PlayerMock b = player("Bob");
        combat.tag(a, b);
        combat.forget(a);
        combat.clear(b, false);
        assertEquals(0, combat.taggedCount());
    }

    private static int count(Iterable<?> it) {
        int n = 0;
        for (Object ignored : it) {
            n++;
        }
        return n;
    }
}
