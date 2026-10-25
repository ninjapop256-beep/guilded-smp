package com.guildedsmp.teleport;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import com.guildedsmp.util.SafeLocations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * /tpa requests: "requester asks to teleport to target". One outgoing request per player.
 * Requests live in memory only, so nothing stale survives a restart.
 */
public final class TpaManager implements Listener {

    public record Request(UUID requester, UUID target, long createdAt, long expiresAt, BukkitTask expiryTask) {
    }

    public enum SendResult { SENT, SELF, TARGET_OFFLINE, ALREADY_PENDING_SAME, ALREADY_PENDING_OTHER, COOLDOWN, IN_COMBAT, TARGET_IN_COMBAT }

    private final GuildedSMP plugin;
    private final LongSupplier clock;
    private final Map<UUID, Request> outgoing = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public TpaManager(GuildedSMP plugin) {
        this(plugin, System::currentTimeMillis);
    }

    public TpaManager(GuildedSMP plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    public Optional<Request> outgoing(Player requester) {
        return Optional.ofNullable(outgoing.get(requester.getUniqueId()));
    }

    /** Incoming, unexpired requests for a target, newest first. */
    public List<Request> incoming(Player target) {
        long now = clock.getAsLong();
        List<Request> list = new ArrayList<>();
        for (Request r : outgoing.values()) {
            if (r.target().equals(target.getUniqueId()) && r.expiresAt() > now) {
                list.add(r);
            }
        }
        list.sort(Comparator.comparingLong(Request::createdAt).reversed());
        return list;
    }

    public long cooldownRemainingSeconds(Player p) {
        Long until = cooldownUntil.get(p.getUniqueId());
        if (until == null) {
            return 0;
        }
        long ms = until - clock.getAsLong();
        return ms <= 0 ? 0 : (long) Math.ceil(ms / 1000.0);
    }

    public SendResult send(Player requester, Player target) {
        if (target == null || !target.isOnline() || !requester.canSee(target)) {
            return SendResult.TARGET_OFFLINE;
        }
        if (requester.equals(target)) {
            return SendResult.SELF;
        }
        if (plugin.teleports().combatBlocked(requester)) {
            return SendResult.IN_COMBAT;
        }
        if (plugin.combat().isTagged(target)) {
            return SendResult.TARGET_IN_COMBAT;
        }
        Request existing = outgoing.get(requester.getUniqueId());
        if (existing != null && existing.expiresAt() > clock.getAsLong()) {
            return existing.target().equals(target.getUniqueId()) ? SendResult.ALREADY_PENDING_SAME : SendResult.ALREADY_PENDING_OTHER;
        }
        if (!requester.hasPermission(Perms.TPA_BYPASS_COOLDOWN) && cooldownRemainingSeconds(requester) > 0) {
            return SendResult.COOLDOWN;
        }
        if (existing != null) {
            remove(existing);
        }
        long now = clock.getAsLong();
        int timeout = plugin.settings().tpa.requestTimeoutSeconds();
        UUID rid = requester.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> expire(rid), timeout * 20L);
        outgoing.put(rid, new Request(rid, target.getUniqueId(), now, now + timeout * 1000L, task));
        int cooldown = plugin.settings().tpa.cooldownSeconds();
        if (cooldown > 0) {
            cooldownUntil.put(rid, now + cooldown * 1000L);
        }
        pruneCooldowns(now);
        return SendResult.SENT;
    }

    private void pruneCooldowns(long now) {
        cooldownUntil.values().removeIf(until -> until <= now);
    }

    private void remove(Request r) {
        outgoing.remove(r.requester(), r);
        if (r.expiryTask() != null) {
            r.expiryTask().cancel();
        }
    }

    private void expire(UUID requesterId) {
        Request r = outgoing.get(requesterId);
        if (r == null) {
            return;
        }
        outgoing.remove(requesterId);
        Player requester = Bukkit.getPlayer(r.requester());
        Player target = Bukkit.getPlayer(r.target());
        if (requester != null) {
            plugin.messages().send(requester, "tpa.expired-requester",
                    Messages.ph("player", target != null ? target.getName() : "?"));
        }
        if (target != null) {
            plugin.messages().send(target, "tpa.expired-target",
                    Messages.ph("player", requester != null ? requester.getName() : "?"));
        }
    }

    /** Picks the request a target means: by requester name, else the newest. */
    public Optional<Request> find(Player target, String requesterName) {
        List<Request> list = incoming(target);
        if (requesterName == null) {
            return list.stream().findFirst();
        }
        for (Request r : list) {
            Player req = Bukkit.getPlayer(r.requester());
            if (req != null && req.getName().equalsIgnoreCase(requesterName)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Accepts a request; the requester then teleports (warm-up and checks apply). */
    public boolean accept(Player target, Request r) {
        remove(r);
        Player requester = Bukkit.getPlayer(r.requester());
        if (requester == null || !requester.isOnline()) {
            plugin.messages().send(target, "tpa.requester-gone");
            return false;
        }
        if (plugin.teleports().combatBlocked(requester)) {
            plugin.messages().send(target, "tpa.requester-in-combat", Messages.ph("player", requester.getName()));
            plugin.messages().send(requester, "combat.teleport-blocked",
                    Messages.ph("seconds", plugin.combat().remainingSeconds(requester.getUniqueId())));
            return false;
        }
        if (plugin.combat().isTagged(target)) {
            plugin.messages().send(target, "tpa.accept-in-combat");
            return false;
        }
        plugin.messages().send(target, "tpa.accepted-target", Messages.ph("player", requester.getName()));
        plugin.messages().send(requester, "tpa.accepted-requester", Messages.ph("player", target.getName()));
        UUID targetId = target.getUniqueId();
        plugin.teleports().teleport(requester, () -> {
            Player t = Bukkit.getPlayer(targetId);
            // Re-validated at the moment of teleport: the target must still be online, alive, not in combat.
            if (t == null || !t.isOnline() || t.isDead() || plugin.combat().isTagged(t)) {
                return null;
            }
            Location loc = t.getLocation();
            return t.isGliding() || t.getFallDistance() > 3 ? null : loc;
        }, SafeLocations.Mode.LENIENT, target.getName(), null);
        return true;
    }

    public void deny(Player target, Request r) {
        remove(r);
        Player requester = Bukkit.getPlayer(r.requester());
        plugin.messages().send(target, "tpa.denied-target", Messages.ph("player", requester != null ? requester.getName() : "?"));
        if (requester != null) {
            plugin.messages().send(requester, "tpa.denied-requester", Messages.ph("player", target.getName()));
        }
    }

    /** /tpacancel. */
    public boolean cancelOutgoing(Player requester) {
        Request r = outgoing.get(requester.getUniqueId());
        if (r == null) {
            return false;
        }
        remove(r);
        Player target = Bukkit.getPlayer(r.target());
        if (target != null) {
            plugin.messages().send(target, "tpa.cancelled-target", Messages.ph("player", requester.getName()));
        }
        return true;
    }

    /** Drops every request involving the player (combat start or disconnect). */
    public void cancelAllFor(Player p, String reasonKey) {
        UUID id = p.getUniqueId();
        for (Request r : new ArrayList<>(outgoing.values())) {
            if (!r.requester().equals(id) && !r.target().equals(id)) {
                continue;
            }
            remove(r);
            UUID otherId = r.requester().equals(id) ? r.target() : r.requester();
            Player other = Bukkit.getPlayer(otherId);
            if (reasonKey != null) {
                plugin.messages().send(p, reasonKey);
                if (other != null) {
                    plugin.messages().send(other, "tpa.cancelled-other", Messages.ph("player", p.getName()));
                }
            } else if (other != null) {
                plugin.messages().send(other, "tpa.cancelled-other", Messages.ph("player", p.getName()));
            }
        }
    }

    public void shutdown() {
        for (Request r : new ArrayList<>(outgoing.values())) {
            remove(r);
        }
        outgoing.clear();
        cooldownUntil.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancelAllFor(event.getPlayer(), null);
    }
}
