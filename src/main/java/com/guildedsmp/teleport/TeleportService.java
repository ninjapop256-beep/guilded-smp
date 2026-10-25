package com.guildedsmp.teleport;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import com.guildedsmp.util.SafeLocations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Every teleport this plugin performs goes through here (/spawn, /home, /tpa).
 * Combat status is checked when the teleport is requested, again when the warm-up ends,
 * and once more after the destination chunk has loaded, right before the player moves.
 */
public final class TeleportService implements Listener {

    private record Pending(Supplier<Location> destination, SafeLocations.Mode mode, String label,
                           Runnable afterSuccess, BukkitTask task) {
    }

    private final GuildedSMP plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    /** Incremented per request, so a stale async completion can tell it was superseded. */
    private final Map<UUID, Long> generation = new HashMap<>();

    public TeleportService(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    public boolean combatBlocked(Player p) {
        return plugin.combat().isTagged(p) && !p.hasPermission(Perms.COMBAT_BYPASS);
    }

    public boolean hasPending(Player p) {
        return pending.containsKey(p.getUniqueId());
    }

    /**
     * Starts a teleport, with the configured warm-up unless the player may skip it.
     *
     * @param destination resolved again at the moment of teleport (a target may have moved or left)
     * @param label       shown in messages, e.g. "spawn" or "home base"
     */
    public void teleport(Player p, Supplier<Location> destination, SafeLocations.Mode mode, String label, Runnable afterSuccess) {
        if (combatBlocked(p)) {
            plugin.messages().send(p, "combat.teleport-blocked",
                    Messages.ph("seconds", plugin.combat().remainingSeconds(p.getUniqueId())));
            return;
        }
        cancel(p, null);
        long gen = generation.merge(p.getUniqueId(), 1L, Long::sum);
        int warmup = p.hasPermission(Perms.TELEPORT_BYPASS_WARMUP) ? 0 : plugin.settings().teleport.warmupSeconds();
        if (warmup <= 0) {
            execute(p, destination, mode, label, afterSuccess, gen);
            return;
        }
        plugin.messages().send(p, "teleport.warmup", Messages.ph("seconds", warmup), Messages.ph("destination", label));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(p.getUniqueId());
            execute(p, destination, mode, label, afterSuccess, gen);
        }, warmup * 20L);
        pending.put(p.getUniqueId(), new Pending(destination, mode, label, afterSuccess, task));
    }

    private boolean current(Player p, long gen) {
        Long now = generation.get(p.getUniqueId());
        return now != null && now == gen;
    }

    private void execute(Player p, Supplier<Location> destination, SafeLocations.Mode mode, String label,
                         Runnable afterSuccess, long gen) {
        if (!p.isOnline() || p.isDead() || !current(p, gen)) {
            return;
        }
        if (combatBlocked(p)) {
            plugin.messages().send(p, "combat.teleport-blocked",
                    Messages.ph("seconds", plugin.combat().remainingSeconds(p.getUniqueId())));
            return;
        }
        Location target = destination.get();
        World world = target == null ? null : target.getWorld();
        if (world == null) {
            plugin.messages().send(p, "teleport.destination-unavailable", Messages.ph("destination", label));
            return;
        }
        if (world.isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
            finish(p, target, mode, label, afterSuccess, gen);
            return;
        }
        // Load the destination chunk off the main thread first; the checks below run again afterwards.
        world.getChunkAtAsync(target).whenComplete((chunk, error) -> runOnMain(() -> {
            if (error != null) {
                plugin.messages().send(p, "teleport.destination-unavailable", Messages.ph("destination", label));
                return;
            }
            finish(p, target, mode, label, afterSuccess, gen);
        }));
    }

    private void finish(Player p, Location target, SafeLocations.Mode mode, String label, Runnable afterSuccess, long gen) {
        if (!p.isOnline() || p.isDead() || !current(p, gen)) {
            return;
        }
        if (combatBlocked(p)) {
            plugin.messages().send(p, "combat.teleport-blocked",
                    Messages.ph("seconds", plugin.combat().remainingSeconds(p.getUniqueId())));
            return;
        }
        Location safe = SafeLocations.resolve(target, mode);
        if (safe == null) {
            plugin.messages().send(p, "teleport.unsafe", Messages.ph("destination", label));
            return;
        }
        // The destination chunk is loaded at this point, so a direct teleport is safe on the main thread.
        // It fires PlayerTeleportEvent, so spawn protection and other plugins can still veto it.
        if (p.teleport(safe, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            plugin.messages().send(p, "teleport.success", Messages.ph("destination", label));
            if (afterSuccess != null) {
                afterSuccess.run();
            }
        } else {
            plugin.messages().send(p, "teleport.failed", Messages.ph("destination", label));
        }
    }

    private void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    /** Cancels a pending (warming-up) teleport. A null message key cancels silently. */
    public void cancel(Player p, String messageKey) {
        generation.merge(p.getUniqueId(), 1L, Long::sum);
        Pending pd = pending.remove(p.getUniqueId());
        if (pd != null) {
            pd.task().cancel();
            if (messageKey != null) {
                plugin.messages().send(p, messageKey, Messages.ph("destination", pd.label()));
            }
        }
    }

    public void shutdown() {
        for (Pending pd : new ArrayList<>(pending.values())) {
            pd.task().cancel();
        }
        pending.clear();
        generation.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock() && plugin.settings().teleport.cancelOnMove() && hasPending(event.getPlayer())) {
            cancel(event.getPlayer(), "teleport.cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && plugin.settings().teleport.cancelOnDamage() && hasPending(p)) {
            cancel(p, "teleport.cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), null);
        generation.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        cancel(event.getEntity(), null);
    }
}
