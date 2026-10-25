package com.guildedsmp.combat;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import com.guildedsmp.config.Settings;
import com.guildedsmp.util.Attackers;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Creates combat tags from valid PvP hits and enforces combat restrictions and combat logging. */
public final class CombatListener implements Listener {

    private final GuildedSMP plugin;
    /** Players killed for combat logging this session, told why when they come back. */
    private final Set<UUID> combatLogged = new HashSet<>();

    public CombatListener(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private Settings.Combat cfg() {
        return plugin.settings().combat;
    }

    private static boolean canFight(Player p) {
        GameMode gm = p.getGameMode();
        return !p.isDead() && (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE);
    }

    /**
     * MONITOR + ignoreCancelled: runs only for hits that actually went through, so a hit blocked
     * by spawn protection (or any other plugin) never creates a tag.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = Attackers.directOrProjectile(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (event.getFinalDamage() <= 0) {
            return;
        }
        if (!canFight(victim) || !canFight(attacker)) {
            return;
        }
        if (plugin.spawn().isProtected(victim.getLocation()) || plugin.spawn().isProtected(attacker.getLocation())) {
            return;
        }
        plugin.combat().tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.combat().clear(event.getEntity(), false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        CombatManager combat = plugin.combat();
        boolean tagged = combat.isTagged(player);
        // Always clear state and UI first, so the death below can never be handled twice.
        combat.forget(player);
        Settings.CombatLog log = cfg().combatLog();
        if (!tagged || !log.enabled() || log.penalty() == Settings.CombatLogPenalty.NONE) {
            return;
        }
        if (Bukkit.isStopping() || !log.penalizeReasons().contains(event.getReason())) {
            return;
        }
        if (!canFight(player) || player.getHealth() <= 0) {
            return;
        }
        // A normal in-game death: the server applies its usual death drops / keepInventory rules
        // exactly once, and the player respawns on their next join.
        player.setHealth(0.0);
        combatLogged.add(player.getUniqueId());
        plugin.getLogger().info(player.getName() + " logged out during combat and was killed.");
        if (log.broadcast()) {
            Bukkit.broadcast(plugin.messages().prefixed("combat.log-broadcast", Messages.ph("player", player.getName())));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (combatLogged.remove(event.getPlayer().getUniqueId())) {
            plugin.messages().send(event.getPlayer(), "combat.log-notice");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!plugin.combat().isTagged(p) || p.hasPermission(Perms.COMBAT_BYPASS)) {
            return;
        }
        String msg = event.getMessage();
        String label = msg.length() > 1 ? msg.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT) : "";
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (cfg().blockedCommands().contains(label)) {
            event.setCancelled(true);
            plugin.messages().send(p, "combat.command-blocked",
                    Messages.ph("seconds", plugin.combat().remainingSeconds(p.getUniqueId())));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (!plugin.combat().isTagged(p) || p.hasPermission(Perms.COMBAT_BYPASS)) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        boolean block = switch (cause) {
            case COMMAND -> cfg().blockCommandTeleports();
            case NETHER_PORTAL, END_PORTAL, END_GATEWAY -> cfg().blockPortals();
            default -> false;
        };
        if (block) {
            event.setCancelled(true);
            plugin.messages().send(p, "combat.teleport-blocked",
                    Messages.ph("seconds", plugin.combat().remainingSeconds(p.getUniqueId())));
        }
    }
}
