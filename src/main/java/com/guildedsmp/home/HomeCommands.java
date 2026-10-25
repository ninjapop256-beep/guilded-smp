package com.guildedsmp.home;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Messages;
import com.guildedsmp.storage.StoredLocation;
import com.guildedsmp.util.SafeLocations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /home [name], /sethome [name], /delhome &lt;name&gt;, /homes. */
public final class HomeCommands implements CommandExecutor, TabCompleter {

    private final GuildedSMP plugin;

    public HomeCommands(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        HomeManager homes = plugin.homes();
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "home" -> {
                if (args.length > 1) {
                    plugin.messages().send(player, "homes.usage-home");
                    return true;
                }
                Map<String, StoredLocation> mine = homes.homes(player.getUniqueId());
                String name;
                if (args.length == 1) {
                    name = HomeManager.normalize(args[0]);
                } else if (mine.containsKey(HomeManager.DEFAULT_NAME) || mine.size() != 1) {
                    name = HomeManager.DEFAULT_NAME;
                } else {
                    name = mine.keySet().iterator().next();
                }
                StoredLocation home = homes.get(player.getUniqueId(), name);
                if (home == null) {
                    plugin.messages().send(player, mine.isEmpty() ? "homes.none" : "homes.not-found", Messages.ph("home", name));
                    return true;
                }
                if (home.world() == null) {
                    plugin.messages().send(player, "homes.world-unavailable", Messages.ph("home", name),
                            Messages.ph("world", home.worldName()));
                    return true;
                }
                plugin.teleports().teleport(player, home::toLocation, SafeLocations.Mode.EXACT_OR_NEARBY, "home " + name, null);
            }
            case "sethome" -> {
                if (args.length > 1) {
                    plugin.messages().send(player, "homes.usage-sethome");
                    return true;
                }
                String name = HomeManager.normalize(args.length == 1 ? args[0] : null);
                if (!homes.isValidName(name)) {
                    plugin.messages().send(player, "homes.invalid-name",
                            Messages.ph("max", plugin.settings().homes.maxNameLength()));
                    return true;
                }
                if (!SafeLocations.isSafeStanding(player.getLocation())) {
                    plugin.messages().send(player, "homes.unsafe-here");
                    return true;
                }
                HomeManager.SetResult result = homes.set(player, name, StoredLocation.of(player.getLocation()));
                switch (result) {
                    case CREATED -> plugin.messages().send(player, "homes.set", Messages.ph("home", name));
                    case UPDATED -> plugin.messages().send(player, "homes.updated", Messages.ph("home", name));
                    case LIMIT_REACHED -> plugin.messages().send(player, "homes.limit", Messages.ph("limit", homes.limit(player)));
                    case INVALID_NAME -> plugin.messages().send(player, "homes.invalid-name",
                            Messages.ph("max", plugin.settings().homes.maxNameLength()));
                }
            }
            case "delhome" -> {
                if (args.length != 1) {
                    plugin.messages().send(player, "homes.usage-delhome");
                    return true;
                }
                String name = HomeManager.normalize(args[0]);
                if (homes.delete(player, name)) {
                    plugin.messages().send(player, "homes.deleted", Messages.ph("home", name));
                } else {
                    plugin.messages().send(player, "homes.not-found", Messages.ph("home", name));
                }
            }
            case "homes" -> {
                Map<String, StoredLocation> mine = homes.homes(player.getUniqueId());
                int limit = homes.limit(player);
                String limitText = limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit);
                if (mine.isEmpty()) {
                    plugin.messages().send(player, "homes.none");
                    return true;
                }
                List<Component> entries = new ArrayList<>();
                for (Map.Entry<String, StoredLocation> e : mine.entrySet()) {
                    StoredLocation l = e.getValue();
                    boolean available = l.world() != null;
                    entries.add(Component.text(e.getKey(), available ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY)
                            .hoverEvent(HoverEvent.showText(Component.text(l.worldName() + " " + l.blockX() + ", "
                                    + l.blockY() + ", " + l.blockZ() + (available ? "" : " (world not loaded)"))))
                            .clickEvent(ClickEvent.runCommand("/home " + e.getKey())));
                }
                player.sendMessage(plugin.messages().prefixed("homes.list-header",
                        Messages.ph("count", mine.size()), Messages.ph("limit", limitText)));
                player.sendMessage(Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), entries));
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
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("home") || name.equals("delhome") || name.equals("sethome")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String home : plugin.homes().homes(player.getUniqueId()).keySet()) {
                if (home.startsWith(prefix)) {
                    out.add(home);
                }
            }
        }
        return out;
    }
}
