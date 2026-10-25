package com.guildedsmp.command;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import com.guildedsmp.identity.IdentityRegistry;
import com.guildedsmp.items.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /guilded (alias /gsmp): help, recipes, reload, give, items, combat, opprotect, version.
 */
public final class GuildedCommand implements CommandExecutor, TabCompleter {

    private final GuildedSMP plugin;

    public GuildedCommand(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> plugin.messages().send(sender, "guilded.help");
            case "version" -> plugin.messages().send(sender, "guilded.version",
                    Messages.ph("version", plugin.getPluginMeta().getVersion()),
                    Messages.ph("server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion()));
            case "recipes", "recipe" -> {
                if (!(sender instanceof Player p)) {
                    plugin.messages().send(sender, "general.players-only");
                } else if (!p.hasPermission(Perms.RECIPES)) {
                    plugin.messages().send(p, "general.no-permission");
                } else if (args.length >= 2) {
                    Optional<CustomItem> item = CustomItem.byId(args[1]).filter(c -> c.legendary);
                    if (item.isEmpty()) {
                        plugin.messages().send(p, "guilded.unknown-item", Messages.ph("item", args[1]));
                    } else {
                        plugin.recipeMenu().openRecipe(p, item.get());
                    }
                } else {
                    plugin.recipeMenu().openPicker(p);
                }
            }
            case "reload" -> {
                if (!sender.hasPermission(Perms.RELOAD)) {
                    plugin.messages().send(sender, "general.no-permission");
                    return true;
                }
                List<String> warnings = plugin.reloadAll();
                plugin.messages().send(sender, "guilded.reloaded", Messages.ph("warnings", warnings.size()));
                for (String w : warnings) {
                    sender.sendMessage(Messages.parse("<yellow>⚠ <white><text>", Messages.ph("text", w)));
                }
            }
            case "items" -> {
                if (!sender.hasPermission(Perms.GIVE)) {
                    plugin.messages().send(sender, "general.no-permission");
                    return true;
                }
                List<String> ids = new ArrayList<>();
                for (CustomItem c : CustomItem.values()) {
                    ids.add(c.id);
                }
                plugin.messages().send(sender, "guilded.items", Messages.ph("items", String.join(", ", ids)));
            }
            case "give" -> give(sender, args);
            case "combat" -> combat(sender, args);
            case "opprotect" -> opprotect(sender);
            case "mace" -> {
                if (!plugin.ownerRules().handleCommand(sender, args)) {
                    plugin.messages().send(sender, "guilded.unknown-subcommand");
                }
            }
            default -> plugin.messages().send(sender, "guilded.unknown-subcommand");
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Perms.GIVE)) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "guilded.give-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", Messages.ph("player", args[1]));
            return;
        }
        Optional<CustomItem> type = CustomItem.byId(args[2]);
        if (type.isEmpty()) {
            plugin.messages().send(sender, "guilded.unknown-item", Messages.ph("item", args[2]));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                amount = -1;
            }
            int max = type.get().legendary ? 1 : 64;
            if (amount < 1 || amount > max) {
                plugin.messages().send(sender, "guilded.bad-amount", Messages.ph("max", max));
                return;
            }
        }
        ItemStack stack = plugin.items().create(type.get(), amount);
        for (ItemStack overflow : target.getInventory().addItem(stack).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), overflow);
        }
        plugin.messages().send(sender, "guilded.given", Messages.ph("player", target.getName()),
                Messages.ph("amount", amount), Messages.ph("item", type.get().displayName));
        plugin.getLogger().info(sender.getName() + " gave " + amount + "x " + type.get().id + " to " + target.getName() + ".");
    }

    private void combat(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("clear")) {
            if (!sender.hasPermission(Perms.COMBAT_ADMIN)) {
                plugin.messages().send(sender, "general.no-permission");
                return;
            }
            Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
            if (target == null) {
                plugin.messages().send(sender, "general.player-not-found", Messages.ph("player", args.length >= 3 ? args[2] : ""));
                return;
            }
            plugin.combat().clear(target, true);
            plugin.messages().send(sender, "combat.cleared", Messages.ph("player", target.getName()));
            return;
        }
        Player target = sender instanceof Player p ? p : null;
        if (args.length >= 3 && sender.hasPermission(Perms.COMBAT_ADMIN)) {
            target = Bukkit.getPlayerExact(args[2]);
        }
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", Messages.ph("player", args.length >= 3 ? args[2] : ""));
            return;
        }
        if (plugin.combat().isTagged(target)) {
            plugin.messages().send(sender, "combat.status-tagged", Messages.ph("player", target.getName()),
                    Messages.ph("seconds", plugin.combat().remainingSeconds(target.getUniqueId())));
        } else {
            plugin.messages().send(sender, "combat.status-clear", Messages.ph("player", target.getName()));
        }
    }

    private void opprotect(CommandSender sender) {
        if (!(sender instanceof Player p) || !plugin.operatorProtection().isProtected(p)) {
            plugin.messages().send(sender, "guilded.unknown-subcommand");
            return;
        }
        var cfg = plugin.settings().opProtection;
        plugin.messages().send(sender, "opprotect.status",
                Messages.ph("state", cfg.enabled() ? "enabled" : "disabled"),
                Messages.ph("auth", plugin.identities().secureAuthentication() ? "verified (online-mode/proxy)" : "NOT verified (offline mode)"));
        Map<String, UUID> known = plugin.identities().knownAccounts(plugin.operatorProtection().entries());
        for (IdentityRegistry.Entry e : plugin.operatorProtection().entries()) {
            UUID id = known.get(e.name());
            String op = "?";
            if (id != null) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(id);
                op = off.isOp() ? "op" : "NOT op";
            }
            plugin.messages().send(sender, "opprotect.entry", Messages.ph("player", e.name()),
                    Messages.ph("uuid", id == null ? "not pinned yet" : id.toString()), Messages.ph("op", op));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "version", "combat"));
            if (sender.hasPermission(Perms.RECIPES)) {
                subs.add("recipes");
            }
            if (sender.hasPermission(Perms.RELOAD)) {
                subs.add("reload");
            }
            if (sender.hasPermission(Perms.GIVE)) {
                subs.add("give");
                subs.add("items");
            }
            if (sender instanceof Player p && plugin.operatorProtection().isProtected(p)) {
                subs.add("opprotect");
            }
            if (!plugin.ownerRules().tabComplete(sender, new String[]{"mace", ""}).isEmpty()) {
                subs.add("mace");
            }
            filter(subs, args[0], out);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!sender.hasPermission(Perms.GIVE)) {
                    return out;
                }
                if (args.length == 2) {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    filter(names, args[1], out);
                } else if (args.length == 3) {
                    List<String> ids = new ArrayList<>();
                    for (CustomItem c : CustomItem.values()) {
                        ids.add(c.id);
                    }
                    filter(ids, args[2], out);
                }
            }
            case "recipes", "recipe" -> {
                if (args.length == 2) {
                    filter(List.of("inferno_blade", "frostbite", "void_reaper"), args[1], out);
                }
            }
            case "combat" -> {
                if (args.length == 2) {
                    filter(sender.hasPermission(Perms.COMBAT_ADMIN) ? List.of("status", "clear") : List.of("status"), args[1], out);
                } else if (args.length == 3 && sender.hasPermission(Perms.COMBAT_ADMIN)) {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    filter(names, args[2], out);
                }
            }
            case "mace" -> out.addAll(plugin.ownerRules().tabComplete(sender, args));
            default -> {
            }
        }
        return out;
    }

    private static void filter(List<String> options, String prefix, List<String> out) {
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
    }
}
