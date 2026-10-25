package com.guildedsmp.moderation;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import com.guildedsmp.util.Durations;
import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/** /mute, /unmute, /tempban, /unban. The protected owner accounts can never be targeted. */
public final class ModerationCommands implements CommandExecutor, TabCompleter {

    private final GuildedSMP plugin;

    public ModerationCommands(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "mute" -> mute(sender, args);
            case "unmute" -> unmute(sender, args);
            case "tempban" -> tempban(sender, args);
            case "unban" -> unban(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean refuseTarget(CommandSender sender, String name, OfflinePlayer target) {
        if (plugin.operatorProtection().isProtectedName(name)
                || (target != null && plugin.operatorProtection().isProtectedAccount(target))) {
            plugin.messages().send(sender, "moderation.target-protected", Messages.ph("player", name));
            return true;
        }
        Player online = target == null ? null : target.getPlayer();
        if (online != null && online.hasPermission(Perms.MODERATION_EXEMPT) && !sender.hasPermission(Perms.CREATOR)) {
            plugin.messages().send(sender, "moderation.target-exempt", Messages.ph("player", online.getName()));
            return true;
        }
        return false;
    }

    /** Online player, else a player the server has seen before (no blocking web lookups). */
    private static OfflinePlayer known(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    private void mute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            plugin.messages().send(sender, "mute.usage");
            return;
        }
        OfflinePlayer target = known(args[0]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-unknown", Messages.ph("player", args[0]));
            return;
        }
        String name = target.getName() != null ? target.getName() : args[0];
        if (refuseTarget(sender, name, target)) {
            return;
        }
        long millis = -1L;
        int reasonStart = 1;
        if (args.length >= 2) {
            String d = args[1].toLowerCase(Locale.ROOT);
            Optional<Duration> parsed = Durations.parse(d);
            if (parsed.isPresent()) {
                millis = parsed.get().toMillis();
                reasonStart = 2;
            } else if (d.equals("perm") || d.equals("permanent")) {
                reasonStart = 2;
            }
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, Math.min(reasonStart, args.length), args.length)).trim();
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        plugin.mutes().mute(target.getUniqueId(), name, millis, reason, sender.getName());
        String time = millis < 0 ? "permanently" : "for " + Durations.format(Duration.ofMillis(millis));
        plugin.messages().send(sender, "mute.done", Messages.ph("player", name), Messages.ph("time", time));
        Player online = target.getPlayer();
        if (online != null) {
            plugin.messages().send(online, "mute.notify", Messages.ph("time", time),
                    Messages.ph("reason", reason.isEmpty() ? "-" : reason));
        }
    }

    private void unmute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            plugin.messages().send(sender, "mute.usage-unmute");
            return;
        }
        OfflinePlayer target = known(args[0]);
        if (target == null || !plugin.mutes().unmute(target.getUniqueId())) {
            plugin.messages().send(sender, "mute.not-muted", Messages.ph("player", args[0]));
            return;
        }
        plugin.messages().send(sender, "mute.undone", Messages.ph("player", args[0]));
        Player online = target.getPlayer();
        if (online != null) {
            plugin.messages().send(online, "mute.notify-unmuted");
        }
    }

    /** Resolves a profile without blocking the main thread; the callback runs on the main thread. */
    private void resolveProfile(String name, Consumer<PlayerProfile> callback) {
        OfflinePlayer known = known(name);
        if (known != null) {
            callback.accept(known.getPlayerProfile());
            return;
        }
        Bukkit.createProfile(name).update().whenComplete((profile, error) -> Bukkit.getScheduler().runTask(plugin, () ->
                callback.accept(error == null && profile != null && profile.getId() != null ? profile : null)));
    }

    private void tempban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "tempban.usage");
            return;
        }
        Optional<Duration> duration = Durations.parse(args[1]);
        if (duration.isEmpty()) {
            plugin.messages().send(sender, "general.bad-duration", Messages.ph("input", args[1]));
            return;
        }
        long max = plugin.settings().moderation.maxTempbanSeconds();
        if (duration.get().getSeconds() > max) {
            plugin.messages().send(sender, "tempban.too-long", Messages.ph("max", Durations.format(Duration.ofSeconds(max))));
            return;
        }
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim() : "Banned by a moderator";
        String finalReason = reason.length() > 200 ? reason.substring(0, 200) : reason;
        if (refuseTarget(sender, args[0], known(args[0]))) {
            return;
        }
        resolveProfile(args[0], profile -> {
            if (profile == null) {
                plugin.messages().send(sender, "general.player-unknown", Messages.ph("player", args[0]));
                return;
            }
            String name = profile.getName() != null ? profile.getName() : args[0];
            OfflinePlayer off = Bukkit.getOfflinePlayer(profile.getId());
            if (refuseTarget(sender, name, off)) {
                return;
            }
            ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
            BanEntry<PlayerProfile> entry = bans.addBan(profile, finalReason, duration.get(), sender.getName());
            String time = Durations.format(duration.get());
            plugin.messages().send(sender, "tempban.done", Messages.ph("player", name), Messages.ph("time", time));
            plugin.getLogger().info(sender.getName() + " temp-banned " + name + " for " + time + ": " + finalReason);
            Player online = Bukkit.getPlayer(profile.getId());
            if (online != null && entry != null) {
                online.kick(plugin.messages().get("tempban.kick", Messages.ph("time", time), Messages.ph("reason", finalReason)));
            }
        });
    }

    private void unban(CommandSender sender, String[] args) {
        if (args.length != 1) {
            plugin.messages().send(sender, "tempban.usage-unban");
            return;
        }
        resolveProfile(args[0], profile -> {
            ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
            if (profile == null || !bans.isBanned(profile)) {
                plugin.messages().send(sender, "tempban.not-banned", Messages.ph("player", args[0]));
                return;
            }
            bans.pardon(profile);
            plugin.messages().send(sender, "tempban.undone", Messages.ph("player", args[0]));
            plugin.getLogger().info(sender.getName() + " unbanned " + args[0] + ".");
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && !cmd.equals("unban")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 2 && (cmd.equals("mute") || cmd.equals("tempban"))) {
            for (String d : cmd.equals("mute") ? List.of("10m", "1h", "1d", "perm") : List.of("1h", "1d", "7d", "30d")) {
                if (d.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(d);
                }
            }
        }
        return out;
    }
}
