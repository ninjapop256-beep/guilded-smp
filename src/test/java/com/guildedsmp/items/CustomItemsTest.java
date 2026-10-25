package com.guildedsmp.items;

import com.guildedsmp.PluginTestBase;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomItemsTest extends PluginTestBase {

    @Test
    void legendariesHaveSharpnessSixAndCorrectExtras() {
        for (CustomItem type : CustomItem.legendaries()) {
            ItemStack item = plugin.items().create(type);
            assertEquals(Material.NETHERITE_SWORD, item.getType());
            assertEquals(6, item.getEnchantmentLevel(Enchantment.SHARPNESS), type.id);
            assertEquals(type == CustomItem.INFERNO_BLADE ? 2 : 0, item.getEnchantmentLevel(Enchantment.FIRE_ASPECT), type.id);
            assertEquals(type, plugin.items().identify(item).orElseThrow());
            assertEquals(List.of(type.modelKey()), item.getItemMeta().getCustomModelDataComponent().getStrings());
        }
    }

    @Test
    void everyLegendaryGetsItsOwnInstanceId() {
        ItemStack a = plugin.items().create(CustomItem.VOID_REAPER);
        ItemStack b = plugin.items().create(CustomItem.VOID_REAPER);
        String idA = a.getPersistentDataContainer().get(plugin.keys().instanceId, PersistentDataType.STRING);
        String idB = b.getPersistentDataContainer().get(plugin.keys().instanceId, PersistentDataType.STRING);
        assertNotEquals(idA, idB);
    }

    @Test
    void ingredientsIdentifyAndStack() {
        for (CustomItem type : CustomItem.ingredients()) {
            ItemStack stack = plugin.items().create(type, 5);
            assertEquals(5, stack.getAmount());
            assertEquals(type, plugin.items().identify(stack).orElseThrow());
            assertTrue(stack.isSimilar(plugin.items().create(type)), type.id + " stacks with a fresh copy");
        }
    }

    @Test
    void renamedVanillaItemsWithCopiedLoreAreNotCustom() {
        ItemStack real = plugin.items().create(CustomItem.FROSTBITE);
        ItemStack fake = new ItemStack(Material.NETHERITE_SWORD);
        fake.editMeta(meta -> {
            meta.displayName(real.getItemMeta().displayName());
            meta.lore(real.getItemMeta().lore());
            meta.addEnchant(Enchantment.SHARPNESS, 6, true);
        });
        assertTrue(plugin.items().identify(fake).isEmpty());
        assertFalse(plugin.items().hasCustomTag(fake));

        ItemStack fakeCore = new ItemStack(Material.MAGMA_CREAM);
        fakeCore.editMeta(meta -> meta.displayName(Component.text("Inferno Core")));
        assertTrue(plugin.items().identify(fakeCore).isEmpty());
    }

    @Test
    void customIdOnTheWrongMaterialIsRejected() {
        ItemStack stick = new ItemStack(Material.STICK);
        stick.editPersistentDataContainer(pdc -> pdc.set(plugin.keys().itemId, PersistentDataType.STRING, "inferno_blade"));
        assertTrue(plugin.items().identify(stick).isEmpty());
        assertTrue(plugin.items().hasCustomTag(stick), "still treated as non-vanilla, so it can't be used in recipes");
    }

    @Test
    void menuDisplayCopiesAreNotRealItems() {
        ItemStack copy = plugin.items().displayCopy(CustomItem.INFERNO_BLADE);
        assertTrue(plugin.items().identify(copy).isEmpty());
        assertFalse(plugin.items().hasCustomTag(copy));
    }
}
