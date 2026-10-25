package com.guildedsmp.pack;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Messages;
import com.guildedsmp.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Optionally sends the Guilded SMP resource pack (URL + SHA-1 from config) and tracks which
 * players loaded a pack, so sounds can fall back to vanilla for everyone else.
 * Custom items keep working without the pack; they just look like their vanilla base item.
 */
public final class ResourcePackManager implements Listener {

    private final GuildedSMP plugin;
    private final Map<UUID, Boolean> loaded = new HashMap<>();

    public ResourcePackManager(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private Settings.ResourcePack cfg() {
        return plugin.settings().resourcePack;
    }

    public UUID packId() {
        return UUID.nameUUIDFromBytes(("guildedsmp:" + cfg().url() + "#" + cfg().sha1()).getBytes(StandardCharsets.UTF_8));
    }

    public boolean hasPack(Player p) {
        return Boolean.TRUE.equals(loaded.get(p.getUniqueId()));
    }

    /** Warn if server.properties also sends a pack (players would get two prompts). */
    public void checkConflicts() {
        if (!cfg().enabled()) {
            return;
        }
        try {
            String serverPack = Bukkit.getResourcePack();
            if (serverPack != null && !serverPack.isBlank()) {
                plugin.getLogger().warning("resource-pack.enabled is true but server.properties also sets resource-pack. "
                        + "Use only one of them, or players will be asked twice.");
            }
        } catch (Throwable ignored) {
            // not available on this implementation
        }
    }

    public void send(Player p) {
        if (!cfg().enabled() || !p.isOnline()) {
            return;
        }
        byte[] hash = HexFormat.of().parseHex(cfg().sha1());
        p.setResourcePack(packId(), cfg().url(), hash, Messages.parse(cfg().prompt()), cfg().required());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!cfg().enabled()) {
            return;
        }
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> send(p), Math.max(1, cfg().sendDelayTicks()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        boolean ours = !cfg().enabled() || packId().equals(event.getID());
        if (!ours) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.put(event.getPlayer().getUniqueId(), true);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                loaded.put(event.getPlayer().getUniqueId(), false);
                if (cfg().enabled() && event.getStatus() != PlayerResourcePackStatusEvent.Status.DECLINED) {
                    plugin.getLogger().warning("Resource pack failed for " + event.getPlayer().getName()
                            + " (" + event.getStatus() + "). Check resource-pack.url and sha1.");
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }
}
