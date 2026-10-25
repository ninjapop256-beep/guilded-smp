package com.guildedsmp.items;

import com.guildedsmp.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds and recognises custom items. Recognition only trusts the persistent-data id AND the
 * expected base material; display names and lore are ignored, so a renamed vanilla item (or
 * a vanilla item with copied lore) is never treated as custom.
 */
public final class CustomItems {

    public static final int ITEM_FORMAT_VERSION = 1;

    private final Keys keys;

    public CustomItems(Keys keys) {
        this.keys = keys;
    }

    public ItemStack create(CustomItem type) {
        return create(type, 1);
    }

    public ItemStack create(CustomItem type, int amount) {
        ItemStack stack = new ItemStack(type.base, Math.max(1, Math.min(amount, type.base.getMaxStackSize())));
        stack.editMeta(meta -> {
            decorate(meta, type);
            var pdc = meta.getPersistentDataContainer();
            pdc.set(keys.itemId, PersistentDataType.STRING, type.id);
            pdc.set(keys.itemVersion, PersistentDataType.INTEGER, ITEM_FORMAT_VERSION);
            if (type.legendary) {
                pdc.set(keys.instanceId, PersistentDataType.STRING, UUID.randomUUID().toString());
            }
        });
        return stack;
    }

    /**
     * A look-alike for menus: same name, lore and model, but no custom id, so it is just a
     * renamed vanilla item that can never act as the real thing.
     */
    public ItemStack displayCopy(CustomItem type) {
        ItemStack stack = new ItemStack(type.base);
        stack.editMeta(meta -> decorate(meta, type));
        return stack;
    }

    private static void decorate(ItemMeta meta, CustomItem type) {
        TextColor color = TextColor.color(type.color);
        Component name = Component.text(type.displayName, color).decoration(TextDecoration.ITALIC, false);
        if (type.legendary) {
            name = name.decoration(TextDecoration.BOLD, true);
        }
        meta.displayName(name);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.legendary ? "✦ Legendary Weapon ✦" : "◆ Legendary Ingredient", color)
                .decoration(TextDecoration.ITALIC, false));
        for (String line : type.flavor) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }
        if (!type.effects.isEmpty()) {
            lore.add(Component.empty());
            for (String line : type.effects) {
                lore.add(Component.text("• " + line, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text("Guilded SMP", TextColor.color(0x00FFFF)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(type.modelKey()));
        meta.setCustomModelDataComponent(cmd);

        if (type.legendary) {
            meta.addEnchant(Enchantment.SHARPNESS, 6, true);
            if (type == CustomItem.INFERNO_BLADE) {
                meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            }
            meta.setRarity(ItemRarity.EPIC);
        } else {
            meta.setEnchantmentGlintOverride(true);
            meta.setRarity(ItemRarity.RARE);
        }
    }

    /** The custom type of a stack, or empty for vanilla / spoofed / mismatched items. */
    public Optional<CustomItem> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return Optional.empty();
        }
        String id = stack.getPersistentDataContainer().get(keys.itemId, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        Optional<CustomItem> type = CustomItem.byId(id);
        if (type.isEmpty() || type.get().base != stack.getType()) {
            return Optional.empty();
        }
        return type;
    }

    public boolean isCustom(ItemStack stack) {
        return identify(stack).isPresent();
    }

    /** True if the stack carries our id key at all (even a mismatched one): never treat it as vanilla. */
    public boolean hasCustomTag(ItemStack stack) {
        return stack != null && !stack.getType().isAir()
                && stack.getPersistentDataContainer().has(keys.itemId, PersistentDataType.STRING);
    }

    public boolean isLegendary(ItemStack stack) {
        return identify(stack).map(t -> t.legendary).orElse(false);
    }
}
