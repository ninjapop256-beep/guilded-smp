package com.guildedsmp.rules;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.identity.IdentityRegistry;
import com.guildedsmp.storage.YamlStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mace crafting rule (owner-only, intentionally undocumented in player-facing text):
 * only the owner accounts may craft a mace, and only a fixed number may ever be crafted.
 * <p>
 * Everyone else sees nothing: the crafting result simply stays empty, no message is sent, the
 * mace recipe is kept out of their recipe book, there is no permission node, and no console log.
 * Crafters (auto-crafters) can never make a mace.
 */
public final class OwnerCraftRules implements Listener {

    public static final NamespacedKey MACE_RECIPE = NamespacedKey.minecraft("mace");
    static final List<String> DEFAULT_CRAFTERS = List.of("Itzbreadloaf", "FelixSyn");

    private final GuildedSMP plugin;
    private final YamlStore store;
    private List<IdentityRegistry.Entry> allowed = List.of();
    private int crafted;
    private int limit = 1;
    private List<String> history = new ArrayList<>();
    private boolean readOnly;

    public OwnerCraftRules(GuildedSMP plugin, YamlStore store) {
        this.plugin = plugin;
        this.store = store;
        load();
    }

    private void load() {
        YamlConfiguration yaml = store.load();
        if (yaml == null) {
            readOnly = true;
            yaml = new YamlConfiguration();
            // Fail closed: if the counter can't be read, assume the limit is used up.
            crafted = Integer.MAX_VALUE;
        } else {
            crafted = Math.max(0, yaml.getInt("mace.crafted", 0));
        }
        limit = Math.max(0, yaml.getInt("mace.limit", 1));
        List<String> names = yaml.isList("mace.allowed") ? yaml.getStringList("mace.allowed") : DEFAULT_CRAFTERS;
        List<IdentityRegistry.Entry> parsed = new ArrayList<>();
        for (String n : names) {
            IdentityRegistry.Entry.parse(n).ifPresent(parsed::add);
        }
        allowed = List.copyOf(parsed);
        history = new ArrayList<>(yaml.getStringList("mace.history"));
    }

    private void save() {
        if (readOnly) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mace.crafted", crafted);
        yaml.set("mace.limit", limit);
        List<String> names = new ArrayList<>();
        for (IdentityRegistry.Entry e : allowed) {
            names.add(e.configuredUuid() == null ? e.name() : e.name() + ":" + e.configuredUuid());
        }
        yaml.set("mace.allowed", names);
        yaml.set("mace.history", history);
        store.saveAsync(yaml);
    }

    public boolean isAllowed(Player p) {
        return plugin.identities().matches(p, allowed, plugin.settings().opProtection.allowOfflineMode());
    }

    public boolean canCraftNow(Player p) {
        return isAllowed(p) && crafted < limit;
    }

    int crafted() {
        return crafted;
    }

    private static boolean isMace(Recipe recipe, ItemStack result) {
        if (recipe instanceof Keyed k && MACE_RECIPE.equals(k.getKey())) {
            return true;
        }
        return result != null && result.getType() == Material.MACE;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!isMace(event.getRecipe(), event.getInventory().getResult())) {
            return;
        }
        if (!(event.getView().getPlayer() instanceof Player p) || !canCraftNow(p)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!isMace(event.getRecipe(), event.getInventory().getResult())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player p) || !canCraftNow(p)) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            return;
        }
        ItemStack cursor = event.getCursor();
        boolean plainClick = event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT;
        if (!plainClick || (cursor != null && !cursor.getType().isAir())) {
            // Exactly one mace per craft: no shift-click / drop / hotbar tricks.
            event.setCancelled(true);
            p.sendMessage(Component.text("Take the mace with a normal click and an empty cursor.", NamedTextColor.GRAY));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrafted(CraftItemEvent event) {
        if (!isMace(event.getRecipe(), event.getInventory().getResult()) || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        crafted++;
        history.add(Instant.now() + " " + p.getName() + " " + p.getUniqueId());
        save();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (isMace(event.getRecipe(), event.getResult())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDiscover(PlayerRecipeDiscoverEvent event) {
        if (MACE_RECIPE.equals(event.getRecipe()) && !isAllowed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!isAllowed(p) && p.hasDiscoveredRecipe(MACE_RECIPE)) {
            p.undiscoverRecipe(MACE_RECIPE);
        }
    }

    /** Owner-only "/guilded mace ..." handling. Returns false for anyone else (treated as unknown). */
    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p) || !isAllowed(p)) {
            return false;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "reset" -> {
                crafted = 0;
                save();
                p.sendMessage(Component.text("Mace counter reset. " + limit + " can be crafted again.", NamedTextColor.GRAY));
            }
            case "limit" -> {
                if (args.length < 3) {
                    p.sendMessage(Component.text("/guilded mace limit <number>", NamedTextColor.GRAY));
                    return true;
                }
                try {
                    int n = Integer.parseInt(args[2]);
                    if (n < 0 || n > 100) {
                        throw new NumberFormatException();
                    }
                    limit = n;
                    save();
                    p.sendMessage(Component.text("Mace limit set to " + n + ".", NamedTextColor.GRAY));
                } catch (NumberFormatException e) {
                    p.sendMessage(Component.text("Limit must be 0-100.", NamedTextColor.GRAY));
                }
            }
            default -> {
                p.sendMessage(Component.text("Maces crafted: " + Math.min(crafted, 9999) + "/" + limit
                        + (readOnly ? " (data file unreadable - crafting locked)" : ""), NamedTextColor.GRAY));
                int from = Math.max(0, history.size() - 5);
                for (String h : history.subList(from, history.size())) {
                    p.sendMessage(Component.text("  " + h, NamedTextColor.DARK_GRAY));
                }
            }
        }
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p) || !isAllowed(p)) {
            return List.of();
        }
        if (args.length == 2) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("status", "reset", "limit")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
