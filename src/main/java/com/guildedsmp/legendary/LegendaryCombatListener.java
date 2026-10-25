package com.guildedsmp.legendary;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Settings;
import com.guildedsmp.items.CustomItem;
import com.guildedsmp.pack.SoundService;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * All legendary on-hit and on-kill effects, in one place.
 * <p>
 * Runs at MONITOR with ignoreCancelled, so it only reacts to hits the server actually allowed
 * (spawn protection, PvP-off worlds and other plugins' protections all cancel earlier). It then
 * re-checks the safe zone itself, so effects can never reach a protected player.
 */
public final class LegendaryCombatListener implements Listener {

    private final GuildedSMP plugin;
    /** Attack charge captured just before a swing (vanilla resets it before damage is dealt). */
    private final Map<UUID, Float> charge = new HashMap<>();
    private DoubleSupplier random = () -> ThreadLocalRandom.current().nextDouble();

    public LegendaryCombatListener(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    /** For tests: deterministic rolls. */
    public void setRandom(DoubleSupplier random) {
        this.random = random;
    }

    private Settings.Legendaries cfg() {
        return plugin.settings().legendaries;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (event.willAttack()) {
            charge.put(event.getPlayer().getUniqueId(), event.getPlayer().getAttackCooldown());
        }
    }

    /** For tests: record a swing charge as PrePlayerAttackEntityEvent would. */
    public void recordCharge(Player p, float value) {
        charge.put(p.getUniqueId(), value);
    }

    private boolean validTarget(Player attacker, LivingEntity victim) {
        if (victim.isDead() || victim.isInvulnerable() || victim instanceof ArmorStand) {
            return false;
        }
        if (victim instanceof Player vp) {
            GameMode gm = vp.getGameMode();
            if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) {
                return false;
            }
            if (Boolean.FALSE.equals(vp.getWorld().getGameRuleValue(org.bukkit.GameRules.PVP))) {
                return false;
            }
            return !plugin.spawn().isProtected(vp.getLocation()) && !plugin.spawn().isProtected(attacker.getLocation());
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Float swing = charge.remove(attacker.getUniqueId());
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim) || victim.equals(attacker)) {
            return;
        }
        CustomItem weapon = plugin.items().identify(attacker.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null || !weapon.legendary || event.getFinalDamage() <= 0 || !validTarget(attacker, victim)) {
            return;
        }
        if (cfg().requireFullAttackCharge() && (swing == null || swing < 0.9f)) {
            return;
        }
        switch (weapon) {
            case INFERNO_BLADE -> {
                Settings.Inferno c = cfg().inferno();
                if (roll(c.igniteChance())) {
                    victim.setFireTicks(Math.max(victim.getFireTicks(), c.igniteSeconds() * 20));
                    LegendaryFx.hit(weapon, victim);
                    plugin.sounds().play(victim.getLocation(), SoundService.Cue.INFERNO_HIT);
                }
            }
            case FROSTBITE -> {
                Settings.Frostbite c = cfg().frostbite();
                if (roll(c.slownessChance())) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, c.slownessSeconds() * 20, c.slownessAmplifier()));
                    LegendaryFx.hit(weapon, victim);
                    plugin.sounds().play(victim.getLocation(), SoundService.Cue.FROST_HIT);
                }
            }
            case VOID_REAPER -> {
                Settings.VoidReaper c = cfg().voidReaper();
                if (roll(c.weaknessChance())) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, c.weaknessSeconds() * 20, c.weaknessAmplifier()));
                    LegendaryFx.hit(weapon, victim);
                    plugin.sounds().play(victim.getLocation(), SoundService.Cue.VOID_HIT);
                }
            }
            default -> {
            }
        }
    }

    private boolean roll(double chance) {
        return chance > 0 && random.getAsDouble() < chance;
    }

    /**
     * Void Reaper's kill reward. Only a direct melee kill by the holder counts: environmental
     * deaths (fall, lava, void) and indirect kills credited to the player are ignored.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead) || killer.isDead()) {
            return;
        }
        DamageSource src = event.getDamageSource();
        Entity causing = src.getCausingEntity();
        Entity direct = src.getDirectEntity();
        if (!killer.equals(causing) || !killer.equals(direct)) {
            return;
        }
        CustomItem weapon = plugin.items().identify(killer.getInventory().getItemInMainHand()).orElse(null);
        if (weapon != CustomItem.VOID_REAPER) {
            return;
        }
        Settings.VoidReaper c = cfg().voidReaper();
        boolean counts = dead instanceof Player
                || (c.killTrigger() == Settings.KillTrigger.PLAYERS_AND_HOSTILES && dead instanceof Enemy);
        if (!counts) {
            return;
        }
        killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, c.killStrengthSeconds() * 20, c.killStrengthAmplifier()));
        LegendaryFx.reap(killer);
        plugin.sounds().play(killer.getLocation(), SoundService.Cue.VOID_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        charge.remove(event.getPlayer().getUniqueId());
    }
}
