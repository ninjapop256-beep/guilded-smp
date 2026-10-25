package com.guildedsmp.spawn;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.Perms;
import com.guildedsmp.config.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /spawnprotect [&lt;radius&gt; | status | remove]. The centre is always the configured spawn. */
public final class SpawnProtectCommand implements CommandExecutor, TabCompleter {

    private final GuildedSMP plugin;

    public SpawnProtectCommand(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "spawnprotect.help");
            sendStatus(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status")) {
            sendStatus(sender);
            return true;
        }
        if (!sender.hasPermission(Perms.SPAWNPROTECT_MODIFY)) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        SpawnManager spawn = plugin.spawn();
        if (sub.equals("remove") || sub.equals("off") || sub.equals("disable")) {
            if (spawn.disableProtection()) {
                plugin.messages().send(sender, "spawnprotect.disabled");
            } else {
                plugin.messages().send(sender, "spawnprotect.already-disabled");
            }
            return true;
        }
        int radius;
        try {
            radius = Integer.parseInt(sub);
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "spawnprotect.usage");
            return true;
        }
        int max = plugin.settings().protection.maxRadius();
        if (radius < 1 || radius > max) {
            plugin.messages().send(sender, "spawnprotect.bad-radius", Messages.ph("max", max));
            return true;
        }
        spawn.setRadius(radius);
        plugin.messages().send(sender, "spawnprotect.set", Messages.ph("radius", radius));
        if (spawn.spawn().isEmpty()) {
            plugin.messages().send(sender, "spawnprotect.waiting-for-spawn");
        }
        sendStatus(sender);
        return true;
    }

    private void sendStatus(CommandSender sender) {
        if (!sender.hasPermission(Perms.SPAWNPROTECT_VIEW)) {
            return;
        }
        SpawnManager spawn = plugin.spawn();
        switch (spawn.status()) {
            case ACTIVE -> {
                SquareRegion r = spawn.region();
                plugin.messages().send(sender, "spawnprotect.status-active",
                        Messages.ph("radius", r.radius()),
                        Messages.ph("world", spawn.spawn().map(s -> s.worldName()).orElse("?")),
                        Messages.ph("cx", r.centerX()), Messages.ph("cz", r.centerZ()),
                        Messages.ph("minx", r.minX()), Messages.ph("maxx", r.maxX()),
                        Messages.ph("minz", r.minZ()), Messages.ph("maxz", r.maxZ()),
                        Messages.ph("outline", plugin.outline().isActive() ? "shown" : "hidden"));
            }
            case DISABLED -> plugin.messages().send(sender, "spawnprotect.status-disabled", Messages.ph("radius", spawn.radius()));
            case NO_SPAWN -> plugin.messages().send(sender, "spawnprotect.status-no-spawn", Messages.ph("radius", spawn.radius()));
            case WORLD_UNLOADED -> plugin.messages().send(sender, "spawnprotect.status-world-unloaded",
                    Messages.ph("world", spawn.spawn().map(s -> s.worldName()).orElse("?")));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>(List.of("status"));
        if (sender.hasPermission(Perms.SPAWNPROTECT_MODIFY)) {
            options.addAll(List.of("remove", "50", "100", "200"));
        }
        for (String o : options) {
            if (o.startsWith(prefix)) {
                out.add(o);
            }
        }
        return out;
    }
}
