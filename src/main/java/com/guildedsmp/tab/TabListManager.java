package com.guildedsmp.tab;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Messages;
import com.guildedsmp.config.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TAB list header and footer.
 * <p>
 * The brand line is built from two separately coloured components: "GUILDED" in exactly #00FFFF
 * and "SMP" in exactly #000000. Neither has a gradient and the parent component has no colour, so
 * nothing is inherited. Black text is hard to read on the dark TAB background, so by default "SMP"
 * gets a light text <em>shadow</em> (the letters themselves stay #000000); set
 * {@code tablist.smp-shadow: false} to use the vanilla shadow.
 * <p>
 * The footer is rebuilt per viewer (their own ping, and the players they can see) and only sent
 * when its text changed, so idle servers send almost nothing.
 */
public final class TabListManager implements Listener {

    public static final TextColor GUILDED_CYAN = TextColor.color(0x00FFFF);
    public static final TextColor SMP_BLACK = TextColor.color(0x000000);

    /** Plugins that are known to manage the TAB header/footer. */
    static final List<String> CONFLICTS = List.of("TAB", "TAB-Bridge", "AnimatedTab", "TabList", "BetterTabList",
            "CMI", "SimpleTab", "TabListPro", "KiteBoard");

    private final GuildedSMP plugin;
    private final Map<UUID, String> lastFooterKey = new HashMap<>();
    private BukkitTask task;
    private Component header = Component.empty();
    private boolean active;

    public TabListManager(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private Settings.TabList cfg() {
        return plugin.settings().tab;
    }

    public boolean isActive() {
        return active;
    }

    /** "GUILDED SMP" with exact per-word colours. */
    public static Component brand(boolean smpShadow, int shadowArgb) {
        TextComponent guilded = Component.text("GUILDED", GUILDED_CYAN, TextDecoration.BOLD);
        TextComponent smp = Component.text("SMP", SMP_BLACK, TextDecoration.BOLD);
        if (smpShadow) {
            smp = smp.shadowColor(ShadowColor.shadowColor(shadowArgb));
        }
        return Component.text().append(guilded).append(Component.text(" ")).append(smp).build();
    }

    public List<String> detectConflicts() {
        List<String> found = new ArrayList<>();
        for (String name : CONFLICTS) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) {
                found.add(name);
            }
        }
        return found;
    }

    public void start() {
        stop();
        if (!cfg().enabled()) {
            return;
        }
        List<String> conflicts = detectConflicts();
        if (!conflicts.isEmpty()) {
            plugin.getLogger().warning("TAB header/footer: " + String.join(", ", conflicts)
                    + " also manages the TAB list. " + (cfg().disableOnConflict()
                    ? "Guilded SMP's TAB header is disabled to avoid flicker (tablist.disable-on-conflict)."
                    : "Both will fight over it; consider setting tablist.disable-on-conflict: true."));
            if (cfg().disableOnConflict()) {
                return;
            }
        }
        active = true;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(brand(cfg().smpShadow(), cfg().smpShadowArgb()));
        for (String extra : cfg().headerExtraLines()) {
            lines.add(Messages.parse(extra));
        }
        lines.add(Component.empty());
        header = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
        long period = cfg().refreshIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (active) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
        }
        active = false;
        lastFooterKey.clear();
    }

    public Component header() {
        return header;
    }

    public void refreshAll() {
        if (!active) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            refresh(viewer, false);
        }
    }

    /** Visible online count for this viewer (vanished players are not counted). */
    static int visibleOnline(Player viewer) {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(viewer) || viewer.canSee(p)) {
                n++;
            }
        }
        return n;
    }

    public void refresh(Player viewer, boolean force) {
        if (!active || !viewer.isOnline()) {
            return;
        }
        int ping = viewer.getPing();
        int online = visibleOnline(viewer);
        int max = Bukkit.getMaxPlayers();
        String key = ping + "|" + online + "|" + max;
        if (!force && key.equals(lastFooterKey.get(viewer.getUniqueId()))) {
            return;
        }
        lastFooterKey.put(viewer.getUniqueId(), key);
        viewer.sendPlayerListHeaderAndFooter(header, footer(ping, online, max));
    }

    public Component footer(int ping, int online, int max) {
        Component pingText;
        if (ping < 0) {
            pingText = Component.text("? ms", NamedTextColor.GRAY);
        } else {
            NamedTextColor color = ping <= cfg().pingGood() ? NamedTextColor.GREEN
                    : ping <= cfg().pingOkay() ? NamedTextColor.YELLOW : NamedTextColor.RED;
            pingText = Component.text(ping + " ms", color);
        }
        Component body = Messages.parse(cfg().footerFormat(),
                Messages.comp("ping", pingText),
                Messages.ph("online", online),
                Messages.ph("max", max > 0 ? String.valueOf(max) : "?"));
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), Component.empty(), body, Component.empty());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!active) {
            return;
        }
        // Next tick: everyone's online count changed, and the joiner needs the header.
        Bukkit.getScheduler().runTask(plugin, () -> {
            refresh(event.getPlayer(), true);
            refreshAll();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastFooterKey.remove(event.getPlayer().getUniqueId());
        if (active) {
            // The leaving player still counts during this event, so update one tick later.
            Bukkit.getScheduler().runTask(plugin, this::refreshAll);
        }
    }
}
