package com.guildedsmp.recipes;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Messages;
import com.guildedsmp.items.CustomItems;
import com.guildedsmp.pack.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.block.BlockState;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Guards all crafting:
 * <ul>
 *   <li>Legendary recipes only produce a result when every slot passes {@link LegendaryRecipes#matches}.</li>
 *   <li>The check runs again on the actual craft click, so a grid changed between preview and click is caught.</li>
 *   <li>Custom items can never be used in any other (vanilla or plugin) recipe, including repair.</li>
 *   <li>Crafters (auto-crafters) can't craft legendaries or use custom items at all.</li>
 * </ul>
 */
public final class RecipeListener implements Listener {

    private final GuildedSMP plugin;

    public RecipeListener(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private LegendaryRecipes.Definition ours(Recipe recipe) {
        if (recipe instanceof Keyed keyed) {
            return plugin.recipes().definition(keyed.getKey()).orElse(null);
        }
        return null;
    }

    private boolean anyCustom(ItemStack[] matrix) {
        CustomItems items = plugin.items();
        for (ItemStack it : matrix) {
            if (it != null && items.hasCustomTag(it)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        LegendaryRecipes.Definition def = ours(event.getRecipe());
        if (def != null) {
            inv.setResult(plugin.recipes().matches(def, matrix) ? plugin.items().create(def.result()) : null);
            return;
        }
        if (anyCustom(matrix)) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        LegendaryRecipes.Definition def = ours(event.getRecipe());
        if (def != null) {
            if (!plugin.recipes().matches(def, matrix)) {
                event.setCancelled(true);
                event.getInventory().setResult(null);
            }
            return;
        }
        if (anyCustom(matrix)) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrafted(CraftItemEvent event) {
        LegendaryRecipes.Definition def = ours(event.getRecipe());
        if (def == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.sounds().play(player.getLocation(), SoundService.Cue.FORGE);
        plugin.getLogger().info(player.getName() + " crafted " + def.result().displayName + ".");
        if (plugin.settings().legendaries.announceCrafts()) {
            Bukkit.broadcast(plugin.messages().prefixed("recipes.announce",
                    Messages.ph("player", player.getName()), Messages.ph("item", def.result().displayName)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (ours(event.getRecipe()) != null) {
            event.setCancelled(true);
            return;
        }
        BlockState state = event.getBlock().getState(false);
        if (state instanceof Crafter crafter && anyCustom(crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("recipes.discover-on-join", true)) {
            plugin.recipes().discover(event.getPlayer());
        }
    }
}
