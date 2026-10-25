package com.guildedsmp.items;

import com.guildedsmp.PluginTestBase;
import com.guildedsmp.rules.OwnerCraftRules;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.view.AnvilView;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.AnvilInventoryMock;
import org.mockbukkit.mockbukkit.inventory.GrindstoneInventoryMock;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;
import org.mockbukkit.mockbukkit.inventory.WorkbenchInventoryMock;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Anvil, grindstone and crafting-grid protections, exercised through real event objects. */
class ProtectionEventsTest extends PluginTestBase {

    /** A crafting table whose matched recipe we choose (MockBukkit has no recipe matcher). */
    static final class Workbench extends WorkbenchInventoryMock {
        private final Recipe recipe;

        Workbench(Recipe recipe) {
            super(null);
            this.recipe = recipe;
        }

        @Override
        public Recipe getRecipe() {
            return recipe;
        }

        /** Paper's no-snapshot holder lookup (unimplemented in MockBukkit). */
        @Override
        public org.bukkit.inventory.InventoryHolder getHolder(boolean useSnapshot) {
            return getHolder();
        }

        /** Real Paper returns exactly the 9 grid slots; MockBukkit's mock also includes the result slot. */
        @Override
        public ItemStack[] getMatrix() {
            return java.util.Arrays.copyOf(getContents(), 9);
        }

        @Override
        public void setMatrix(ItemStack[] contents) {
            for (int i = 0; i < 9; i++) {
                setItem(i, i < contents.length ? contents[i] : null);
            }
        }
    }

    /** A crafting-table view; only the calls the events make are answered. */
    private static org.bukkit.inventory.InventoryView view(PlayerMock p, Inventory top) {
        return (org.bukkit.inventory.InventoryView) Proxy.newProxyInstance(ProtectionEventsTest.class.getClassLoader(),
                new Class<?>[]{org.bukkit.inventory.InventoryView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> top;
                    case "getBottomInventory" -> p.getInventory();
                    case "getPlayer" -> p;
                    case "getType" -> InventoryType.WORKBENCH;
                    case "convertSlot" -> args[0];
                    case "getSlotType" -> InventoryType.SlotType.RESULT;
                    case "getCursor", "getItem", "getTitle", "getOriginalTitle" -> null;
                    case "countSlots" -> 46;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static boolean empty(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    // ------------------------------------------------------------------ anvil

    private ItemStack anvil(PlayerMock p, ItemStack left, ItemStack right, ItemStack result) {
        AnvilInventoryMock inv = new AnvilInventoryMock(null);
        inv.setItem(0, left);
        inv.setItem(1, right);
        AnvilView view = (AnvilView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{AnvilView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> inv;
                    case "getBottomInventory" -> p.getInventory();
                    case "getPlayer" -> p;
                    case "getType" -> InventoryType.ANVIL;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        PrepareAnvilEvent event = new PrepareAnvilEvent(view, result);
        server.getPluginManager().callEvent(event);
        return event.getResult();
    }

    @Test
    void anvilRulesForLegendaries() {
        PlayerMock p = player("Smith");
        ItemStack blade = plugin.items().create(CustomItem.INFERNO_BLADE);

        ItemStack renamed = blade.clone();
        renamed.editMeta(m -> m.displayName(Component.text("Butter Knife")));
        assertTrue(empty(anvil(p, blade, null, renamed)), "rename is off by default");

        ItemStack repaired = blade.clone();
        assertFalse(empty(anvil(p, blade, new ItemStack(Material.NETHERITE_INGOT), repaired)), "repair allowed");

        assertTrue(empty(anvil(p, blade, new ItemStack(Material.DIRT), blade.clone())), "odd materials refused");

        ItemStack plain = new ItemStack(Material.NETHERITE_SWORD);
        assertTrue(empty(anvil(p, plain, blade, plain.clone())), "a legendary can't be sacrificed to copy Sharpness VI");

        ItemStack core = plugin.items().create(CustomItem.INFERNO_CORE);
        assertTrue(empty(anvil(p, core, null, core.clone())), "ingredients stay out of anvils");

        ItemStack normal = new ItemStack(Material.IRON_SWORD);
        ItemStack normalResult = normal.clone();
        normalResult.editMeta(m -> m.displayName(Component.text("Bob")));
        assertFalse(empty(anvil(p, normal, null, normalResult)), "vanilla anvil use is untouched");
    }

    @Test
    void grindstoneRefusesCustomItems() {
        PlayerMock p = player("Grinder");
        GrindstoneInventoryMock inv = new GrindstoneInventoryMock(null);
        inv.setItem(0, plugin.items().create(CustomItem.FROSTBITE));
        PrepareGrindstoneEvent event = new PrepareGrindstoneEvent(
                new SimpleInventoryViewMock(p, inv, p.getInventory(), InventoryType.GRINDSTONE), new ItemStack(Material.NETHERITE_SWORD));
        server.getPluginManager().callEvent(event);
        assertTrue(empty(event.getResult()));
    }

    // ------------------------------------------------------------------ crafting

    private ItemStack prepare(PlayerMock p, Recipe recipe, ItemStack[] matrix, ItemStack vanillaResult) {
        Workbench inv = new Workbench(recipe);
        inv.setMatrix(matrix);
        inv.setResult(vanillaResult);
        PrepareItemCraftEvent event = new PrepareItemCraftEvent(inv,
                new SimpleInventoryViewMock(p, inv, p.getInventory(), InventoryType.WORKBENCH), false);
        server.getPluginManager().callEvent(event);
        return inv.getResult();
    }

    @Test
    void customIngredientsCannotBeUsedInOtherRecipes() {
        PlayerMock p = player("Crafter");
        ShapedRecipe magmaBlock = new ShapedRecipe(NamespacedKey.minecraft("magma_block"), new ItemStack(Material.MAGMA_BLOCK));
        ItemStack[] grid = new ItemStack[9];
        grid[0] = plugin.items().create(CustomItem.INFERNO_CORE);
        grid[1] = new ItemStack(Material.MAGMA_CREAM);
        grid[3] = new ItemStack(Material.MAGMA_CREAM);
        grid[4] = new ItemStack(Material.MAGMA_CREAM);
        assertTrue(empty(prepare(p, magmaBlock, grid, new ItemStack(Material.MAGMA_BLOCK))));

        grid[0] = new ItemStack(Material.MAGMA_CREAM);
        assertFalse(empty(prepare(p, magmaBlock, grid, new ItemStack(Material.MAGMA_BLOCK))), "plain vanilla crafting still works");
    }

    @Test
    void legendaryRecipeOnlyResultsFromGenuineIngredients() {
        PlayerMock p = player("Crafter");
        var def = plugin.recipes().definitionFor(CustomItem.VOID_REAPER).orElseThrow();
        Recipe recipe = org.bukkit.Bukkit.getRecipe(def.key());
        assertNotNull(recipe);
        ItemStack[] grid = new ItemStack[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                var spec = def.ingredients().get(def.shape().get(row).charAt(col));
                if (spec != null) {
                    grid[row * 3 + col] = spec.custom() != null ? plugin.items().create(spec.custom()) : new ItemStack(spec.material());
                }
            }
        }
        ItemStack result = prepare(p, recipe, grid, null);
        assertEquals(CustomItem.VOID_REAPER, plugin.items().identify(result).orElseThrow());

        grid[1] = new ItemStack(Material.POPPED_CHORUS_FRUIT); // plain chorus fruit instead of the Void Heart
        assertTrue(empty(prepare(p, recipe, grid, result)));
    }

    // ------------------------------------------------------------------ mace

    private ShapedRecipe maceRecipe() {
        return new ShapedRecipe(OwnerCraftRules.MACE_RECIPE, new ItemStack(Material.MACE));
    }

    @Test
    void anyoneCanCraftTheFirstMace() {
        PlayerMock steve = server.addPlayer("Steve");
        ItemStack[] grid = new ItemStack[9];
        grid[1] = new ItemStack(Material.HEAVY_CORE);
        grid[4] = new ItemStack(Material.BREEZE_ROD);
        assertFalse(empty(prepare(steve, maceRecipe(), grid, new ItemStack(Material.MACE))));
    }

    @Test
    void onlyOneMaceEverAndOnlyByAPlainClick() {
        PlayerMock felix = server.addPlayer("Steve");
        Workbench inv = new Workbench(maceRecipe());
        inv.setResult(new ItemStack(Material.MACE));
        org.bukkit.inventory.InventoryView view = view(felix, inv);

        CraftItemEvent shift = new CraftItemEvent(maceRecipe(), view, InventoryType.SlotType.RESULT, 0,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(shift);
        assertTrue(shift.isCancelled(), "shift-click could craft several");

        CraftItemEvent click = new CraftItemEvent(maceRecipe(), view, InventoryType.SlotType.RESULT, 0,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        assertFalse(click.isCancelled());
        assertFalse(plugin.ownerRules().canCraftNow(felix), "the single server-wide mace has been crafted");

        PlayerMock bread = server.addPlayer("Itzbreadloaf");
        assertFalse(plugin.ownerRules().canCraftNow(bread), "not even the owners get a second one");
        ItemStack[] grid = new ItemStack[9];
        grid[1] = new ItemStack(Material.HEAVY_CORE);
        grid[4] = new ItemStack(Material.BREEZE_ROD);
        assertTrue(empty(prepare(bread, maceRecipe(), grid, new ItemStack(Material.MACE))), "result stays empty");
    }
}
