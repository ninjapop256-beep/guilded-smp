package com.guildedsmp.combat;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Messages;
import com.guildedsmp.config.Settings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Tracks PvP combat tags. One shared timer drives every player's countdown, and it only runs
 * while at least one player is tagged, so idle servers pay nothing. All state is main-thread only.
 */
public final class CombatManager {

    private static final long UI_PERIOD_TICKS = 5L;

    private final GuildedSMP plugin;
    private final LongSupplier clock;
    private final Map<UUID, Long> expiry = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> lastShownSeconds = new HashMap<>();
    private final List<Consumer<Player>> startListeners = new ArrayList<>();
    private BukkitTask task;

    public CombatManager(GuildedSMP plugin) {
        this(plugin, System::currentTimeMillis);
    }

    public CombatManager(GuildedSMP plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    private Settings.Combat cfg() {
        return plugin.settings().combat;
    }

    /** Called (once) when a player goes from untagged to tagged. */
    public void onCombatStart(Consumer<Player> listener) {
        startListeners.add(listener);
    }

    public boolean isTagged(Player player) {
        return isTagged(player.getUniqueId());
    }

    public boolean isTagged(UUID id) {
        Long until = expiry.get(id);
        return until != null && until > clock.getAsLong();
    }

    public long remainingMillis(UUID id) {
        Long until = expiry.get(id);
        return until == null ? 0L : Math.max(0L, until - clock.getAsLong());
    }

    public int remainingSeconds(UUID id) {
        return (int) Math.ceil(remainingMillis(id) / 1000.0);
    }

    public int taggedCount() {
        return expiry.size();
    }

    /** Tags (or refreshes) both players of a valid PvP hit. */
    public void tag(Player attacker, Player victim) {
        tagOne(attacker);
        tagOne(victim);
    }

    public void tagOne(Player player) {
        UUID id = player.getUniqueId();
        boolean wasTagged = isTagged(id);
        expiry.put(id, clock.getAsLong() + cfg().tagDurationSeconds() * 1000L);
        lastShownSeconds.remove(id);
        if (!wasTagged) {
            if (cfg().notifyOnTag()) {
                plugin.messages().send(player, "combat.tagged", Messages.ph("seconds", cfg().tagDurationSeconds()));
            }
            for (Consumer<Player> l : startListeners) {
                l.accept(player);
            }
        }
        render(player);
        ensureTask();
    }

    /** Clears a tag and removes the UI right away. */
    public void clear(Player player, boolean notify) {
        if (expiry.remove(player.getUniqueId()) != null) {
            hideUi(player);
            if (notify && cfg().notifyOnExpire()) {
                plugin.messages().send(player, "combat.expired");
            }
        }
        stopTaskIfIdle();
    }

    /** Drops all state for a player who is leaving (no messages). */
    public void forget(Player player) {
        expiry.remove(player.getUniqueId());
        hideUi(player);
        stopTaskIfIdle();
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(expiry.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                hideUi(p);
            }
        }
        expiry.clear();
        bars.clear();
        lastShownSeconds.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void ensureTask() {
        if (task == null || task.isCancelled()) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, UI_PERIOD_TICKS, UI_PERIOD_TICKS);
        }
    }

    private void stopTaskIfIdle() {
        if (expiry.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Package-private so tests can drive it directly. */
    void tick() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<UUID, Long>> it = expiry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                bars.remove(e.getKey());
                lastShownSeconds.remove(e.getKey());
                continue;
            }
            if (e.getValue() <= now) {
                it.remove();
                hideUi(p);
                if (cfg().notifyOnExpire()) {
                    plugin.messages().send(p, "combat.expired");
                }
            } else {
                render(p);
            }
        }
        stopTaskIfIdle();
    }

    private void render(Player p) {
        Settings.CombatDisplay mode = cfg().display();
        if (mode == Settings.CombatDisplay.NONE) {
            return;
        }
        UUID id = p.getUniqueId();
        int seconds = remainingSeconds(id);
        Integer last = lastShownSeconds.put(id, seconds);
        boolean changed = last == null || last != seconds;
        BossBar existing = bars.get(id);
        boolean wantsBar = mode == Settings.CombatDisplay.BOSS_BAR || mode == Settings.CombatDisplay.BOTH;
        Component text = changed || (wantsBar && existing == null)
                ? Messages.parse(cfg().displayFormat(), Messages.ph("seconds", seconds)) : null;
        if ((mode == Settings.CombatDisplay.ACTION_BAR || mode == Settings.CombatDisplay.BOTH) && changed) {
            p.sendActionBar(text);
        }
        if (wantsBar) {
            float progress = Math.max(0f, Math.min(1f,
                    remainingMillis(id) / (cfg().tagDurationSeconds() * 1000f)));
            BossBar bar = existing;
            if (bar == null) {
                bar = BossBar.bossBar(text, progress, barColor(), BossBar.Overlay.PROGRESS);
                bars.put(id, bar);
                p.showBossBar(bar);
            } else {
                if (changed) {
                    bar.name(text);
                }
                bar.progress(progress);
            }
        }
    }

    private BossBar.Color barColor() {
        try {
            return BossBar.Color.valueOf(cfg().bossBarColor().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.RED;
        }
    }

    private void hideUi(Player p) {
        UUID id = p.getUniqueId();
        lastShownSeconds.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) {
            p.hideBossBar(bar);
        }
        Settings.CombatDisplay mode = cfg().display();
        if (mode == Settings.CombatDisplay.ACTION_BAR || mode == Settings.CombatDisplay.BOTH) {
            p.sendActionBar(Component.empty());
        }
    }
}
