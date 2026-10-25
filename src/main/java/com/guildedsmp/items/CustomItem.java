package com.guildedsmp.items;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Every Guilded SMP custom item. The {@link #id} is the stable identifier stored in the item's
 * persistent data; it never changes, even if the display name or lore does.
 * The base material is what clients without the resource pack will see.
 */
public enum CustomItem {

    INFERNO_BLADE("inferno_blade", Material.NETHERITE_SWORD, true, "Inferno Blade", 0xFF5A1F,
            List.of("Forged in the heart of a dying blaze."),
            List.of("Chance to set foes ablaze", "Fire Resistance while held")),
    FROSTBITE("frostbite", Material.NETHERITE_SWORD, true, "Frostbite", 0x7FE3FF,
            List.of("Carved from ice that never melts."),
            List.of("Chance to slow foes", "Speed while held")),
    VOID_REAPER("void_reaper", Material.NETHERITE_SWORD, true, "Void Reaper", 0xA45CFF,
            List.of("It hungers for what the void left behind."),
            List.of("Chance to weaken foes", "Strength on a player kill")),

    INFERNO_CORE("inferno_core", Material.MAGMA_CREAM, false, "Inferno Core", 0xFF7B2E,
            List.of("A sphere of compressed blaze fire."), List.of()),
    EMBER_CRYSTAL("ember_crystal", Material.RESIN_BRICK, false, "Ember Crystal", 0xFFB347,
            List.of("A crystal glowing with trapped embers."), List.of()),
    FROST_CORE("frost_core", Material.HEART_OF_THE_SEA, false, "Frost Core", 0x5FD3FF,
            List.of("Cold enough to frost the air around it."), List.of()),
    FROZEN_CRYSTAL("frozen_crystal", Material.PRISMARINE_CRYSTALS, false, "Frozen Crystal", 0xC8F6FF,
            List.of("A shard of ancient, unmelting ice."), List.of()),
    VOID_SHARD("void_shard", Material.ECHO_SHARD, false, "Void Shard", 0xB07CFF,
            List.of("A sliver of the End's emptiness."), List.of()),
    VOID_HEART("void_heart", Material.POPPED_CHORUS_FRUIT, false, "Void Heart", 0x7A2BFF,
            List.of("It beats with the silence of the void."), List.of());

    public static final String NAMESPACE = "guildedsmp";

    public final String id;
    public final Material base;
    public final boolean legendary;
    public final String displayName;
    public final int color;
    public final List<String> flavor;
    public final List<String> effects;

    CustomItem(String id, Material base, boolean legendary, String displayName, int color,
               List<String> flavor, List<String> effects) {
        this.id = id;
        this.base = base;
        this.legendary = legendary;
        this.displayName = displayName;
        this.color = color;
        this.flavor = flavor;
        this.effects = effects;
    }

    /** The custom_model_data string the resource pack selects on, e.g. "guildedsmp:inferno_blade". */
    public String modelKey() {
        return NAMESPACE + ":" + id;
    }

    public static Optional<CustomItem> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String s = id.toLowerCase(Locale.ROOT);
        if (s.startsWith(NAMESPACE + ":")) {
            s = s.substring(NAMESPACE.length() + 1);
        }
        for (CustomItem c : values()) {
            if (c.id.equals(s)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public static List<CustomItem> legendaries() {
        return List.of(INFERNO_BLADE, FROSTBITE, VOID_REAPER);
    }

    public static List<CustomItem> ingredients() {
        return List.of(INFERNO_CORE, EMBER_CRYSTAL, FROST_CORE, FROZEN_CRYSTAL, VOID_SHARD, VOID_HEART);
    }
}
