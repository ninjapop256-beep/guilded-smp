package com.guildedsmp.teleport;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** /tpa, /tpaccept, /tpdeny, /tpacancel. */
public final class TpaCommands implements CommandExecutor, TabCompleter {

    private final GuildedSMP plugin;

    public TpaCommands(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        TpaManager tpa = plugin.tpa();
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> {
                if (args.length != 1) {
                    plugin.messages().send(player, "tpa.usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                TpaManager.SendResult result = tpa.send(player, target);
                switch (result) {
                    case SENT -> {
                        plugin.messages().send(player, "tpa.sent", Messages.ph("player", target.getName()),
                                Messages.ph("seconds", plugin.settings().tpa.requestTimeoutSeconds()));
                        Component accept = plugin.messages().get("tpa.button-accept")
                                .clickEvent(ClickEvent.runCommand("/tpaccept " + player.getName()));
                        Component deny = plugin.messages().get("tpa.button-deny")
                                .clickEvent(ClickEvent.runCommand("/tpdeny " + player.getName()));
                        target.sendMessage(plugin.messages().prefixed("tpa.received", Messages.ph("player", player.getName()),
                                Messages.ph("seconds", plugin.settings().tpa.requestTimeoutSeconds())));
                        target.sendMessage(Component.text("  ").append(accept).append(Component.text("  ")).append(deny));
                    }
                    case SELF -> plugin.messages().send(player, "tpa.self");
                    case TARGET_OFFLINE -> plugin.messages().send(player, "general.player-not-found", Messages.ph("player", args[0]));
                    case ALREADY_PENDING_SAME -> plugin.messages().send(player, "tpa.already-pending", Messages.ph("player", target.getName()));
                    case ALREADY_PENDING_OTHER -> plugin.messages().send(player, "tpa.pending-other");
                    case COOLDOWN -> plugin.messages().send(player, "tpa.cooldown", Messages.ph("seconds", tpa.cooldownRemainingSeconds(player)));
                    case IN_COMBAT -> plugin.messages().send(player, "combat.teleport-blocked",
                            Messages.ph("seconds", plugin.combat().remainingSeconds(player.getUniqueId())));
                    case TARGET_IN_COMBAT -> plugin.messages().send(player, "tpa.target-in-combat", Messages.ph("player", target.getName()));
                }
            }
            case "tpaccept", "tpdeny" -> {
                boolean accept = command.getName().equalsIgnoreCase("tpaccept");
                Optional<TpaManager.Request> request = tpa.find(player, args.length >= 1 ? args[0] : null);
                if (request.isEmpty()) {
                    plugin.messages().send(player, args.length >= 1 ? "tpa.no-request-from" : "tpa.no-requests",
                            Messages.ph("player", args.length >= 1 ? args[0] : ""));
                    return true;
                }
                if (accept) {
                    tpa.accept(player, request.get());
                } else {
                    tpa.deny(player, request.get());
                }
            }
            case "tpacancel" -> {
                if (tpa.cancelOutgoing(player)) {
                    plugin.messages().send(player, "tpa.cancelled");
                } else {
                    plugin.messages().send(player, "tpa.nothing-to-cancel");
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player) || args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player) && player.canSee(p) && p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(p.getName());
                    }
                }
            }
            case "tpaccept", "tpdeny" -> {
                for (TpaManager.Request r : plugin.tpa().incoming(player)) {
                    Player req = Bukkit.getPlayer(r.requester());
                    if (req != null && req.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(req.getName());
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }
}
