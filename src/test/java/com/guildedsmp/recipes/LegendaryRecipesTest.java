package com.guildedsmp.recipes;

import com.guildedsmp.PluginTestBase;
import com.guildedsmp.items.CustomItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryRecipesTest extends PluginTestBase {

    /** Builds a genuine 3x3 grid for a definition (optionally mirrored). */
    private ItemStack[] grid(LegendaryRecipes.Definition def, boolean mirror) {
        ItemStack[] m = new ItemStack[9];
        for (int row = 0; row < def.shape().size(); row++) {
            String line = def.shape().get(row);
            for (int col = 0; col < line.length(); col++) {
                LegendaryRecipes.Spec spec = def.ingredients().get(line.charAt(col));
                if (spec == null) {
                    continue;
                }
                int x = mirror ? line.length() - 1 - col : col;
                m[row * 3 + x] = spec.custom() != null ? plugin.items().create(spec.custom()) : new ItemStack(spec.material());
            }
        }
        return m;
    }

    private LegendaryRecipes.Definition def(CustomItem item) {
        return plugin.recipes().definitionFor(item).orElseThrow();
    }

    private int slotOf(LegendaryRecipes.Definition def, Material material) {
        for (int row = 0; row < def.shape().size(); row++) {
            for (int col = 0; col < def.shape().get(row).length(); col++) {
                LegendaryRecipes.Spec s = def.ingredients().get(def.shape().get(row).charAt(col));
                if (s != null && s.choiceMaterial() == material) {
                    return row * 3 + col;
                }
            }
        }
        throw new AssertionError(material + " not in recipe");
    }

    @Test
    void defaultRecipesUseExactlyTheRequestedIngredients() {
        assertEquals(Map.of("Netherite Sword", 1, "Inferno Core", 2, "Blaze Rod", 2, "Magma Block", 1,
                "Ember Crystal", 1, "Blaze Powder", 1), def(CustomItem.INFERNO_BLADE).counts());
        assertEquals(Map.of("Netherite Sword", 1, "Frost Core", 2, "Packed Ice", 2, "Blue Ice", 1,
                "Frozen Crystal", 1, "Prismarine Shard", 1), def(CustomItem.FROSTBITE).counts());
        assertEquals(Map.of("Netherite Sword", 1, "Void Shard", 2, "Ender Eye", 1, "Obsidian", 2,
                "Void Heart", 1, "Nether Star", 1), def(CustomItem.VOID_REAPER).counts());
    }

    @Test
    void genuineAndMirroredGridsMatch() {
        for (CustomItem item : CustomItem.legendaries()) {
            assertTrue(plugin.recipes().matches(def(item), grid(def(item), false)), item.id);
            assertTrue(plugin.recipes().matches(def(item), grid(def(item), true)), item.id + " mirrored");
        }
    }

    @Test
    void renamedVanillaItemCannotStandInForACustomIngredient() {
        LegendaryRecipes.Definition d = def(CustomItem.INFERNO_BLADE);
        ItemStack[] m = grid(d, false);
        int core = slotOf(d, Material.MAGMA_CREAM);
        ItemStack fake = new ItemStack(Material.MAGMA_CREAM);
        fake.editMeta(meta -> meta.displayName(Component.text("Inferno Core")));
        m[core] = fake;
        assertFalse(plugin.recipes().matches(d, m));
    }

    @Test
    void wrongCustomIngredientIsRejected() {
        LegendaryRecipes.Definition d = def(CustomItem.FROSTBITE);
        ItemStack[] m = grid(d, false);
        // Frost Core and Void Heart share no material, but Frost Core vs. a tagged heart-of-the-sea
        // with another id must still fail.
        int core = slotOf(d, Material.HEART_OF_THE_SEA);
        ItemStack other = new ItemStack(Material.HEART_OF_THE_SEA);
        other.editPersistentDataContainer(pdc -> pdc.set(plugin.keys().itemId, PersistentDataType.STRING, "void_heart"));
        m[core] = other;
        assertFalse(plugin.recipes().matches(d, m));
    }

    @Test
    void legendaryOrTaggedItemInAVanillaSlotIsRejected() {
        LegendaryRecipes.Definition d = def(CustomItem.VOID_REAPER);
        ItemStack[] m = grid(d, false);
        m[slotOf(d, Material.NETHERITE_SWORD)] = plugin.items().create(CustomItem.INFERNO_BLADE);
        assertFalse(plugin.recipes().matches(d, m), "a legendary can't be consumed as the base sword");

        ItemStack[] m2 = grid(d, false);
        ItemStack taggedObsidian = new ItemStack(Material.OBSIDIAN);
        taggedObsidian.editPersistentDataContainer(pdc -> pdc.set(plugin.keys().itemId, PersistentDataType.STRING, "x"));
        m2[slotOf(d, Material.OBSIDIAN)] = taggedObsidian;
        assertFalse(plugin.recipes().matches(d, m2));
    }

    @Test
    void missingOrExtraItemsAreRejected() {
        LegendaryRecipes.Definition d = def(CustomItem.INFERNO_BLADE);
        ItemStack[] missing = grid(d, false);
        missing[slotOf(d, Material.BLAZE_POWDER)] = null;
        assertFalse(plugin.recipes().matches(d, missing));

        ItemStack[] extra = grid(d, false);
        for (int i = 0; i < 9; i++) {
            if (extra[i] == null) {
                extra[i] = new ItemStack(Material.DIRT);
            }
        }
        assertFalse(plugin.recipes().matches(d, extra));
    }

    @Test
    void stackedIngredientsStillOnlyNeedOnePerSlot() {
        LegendaryRecipes.Definition d = def(CustomItem.INFERNO_BLADE);
        ItemStack[] m = grid(d, false);
        m[slotOf(d, Material.MAGMA_CREAM)].setAmount(10);
        assertTrue(plugin.recipes().matches(d, m), "vanilla consumes one per slot; the rest stays in the grid");
    }

    @Test
    void configCannotUseALegendaryAsAnIngredient() {
        assertNull(LegendaryRecipes.parseSpec("guildedsmp:inferno_blade"));
        assertNull(LegendaryRecipes.parseSpec("minecraft:not_a_real_item"));
        assertEquals(CustomItem.VOID_SHARD, LegendaryRecipes.parseSpec("guildedsmp:void_shard").custom());
        assertEquals(Material.BLAZE_ROD, LegendaryRecipes.parseSpec("minecraft:blaze_rod").material());
    }

    @Test
    void invalidConfiguredShapeFallsBackToDefault() {
        plugin.getConfig().set("recipes.frostbite.shape", List.of("FZF", "PS"));
        LegendaryRecipes.Definition loaded = plugin.recipes().load(CustomItem.FROSTBITE);
        assertEquals(LegendaryRecipes.defaults(plugin, CustomItem.FROSTBITE).shape(), loaded.shape());
    }
}
