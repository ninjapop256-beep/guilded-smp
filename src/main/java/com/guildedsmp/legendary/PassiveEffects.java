package com.guildedsmp.legendary;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Settings;
import com.guildedsmp.items.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Held-weapon effects (Inferno Blade: Fire Resistance, Frostbite: Speed).
 * <p>
 * One timer for the whole server. Effects are applied as short, refreshed effects, and are only
 * removed when they are still "ours" (same amplifier, no longer than our duration). A longer or
 * stronger effect from a potion or beacon is never replaced or removed.
 */
public final class PassiveEffects implements Listener {

    private final GuildedSMP plugin;
    private final Map<UUID, Set<PotionEffectType>> managed = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public PassiveEffects(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private Settings.Legendaries cfg() {
        return plugin.settings().legendaries;
    }

    public void start() {
        stop();
        int period = cfg().passiveCheckIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, Set<PotionEffectType>> e : managed.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                for (PotionEffectType type : e.getValue()) {
                    removeIfOurs(p, type);
                }
            }
        }
        managed.clear();
    }

    private Map<PotionEffectType, Integer> desired(CustomItem held) {
        Map<PotionEffectType, Integer> out = new HashMap<>();
        if (held == CustomItem.INFERNO_BLADE && cfg().inferno().fireResistanceWhileHeld()) {
            out.put(PotionEffectType.FIRE_RESISTANCE, 0);
        } else if (held == CustomItem.FROSTBITE && cfg().frostbite().speedWhileHeld()) {
            out.put(PotionEffectType.SPEED, cfg().frostbite().speedAmplifier());
        }
        return out;
    }

    private int amplifierFor(PotionEffectType type) {
        return type.equals(PotionEffectType.SPEED) ? cfg().frostbite().speedAmplifier() : 0;
    }

    void tick() {
        ticks++;
        int duration = cfg().passiveEffectDurationTicks();
        for (Player p : Bukkit.getOnlinePlayers()) {
            CustomItem held = plugin.items().identify(p.getInventory().getItemInMainHand()).orElse(null);
            Map<PotionEffectType, Integer> want = held == null || !held.legendary ? Map.of() : desired(held);
            Set<PotionEffectType> mine = managed.get(p.getUniqueId());
            if (mine != null) {
                for (PotionEffectType type : new HashSet<>(mine)) {
                    if (!want.containsKey(type)) {
                        removeIfOurs(p, type);
                        mine.remove(type);
                    }
                }
                if (mine.isEmpty()) {
                    managed.remove(p.getUniqueId());
                }
            }
            for (Map.Entry<PotionEffectType, Integer> e : want.entrySet()) {
                PotionEffect current = p.getPotionEffect(e.getKey());
                boolean ours = current == null || (current.getAmplifier() == e.getValue()
                        && !current.isInfinite() && current.getDuration() <= duration);
                if (!ours) {
                    continue; // something stronger or longer is active; leave it alone
                }
                p.addPotionEffect(new PotionEffect(e.getKey(), duration, e.getValue(), true, false, true));
                managed.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(e.getKey());
            }
            if (held != null && held.legendary && cfg().heldParticles() && ticks % 2 == 0) {
                LegendaryFx.held(held, p);
            }
        }
    }

    private void removeIfOurs(Player p, PotionEffectType type) {
        PotionEffect current = p.getPotionEffect(type);
        if (current != null && current.getAmplifier() == amplifierFor(type) && !current.isInfinite()
                && current.getDuration() <= cfg().passiveEffectDurationTicks()) {
            p.removePotionEffect(type);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Set<PotionEffectType> mine = managed.remove(event.getPlayer().getUniqueId());
        if (mine != null) {
            for (PotionEffectType type : mine) {
                removeIfOurs(event.getPlayer(), type);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        managed.remove(event.getEntity().getUniqueId());
    }
}
