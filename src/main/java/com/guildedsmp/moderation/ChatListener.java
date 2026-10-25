package com.guildedsmp.moderation;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Messages;
import com.guildedsmp.util.Durations;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/** Blocks chat (and configured message commands) for muted players. */
public final class ChatListener implements Listener {

    private final GuildedSMP plugin;

    public ChatListener(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private void notifyMuted(Player p, MuteManager.Mute m) {
        long left = plugin.mutes().remainingMillis(m);
        plugin.messages().send(p, m.permanent() ? "mute.you-are-muted-permanent" : "mute.you-are-muted",
                Messages.ph("time", left < 0 ? "" : Durations.format(Duration.ofMillis(left))),
                Messages.ph("reason", m.reason().isEmpty() ? "-" : m.reason()));
    }

    /** Async: only touches the concurrent mute map and sends a message (Audience is thread-safe). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Optional<MuteManager.Mute> mute = plugin.mutes().active(event.getPlayer().getUniqueId());
        if (mute.isPresent()) {
            event.setCancelled(true);
            notifyMuted(event.getPlayer(), mute.get());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg.length() < 2) {
            return;
        }
        String label = msg.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!plugin.settings().moderation.mutedBlockedCommands().contains(label)) {
            return;
        }
        Optional<MuteManager.Mute> mute = plugin.mutes().active(event.getPlayer().getUniqueId());
        if (mute.isPresent()) {
            event.setCancelled(true);
            notifyMuted(event.getPlayer(), mute.get());
        }
    }
}
