package com.guildedsmp.spawn;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import com.guildedsmp.storage.StoredLocation;
import com.guildedsmp.util.SafeLocations;
import org.bukkit.Location;
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

/** /spawn, /spawn set, /spawn remove, /spawn find. */
public final class SpawnCommand implements CommandExecutor, TabCompleter {

    private final GuildedSMP plugin;

    public SpawnCommand(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        SpawnManager spawn = plugin.spawn();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "general.players-only");
                    return true;
                }
                Optional<StoredLocation> stored = spawn.spawn();
                if (stored.isEmpty()) {
                    plugin.messages().send(player, "spawn.not-set");
                    return true;
                }
                if (stored.get().world() == null) {
                    plugin.messages().send(player, "spawn.world-unavailable", Messages.ph("world", stored.get().worldName()));
                    return true;
                }
                plugin.teleports().teleport(player, spawn::spawnLocation, SafeLocations.Mode.SURFACE, "spawn", null);
            }
            case "set" -> {
                if (!sender.hasPermission(Perms.SPAWN_SET)) {
                    plugin.messages().send(sender, "general.no-permission");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "general.players-only");
                    return true;
                }
                Location loc = player.getLocation();
                spawn.setSpawn(loc);
                plugin.messages().send(player, "spawn.set", Messages.ph("world", loc.getWorld().getName()),
                        Messages.ph("x", loc.getBlockX()), Messages.ph("y", loc.getBlockY()), Messages.ph("z", loc.getBlockZ()));
                if (spawn.status() == SpawnManager.Status.ACTIVE) {
                    plugin.messages().send(player, "spawn.protection-active", Messages.ph("radius", spawn.radius()));
                }
            }
            case "remove" -> {
                if (!sender.hasPermission(Perms.SPAWN_REMOVE)) {
                    plugin.messages().send(sender, "general.no-permission");
                    return true;
                }
                if (spawn.removeSpawn()) {
                    plugin.messages().send(sender, "spawn.removed");
                } else {
                    plugin.messages().send(sender, "spawn.not-set");
                }
            }
            case "find" -> {
                if (!sender.hasPermission(Perms.SPAWN_FIND)) {
                    plugin.messages().send(sender, "general.no-permission");
                    return true;
                }
                Optional<StoredLocation> stored = spawn.spawn();
                if (stored.isEmpty()) {
                    plugin.messages().send(sender, "spawn.not-set");
                    return true;
                }
                StoredLocation s = stored.get();
                plugin.messages().send(sender, "spawn.find", Messages.ph("world", s.worldName()),
                        Messages.ph("x", s.blockX()), Messages.ph("y", s.blockY()), Messages.ph("z", s.blockZ()));
                if (sender instanceof Player player) {
                    Location here = player.getLocation();
                    Location there = s.toLocation();
                    if (there == null) {
                        plugin.messages().send(player, "spawn.world-unavailable", Messages.ph("world", s.worldName()));
                    } else if (!there.getWorld().equals(here.getWorld())) {
                        plugin.messages().send(player, "spawn.find-other-world", Messages.ph("world", s.worldName()));
                    } else {
                        plugin.messages().send(player, "spawn.find-distance",
                                Messages.ph("distance", Math.round(here.distance(there))));
                    }
                }
            }
            default -> plugin.messages().send(sender, "spawn.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if (sender.hasPermission(Perms.SPAWN_FIND) && "find".startsWith(prefix)) {
            out.add("find");
        }
        if (sender.hasPermission(Perms.SPAWN_SET) && "set".startsWith(prefix)) {
            out.add("set");
        }
        if (sender.hasPermission(Perms.SPAWN_REMOVE) && "remove".startsWith(prefix)) {
            out.add("remove");
        }
        return out;
    }
}
