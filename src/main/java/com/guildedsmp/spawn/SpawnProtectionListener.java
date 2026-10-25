package com.guildedsmp.spawn;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import com.guildedsmp.config.Settings;
import com.guildedsmp.util.Attackers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The safe zone around spawn. Runs at LOW priority so later listeners (combat tags at MONITOR,
 * legendary effects at MONITOR with ignoreCancelled) never see blocked hits.
 */
public final class SpawnProtectionListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MS = 2_000L;

    private final GuildedSMP plugin;
    private final Map<UUID, Long> lastEntryMessage = new HashMap<>();
    private BukkitTask sweepTask;

    public SpawnProtectionListener(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private Settings.Protection cfg() {
        return plugin.settings().protection;
    }

    private boolean prot(Location loc) {
        return plugin.spawn().isProtected(loc);
    }

    public void start() {
        stop();
        if (cfg().removeWanderingHostiles()) {
            long period = cfg().hostileSweepIntervalSeconds() * 20L;
            sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepHostiles, period, period);
        }
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        lastEntryMessage.clear();
    }

    // ------------------------------------------------------------------ PvP and player-caused harm

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity damager = event.getDamager();
        Player attacker = Attackers.responsiblePlayer(damager);
        if (attacker != null) {
            if (!attacker.equals(victim) && cfg().blockPvp() && (prot(victim.getLocation()) || prot(attacker.getLocation()))) {
                event.setCancelled(true);
                notifyBlocked(attacker);
            }
            return;
        }
        if (cfg().blockHostileMobDamage() && prot(victim.getLocation()) && isHostileSource(damager)) {
            event.setCancelled(true);
        }
    }

    private static boolean isHostileSource(Entity damager) {
        if (damager instanceof Enemy) {
            return true;
        }
        return damager instanceof Projectile p && p.getShooter() instanceof Enemy;
    }

    private void notifyBlocked(Player attacker) {
        long now = System.currentTimeMillis();
        Long last = lastEntryMessage.get(attacker.getUniqueId());
        if (last == null || now - last > MESSAGE_COOLDOWN_MS) {
            lastEntryMessage.put(attacker.getUniqueId(), now);
            plugin.messages().send(attacker, "spawnprotect.pvp-blocked");
        }
    }

    /** Fire Aspect, Flame arrows and the Inferno Blade cannot set protected players on fire. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !cfg().blockPvp()) {
            return;
        }
        Player attacker = Attackers.responsiblePlayer(event.getCombuster());
        if (attacker != null && !attacker.equals(victim) && (prot(victim.getLocation()) || prot(attacker.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onKnockback(EntityPushedByEntityAttackEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !cfg().blockPvp()) {
            return;
        }
        Player attacker = Attackers.responsiblePlayer(event.getPushedBy());
        if (attacker != null && !attacker.equals(victim) && (prot(victim.getLocation()) || prot(attacker.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!cfg().blockPvp() || !(potion.getShooter() instanceof Player thrower) || !isHarmful(potion.getEffects(), null)) {
            return;
        }
        boolean throwerProtected = prot(thrower.getLocation());
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player p && !p.equals(thrower) && (throwerProtected || prot(p.getLocation()))) {
                event.setIntensity(p, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!cfg().blockPvp() || !(cloud.getSource() instanceof Player thrower)
                || !isHarmful(cloud.getCustomEffects(), cloud.getBasePotionType())) {
            return;
        }
        event.getAffectedEntities().removeIf(e -> e instanceof Player p && !p.equals(thrower) && prot(p.getLocation()));
    }

    private static boolean isHarmful(Collection<PotionEffect> effects, PotionType base) {
        for (PotionEffect e : effects) {
            if (e.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                return true;
            }
        }
        if (base != null) {
            for (PotionEffect e : base.getPotionEffects()) {
                if (e.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Fishing rods can't drag players out of (or within) the safe zone. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (cfg().blockPvp() && event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY
                && event.getCaught() instanceof Player caught && !caught.equals(event.getPlayer())
                && (prot(caught.getLocation()) || prot(event.getPlayer().getLocation()))) {
            event.setCancelled(true);
        }
    }

    /** Wind charges fired by players don't burst inside the safe zone. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (cfg().blockPvp() && event.getEntity() instanceof AbstractWindCharge charge
                && charge.getShooter() instanceof Player && prot(charge.getLocation())) {
            event.setCancelled(true);
            charge.remove();
        }
    }

    // ------------------------------------------------------------------ hostile mobs

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (cfg().blockHostileSpawns() && event.getEntity() instanceof Enemy
                && cfg().hostileSpawnReasons().contains(event.getSpawnReason()) && prot(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (cfg().blockHostileTargeting() && event.getEntity() instanceof Enemy
                && event.getTarget() instanceof Player p && prot(p.getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Removes hostile mobs that wandered into the safe zone, but only ones the game would despawn
     * anyway (no name tag, not leashed, not persistence-required, not a boss). Removing instead of pushing
     * avoids mobs getting stuck at the border or bouncing in and out.
     */
    void sweepHostiles() {
        SquareRegion r = plugin.spawn().region();
        if (r == null) {
            return;
        }
        World w = Bukkit.getWorld(r.worldId());
        if (w == null) {
            return;
        }
        // Only loaded chunks can hold mobs, so walk those instead of the whole (possibly huge) square.
        for (org.bukkit.Chunk chunk : w.getLoadedChunks()) {
            int x0 = chunk.getX() << 4;
            int z0 = chunk.getZ() << 4;
            if (x0 + 15 < r.minX() || x0 > r.maxX() || z0 + 15 < r.minZ() || z0 > r.maxZ()) {
                continue;
            }
            for (Entity e : chunk.getEntities()) {
                Location l = e.getLocation();
                if (isRemovableHostile(e) && r.contains(w.getUID(), l.getBlockX(), l.getBlockZ())) {
                    e.remove();
                }
            }
        }
    }

    static boolean isRemovableHostile(Entity e) {
        if (!(e instanceof Enemy) || !(e instanceof Mob mob) || e instanceof Boss) {
            return false;
        }
        // getRemoveWhenFarAway() is vanilla's "can despawn" flag (false for name-tagged, tamed or
        // otherwise persistence-required mobs). Entity#isPersistent means "saved to disk" and is
        // true for almost every mob, so it must not be used here.
        return mob.getRemoveWhenFarAway() && mob.customName() == null
                && !mob.isLeashed() && mob.getPassengers().isEmpty() && !mob.isInsideVehicle();
    }

    // ------------------------------------------------------------------ combat-tagged entry

    private boolean mayNotEnter(Player p) {
        return cfg().blockCombatTaggedEntry() && plugin.combat().isTagged(p) && !p.hasPermission(Perms.COMBAT_BYPASS);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player p = event.getPlayer();
        if (!mayNotEnter(p)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        // Only block crossing *into* the zone; players already inside can always walk out.
        if (!prot(from) && prot(to)) {
            event.setCancelled(true);
            Vector away = from.toVector().subtract(to.toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-4) {
                p.setVelocity(away.normalize().multiply(0.4).setY(0.1));
            }
            entryMessage(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (mayNotEnter(p) && !prot(event.getFrom()) && prot(event.getTo())) {
            event.setCancelled(true);
            entryMessage(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleMove(VehicleMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (prot(from) || !prot(to)) {
            return;
        }
        List<Entity> passengers = event.getVehicle().getPassengers();
        for (Entity passenger : passengers) {
            if (passenger instanceof Player p && mayNotEnter(p)) {
                event.getVehicle().removePassenger(p);
                Location back = from.clone();
                back.setYaw(p.getLocation().getYaw());
                back.setPitch(p.getLocation().getPitch());
                p.teleport(back);
                entryMessage(p);
            }
        }
    }

    private void entryMessage(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastEntryMessage.get(p.getUniqueId());
        if (last == null || now - last > MESSAGE_COOLDOWN_MS) {
            lastEntryMessage.put(p.getUniqueId(), now);
            plugin.messages().send(p, "spawnprotect.tagged-entry-blocked",
                    Messages.ph("seconds", plugin.combat().remainingSeconds(p.getUniqueId())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastEntryMessage.remove(event.getPlayer().getUniqueId());
    }
}
