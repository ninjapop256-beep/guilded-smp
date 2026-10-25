package com.guildedsmp.legendary;

import com.guildedsmp.PluginTestBase;
import com.guildedsmp.items.CustomItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryEffectsTest extends PluginTestBase {

    private PlayerMock attacker;
    private PlayerMock victim;

    @BeforeEach
    void setUp() {
        attacker = player("Attacker", 4000.5, 4000.5);
        victim = player("Victim", 4001.5, 4000.5);
        plugin.legendaryCombat().setRandom(() -> 0.0); // every roll succeeds
    }

    private void hold(PlayerMock p, CustomItem item) {
        p.getInventory().setItemInMainHand(plugin.items().create(item));
    }

    private void swing() {
        plugin.legendaryCombat().recordCharge(attacker, 1.0f);
        melee(attacker, victim, 6.0);
    }

    @Test
    void frostbiteSlowsVoidReaperWeakensInfernoIgnites() {
        hold(attacker, CustomItem.FROSTBITE);
        swing();
        PotionEffect slow = victim.getPotionEffect(PotionEffectType.SLOWNESS);
        assertNotNull(slow);
        assertEquals(1, slow.getAmplifier());
        assertEquals(60, slow.getDuration());

        hold(attacker, CustomItem.VOID_REAPER);
        swing();
        assertNotNull(victim.getPotionEffect(PotionEffectType.WEAKNESS));

        hold(attacker, CustomItem.INFERNO_BLADE);
        swing();
        assertTrue(victim.getFireTicks() >= 80, "fire ticks " + victim.getFireTicks());
    }

    @Test
    void unchargedSwingsAndFailedRollsDoNothing() {
        hold(attacker, CustomItem.FROSTBITE);
        plugin.legendaryCombat().recordCharge(attacker, 0.3f);
        melee(attacker, victim, 6.0);
        assertNull(victim.getPotionEffect(PotionEffectType.SLOWNESS));

        plugin.legendaryCombat().setRandom(() -> 0.99);
        swing();
        assertNull(victim.getPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void renamedNormalSwordHasNoPowers() {
        ItemStack fake = new ItemStack(Material.NETHERITE_SWORD);
        fake.editMeta(m -> m.displayName(Component.text("Frostbite")));
        attacker.getInventory().setItemInMainHand(fake);
        swing();
        assertNull(victim.getPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void effectsNeverReachPlayersInsideSpawn() {
        PlayerMock admin = player("Admin", 4000.5, 4000.5);
        admin.setOp(true);
        admin.performCommand("spawn set"); // protects radius 100 around 4000,4000
        hold(attacker, CustomItem.FROSTBITE);
        swing();
        assertNull(victim.getPotionEffect(PotionEffectType.SLOWNESS));
        assertEquals(0, Math.max(0, victim.getFireTicks()));
    }

    @Test
    void voidReaperRewardsOnlyDirectPlayerKills() {
        hold(attacker, CustomItem.VOID_REAPER);
        DamageSource melee = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
        victim.setKiller(attacker);
        server.getPluginManager().callEvent(new EntityDeathEvent(victim, melee, new ArrayList<>()));
        PotionEffect strength = attacker.getPotionEffect(PotionEffectType.STRENGTH);
        assertNotNull(strength);
        assertEquals(160, strength.getDuration());

        attacker.removePotionEffect(PotionEffectType.STRENGTH);
        DamageSource fall = DamageSource.builder(DamageType.FALL).build();
        server.getPluginManager().callEvent(new EntityDeathEvent(victim, fall, new ArrayList<>()));
        assertNull(attacker.getPotionEffect(PotionEffectType.STRENGTH), "environmental death credited to the player");

        Zombie z = world.spawn(new Location(world, 4002, 64, 4000), Zombie.class);
        z.setKiller(attacker);
        server.getPluginManager().callEvent(new EntityDeathEvent(z, melee, new ArrayList<>()));
        assertNull(attacker.getPotionEffect(PotionEffectType.STRENGTH), "mob kills don't count by default");
    }

    @Test
    void heldEffectsAreAppliedAndOnlyOurOwnAreRemoved() {
        hold(attacker, CustomItem.INFERNO_BLADE);
        plugin.passiveEffects().tick();
        PotionEffect fr = attacker.getPotionEffect(PotionEffectType.FIRE_RESISTANCE);
        assertNotNull(fr);
        assertTrue(fr.getDuration() <= 60);

        attacker.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        plugin.passiveEffects().tick();
        assertNull(attacker.getPotionEffect(PotionEffectType.FIRE_RESISTANCE), "removed when switching away");

        hold(victim, CustomItem.FROSTBITE);
        plugin.passiveEffects().tick();
        assertEquals(0, victim.getPotionEffect(PotionEffectType.SPEED).getAmplifier());

        // A real 8-minute potion is never replaced or removed by the held effect.
        victim.removePotionEffect(PotionEffectType.SPEED);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 9600, 0));
        plugin.passiveEffects().tick();
        victim.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        plugin.passiveEffects().tick();
        PotionEffect potion = victim.getPotionEffect(PotionEffectType.SPEED);
        assertNotNull(potion);
        assertTrue(potion.getDuration() > 9000);
    }

    @Test
    void heldEffectsDoNotLeakToOtherItems() {
        ItemStack fake = new ItemStack(Material.NETHERITE_SWORD);
        fake.editMeta(m -> m.displayName(Component.text("Inferno Blade")));
        attacker.getInventory().setItemInMainHand(fake);
        plugin.passiveEffects().tick();
        assertFalse(attacker.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE));
    }
}
