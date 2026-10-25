package com.guildedsmp.recipes;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Settings;
import com.guildedsmp.items.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A read-only menu that shows the legendary recipes. Every click and drag inside it is cancelled,
 * and the items shown are look-alike copies without a custom id, so even if an item somehow left
 * the menu it would be an ordinary renamed vanilla item.
 */
public final class RecipeMenu implements Listener {

    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT_SLOT = 25;
    private static final int ARROW_SLOT = 23;
    private static final int INFO_SLOT = 16;
    private static final int BACK_SLOT = 36;
    private static final int[] PICKER_SLOTS = {11, 13, 15};

    /** Identifies our inventories; holds what page is shown. */
    public static final class Holder implements InventoryHolder {
        private final CustomItem shown;
        private Inventory inventory;

        Holder(CustomItem shown) {
            this.shown = shown;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final GuildedSMP plugin;

    public RecipeMenu(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    public void openPicker(Player player) {
        Holder holder = new Holder(null);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Legendary Recipes", TextColor.color(0x00FFFF)));
        holder.inventory = inv;
        fill(inv);
        List<CustomItem> legendaries = CustomItem.legendaries();
        for (int i = 0; i < legendaries.size(); i++) {
            ItemStack icon = plugin.items().displayCopy(legendaries.get(i));
            icon.editMeta(meta -> {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.text("Click to view the recipe", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            inv.setItem(PICKER_SLOTS[i], icon);
        }
        player.openInventory(inv);
    }

    public void openRecipe(Player player, CustomItem result) {
        LegendaryRecipes.Definition def = plugin.recipes().definitionFor(result).orElse(null);
        if (def == null) {
            plugin.messages().send(player, "recipes.disabled");
            return;
        }
        Holder holder = new Holder(result);
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text(result.displayName + " Recipe", TextColor.color(result.color)));
        holder.inventory = inv;
        fill(inv);
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            inv.setItem(GRID_SLOTS[i], null);
        }
        int width = def.shape().get(0).length();
        for (int row = 0; row < def.shape().size(); row++) {
            for (int col = 0; col < width; col++) {
                char c = def.shape().get(row).charAt(col);
                LegendaryRecipes.Spec spec = def.ingredients().get(c);
                if (spec == null) {
                    continue;
                }
                ItemStack shown = spec.custom() != null
                        ? plugin.items().displayCopy(spec.custom())
                        : new ItemStack(spec.material());
                inv.setItem(GRID_SLOTS[row * 3 + col], shown);
            }
        }
        inv.setItem(ARROW_SLOT, named(Material.SPECTRAL_ARROW, Component.text("Crafts into", NamedTextColor.GRAY)));
        inv.setItem(RESULT_SLOT, plugin.items().displayCopy(result));
        inv.setItem(INFO_SLOT, infoBook(def));
        inv.setItem(BACK_SLOT, named(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW)));
        player.openInventory(inv);
    }

    private ItemStack infoBook(LegendaryRecipes.Definition def) {
        ItemStack book = new ItemStack(Material.BOOK);
        book.editMeta(meta -> {
            meta.displayName(Component.text("Ingredients", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (Map.Entry<String, Integer> e : def.counts().entrySet()) {
                lore.add(Component.text(e.getValue() + " × " + e.getKey(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Where to find them:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            for (LegendaryRecipes.Spec spec : def.ingredients().values()) {
                if (spec.custom() == null) {
                    continue;
                }
                lore.add(Component.text(spec.custom().displayName + ": " + dropText(spec.custom()), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Use a crafting table. Ingredients must be genuine.", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return book;
    }

    private String dropText(CustomItem item) {
        Settings.Drops drops = plugin.settings().drops;
        List<Settings.DropRule> rules = drops.table().getOrDefault(item.id, List.of());
        if (!drops.enabled() || rules.isEmpty()) {
            return "not currently obtainable from mobs";
        }
        List<String> parts = new ArrayList<>();
        for (Settings.DropRule r : rules) {
            parts.add(pretty(r.entity().name()) + " " + formatChance(r.chance()));
        }
        return String.join(", ", parts);
    }

    private static String pretty(String enumName) {
        String[] words = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static String formatChance(double chance) {
        double pct = chance * 100.0;
        return (pct == Math.rint(pct) ? String.valueOf((long) pct) : String.format(Locale.ROOT, "%.1f", pct)) + "%";
    }

    private static ItemStack named(Material m, Component name) {
        ItemStack item = new ItemStack(m);
        item.editMeta(meta -> meta.displayName(name.decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private static void fill(Inventory inv) {
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private static Holder holderOf(Inventory top) {
        return top.getHolder(false) instanceof Holder h ? h : null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Holder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.shown == null) {
            for (int i = 0; i < PICKER_SLOTS.length; i++) {
                if (PICKER_SLOTS[i] == slot) {
                    CustomItem target = CustomItem.legendaries().get(i);
                    Bukkit.getScheduler().runTask(plugin, () -> openRecipe(player, target));
                }
            }
        } else if (slot == BACK_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> openPicker(player));
        }
    }

    /** Re-cancel late, in case another plugin un-cancelled the click. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickLate(InventoryClickEvent event) {
        if (holderOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (holderOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }
}
