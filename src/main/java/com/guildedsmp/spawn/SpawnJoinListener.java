package com.guildedsmp.spawn;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.util.SafeLocations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Sends brand-new players to spawn, and respawns players without a bed/anchor at spawn. */
public final class SpawnJoinListener implements Listener {

    private final GuildedSMP plugin;

    public SpawnJoinListener(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.hasPlayedBefore() || !plugin.settings().spawn.teleportOnFirstJoin()) {
            return;
        }
        Location spawn = plugin.spawn().spawnLocation();
        if (spawn == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Runnable go = () -> {
                Location safe = SafeLocations.resolve(spawn, SafeLocations.Mode.SURFACE);
                if (safe != null && p.isOnline()) {
                    p.teleport(safe, PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            };
            if (spawn.getWorld().isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) {
                go.run();
            } else {
                spawn.getWorld().getChunkAtAsync(spawn).thenRun(() -> Bukkit.getScheduler().runTask(plugin, go));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.settings().spawn.respawnAtSpawnWithoutBed() || event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location spawn = plugin.spawn().spawnLocation();
        if (spawn == null) {
            return;
        }
        Location safe = spawn.getWorld().isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)
                ? SafeLocations.resolve(spawn, SafeLocations.Mode.SURFACE) : spawn;
        if (safe != null) {
            event.setRespawnLocation(safe);
        }
    }
}
