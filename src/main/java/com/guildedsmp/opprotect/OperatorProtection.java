package com.guildedsmp.opprotect;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import com.guildedsmp.config.Settings;
import com.guildedsmp.identity.IdentityRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the configured accounts opped.
 * <ul>
 *   <li>Re-ops on plugin enable, on join, after any command that mentions "deop", and on a slow timer.</li>
 *   <li>{@code setOp} is only called when an account is not already op, so nothing is written to ops.json
 *       unless a change is actually needed (no write loops).</li>
 *   <li>Blocks {@code /deop <protected name>} from players, console, RCON and command blocks.</li>
 *   <li>Never touches any other player's op status or permissions.</li>
 * </ul>
 * It cannot stop someone with access to the server files, console process, or another plugin
 * that edits ops directly; it only restores op status afterwards.
 */
public final class OperatorProtection implements Listener {

    private final GuildedSMP plugin;
    private final Map<UUID, PermissionAttachment> creatorAttachments = new HashMap<>();
    private List<IdentityRegistry.Entry> entries = List.of();
    private BukkitTask task;
    private boolean warnedOffline;

    public OperatorProtection(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private Settings.OpProtection cfg() {
        return plugin.settings().opProtection;
    }

    public List<IdentityRegistry.Entry> entries() {
        return entries;
    }

    public void start() {
        stop();
        List<IdentityRegistry.Entry> parsed = new ArrayList<>();
        for (String raw : cfg().protectedPlayers()) {
            IdentityRegistry.Entry.parse(raw).ifPresent(parsed::add);
        }
        entries = List.copyOf(parsed);
        if (cfg().enabled()) {
            long period = cfg().checkIntervalSeconds() * 20L;
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::enforceAll, period, period);
            enforceAll();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshCreator(p);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, PermissionAttachment> e : creatorAttachments.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                try {
                    p.removeAttachment(e.getValue());
                } catch (IllegalArgumentException ignored) {
                    // already removed
                }
            }
        }
        creatorAttachments.clear();
    }

    /** True if this online player is one of the protected accounts. */
    public boolean isProtected(Player player) {
        return plugin.identities().matches(player, entries, cfg().allowOfflineMode());
    }

    /** True if the name is on the protected list (used to refuse moderation of the owners). */
    public boolean isProtectedName(String name) {
        return plugin.identities().isTrustedName(name, entries);
    }

    public boolean isProtectedAccount(OfflinePlayer player) {
        return plugin.identities().isTrustedAccount(player, entries);
    }

    /** Restores op where needed. Cheap: a handful of map lookups. */
    public void enforceAll() {
        if (!cfg().enabled()) {
            return;
        }
        if (!plugin.identities().secureAuthentication() && !cfg().allowOfflineMode() && !warnedOffline) {
            warnedOffline = true;
            plugin.getLogger().warning("Operator protection: this server does not verify player accounts "
                    + "(offline mode without a verified proxy). Name-only entries will NOT be auto-opped, "
                    + "because anyone could join with those names. Pin UUIDs with 'Name:uuid' or see README.");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            enforce(p);
        }
        for (Map.Entry<String, UUID> account : plugin.identities().knownAccounts(entries).entrySet()) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(account.getValue());
            if (!off.isOnline() && !off.isOp()) {
                off.setOp(true);
                plugin.getLogger().info("Operator protection restored op for " + account.getKey() + ".");
            }
        }
    }

    private void enforce(Player p) {
        if (cfg().enabled() && !p.isOp() && isProtected(p)) {
            p.setOp(true);
            plugin.getLogger().info("Operator protection restored op for " + p.getName() + ".");
        }
    }

    private void refreshCreator(Player p) {
        PermissionAttachment old = creatorAttachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
                // already gone
            }
        }
        if (cfg().grantCreatorPermission() && isProtected(p)) {
            creatorAttachments.put(p.getUniqueId(), p.addAttachment(plugin, Perms.CREATOR, true));
            try {
                p.updateCommands();
            } catch (RuntimeException ignored) {
                // Only refreshes client tab-completion; the permission itself is already granted.
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        enforce(event.getPlayer());
        refreshCreator(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment att = creatorAttachments.remove(event.getPlayer().getUniqueId());
        if (att != null) {
            try {
                event.getPlayer().removeAttachment(att);
            } catch (IllegalArgumentException ignored) {
                // already gone
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (handleCommand(event.getMessage(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (handleCommand(event.getCommand(), event.getSender())) {
            event.setCancelled(true);
        }
    }

    /** @return true when the command must be cancelled. */
    boolean handleCommand(String raw, CommandSender sender) {
        if (!cfg().enabled() || raw == null) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!lower.contains("deop")) {
            return false;
        }
        String s = lower.startsWith("/") ? raw.substring(1).trim() : raw.trim();
        String[] parts = s.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (label.equals("deop") && parts.length >= 2 && cfg().blockDeopCommands() && isProtectedName(parts[1])) {
            plugin.messages().send(sender, "opprotect.deop-blocked", Messages.ph("player", parts[1]));
            plugin.getLogger().warning(sender.getName() + " tried to de-op protected player " + parts[1] + "; blocked.");
            return true;
        }
        // Selectors, /execute ... deop, or protection of deop disabled: restore right after it runs.
        Bukkit.getScheduler().runTask(plugin, this::enforceAll);
        return false;
    }
}
