package com.guildedsmp.items;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Settings;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.Objects;

/**
 * Stops custom items from being changed or consumed in ways that break their rules:
 * <ul>
 *   <li>Anvil: legendaries may only be repaired (netherite ingot / plain netherite sword) or take
 *       enchanted books, per config; renaming is off by default. A custom item in the right-hand
 *       slot is always refused, so Sharpness VI can't be moved onto a normal sword.</li>
 *   <li>Grindstone, smithing table, enchanting table: custom items are refused.</li>
 *   <li>Brewing stands: custom ingredients can't be inserted (some share a vanilla base item).</li>
 * </ul>
 */
public final class ItemProtectionListener implements Listener {

    private final GuildedSMP plugin;

    public ItemProtectionListener(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private CustomItems items() {
        return plugin.items();
    }

    private boolean tagged(ItemStack it) {
        return it != null && items().hasCustomTag(it);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack left = inv.getFirstItem();
        ItemStack right = inv.getSecondItem();
        if (tagged(right)) {
            event.setResult(null);
            return;
        }
        if (!tagged(left)) {
            return;
        }
        if (!items().isLegendary(left)) {
            event.setResult(null);
            return;
        }
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        Settings.Legendaries cfg = plugin.settings().legendaries;
        Component before = left.getItemMeta().displayName();
        Component after = result.getItemMeta().displayName();
        if (!cfg.allowRename() && !Objects.equals(before, after)) {
            event.setResult(null);
            return;
        }
        boolean rightEmpty = right == null || right.getType().isAir();
        if (rightEmpty) {
            return;
        }
        Material m = right.getType();
        boolean allowed = switch (m) {
            case NETHERITE_INGOT, NETHERITE_SWORD -> cfg.allowAnvilRepair();
            case ENCHANTED_BOOK -> cfg.allowAnvilBooks();
            default -> false;
        };
        if (!allowed || !items().isLegendary(result)) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inv = event.getInventory();
        if (tagged(inv.getUpperItem()) || tagged(inv.getLowerItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        if (tagged(inv.getInputTemplate()) || tagged(inv.getInputEquipment()) || tagged(inv.getInputMineral())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (tagged(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (tagged(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (tagged(event.getContents().getIngredient())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrewerClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.BREWING) {
            return;
        }
        boolean intoTop = event.getClickedInventory() == top;
        if (intoTop && (tagged(event.getCursor())
                || (event.getHotbarButton() >= 0 && tagged(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()))))) {
            event.setCancelled(true);
            return;
        }
        if (!intoTop && event.isShiftClick() && tagged(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrewerDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.BREWING || !tagged(event.getOldCursor())) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() == InventoryType.BREWING && tagged(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
