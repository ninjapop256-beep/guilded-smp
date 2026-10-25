package com.guildedsmp.drops;

import com.guildedsmp.PluginTestBase;
import com.guildedsmp.items.CustomItem;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Cow;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientDropsTest extends PluginTestBase {

    private List<ItemStack> kill(org.bukkit.entity.LivingEntity mob, PlayerMock killer) {
        if (killer != null) {
            mob.setKiller(killer);
        }
        EntityDeathEvent e = new EntityDeathEvent(mob, DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>());
        server.getPluginManager().callEvent(e);
        return e.getDrops();
    }

    @Test
    void blazeDropsGenuineInfernoCoreOnLuckyPlayerKill() {
        plugin.drops().setRandom(() -> 0.0);
        PlayerMock p = player("Hunter");
        Blaze blaze = world.spawn(new Location(world, 1000, 64, 1003), Blaze.class);
        List<ItemStack> drops = kill(blaze, p);
        assertEquals(1, drops.size());
        assertEquals(CustomItem.INFERNO_CORE, plugin.items().identify(drops.get(0)).orElseThrow());
    }

    @Test
    void noDropWithoutAPlayerKillOrOnAFailedRoll() {
        plugin.drops().setRandom(() -> 0.0);
        Blaze blaze = world.spawn(new Location(world, 1000, 64, 1003), Blaze.class);
        assertTrue(kill(blaze, null).isEmpty(), "environment kill");

        plugin.drops().setRandom(() -> 0.5);
        PlayerMock p = player("Hunter");
        Blaze blaze2 = world.spawn(new Location(world, 1000, 64, 1004), Blaze.class);
        assertTrue(kill(blaze2, p).isEmpty(), "5% roll failed");

        plugin.drops().setRandom(() -> 0.0);
        Cow cow = world.spawn(new Location(world, 1000, 64, 1005), Cow.class);
        assertTrue(kill(cow, p).isEmpty(), "cows have no table entry");
    }
}
