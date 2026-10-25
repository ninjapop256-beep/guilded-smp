package com.guildedsmp.recipes;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.items.CustomItem;
import com.guildedsmp.items.CustomItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The three legendary crafting recipes. They are registered as normal shaped recipes (so they
 * appear in the recipe book and work in a crafting table), but every craft is re-validated by
 * {@link #matches}: custom ingredients must carry the right custom id, and vanilla ingredients
 * must be plain vanilla items (a renamed item or another custom item is rejected).
 */
public final class LegendaryRecipes {

    /** One ingredient slot: either a custom ingredient or a plain vanilla material. */
    public record Spec(CustomItem custom, Material material) {
        public Material choiceMaterial() {
            return custom != null ? custom.base : material;
        }

        public String describe() {
            return custom != null ? custom.displayName : prettify(material);
        }
    }

    public record Definition(CustomItem result, NamespacedKey key, List<String> shape, Map<Character, Spec> ingredients) {
        /** How many of each ingredient one craft consumes. */
        public Map<String, Integer> counts() {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (String row : shape) {
                for (char c : row.toCharArray()) {
                    Spec s = ingredients.get(c);
                    if (s != null) {
                        out.merge(s.describe(), 1, Integer::sum);
                    }
                }
            }
            return out;
        }
    }

    private static final Map<CustomItem, String> PATHS = Map.of(
            CustomItem.INFERNO_BLADE, "recipes.inferno-blade",
            CustomItem.FROSTBITE, "recipes.frostbite",
            CustomItem.VOID_REAPER, "recipes.void-reaper");

    private final GuildedSMP plugin;
    private final Map<NamespacedKey, Definition> registered = new LinkedHashMap<>();

    public LegendaryRecipes(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    public Collection<Definition> definitions() {
        return Collections.unmodifiableCollection(registered.values());
    }

    public Optional<Definition> definition(NamespacedKey key) {
        return Optional.ofNullable(registered.get(key));
    }

    public Optional<Definition> definitionFor(CustomItem result) {
        return registered.values().stream().filter(d -> d.result() == result).findFirst();
    }

    public static Definition defaults(GuildedSMP plugin, CustomItem result) {
        NamespacedKey key = new NamespacedKey(plugin, result.id);
        return switch (result) {
            case INFERNO_BLADE -> new Definition(result, key, List.of("CEC", "RSR", "PM "), Map.of(
                    'C', new Spec(CustomItem.INFERNO_CORE, null),
                    'E', new Spec(CustomItem.EMBER_CRYSTAL, null),
                    'R', new Spec(null, Material.BLAZE_ROD),
                    'S', new Spec(null, Material.NETHERITE_SWORD),
                    'P', new Spec(null, Material.BLAZE_POWDER),
                    'M', new Spec(null, Material.MAGMA_BLOCK)));
            case FROSTBITE -> new Definition(result, key, List.of("FZF", "PSP", "BH "), Map.of(
                    'F', new Spec(CustomItem.FROST_CORE, null),
                    'Z', new Spec(CustomItem.FROZEN_CRYSTAL, null),
                    'P', new Spec(null, Material.PACKED_ICE),
                    'S', new Spec(null, Material.NETHERITE_SWORD),
                    'B', new Spec(null, Material.BLUE_ICE),
                    'H', new Spec(null, Material.PRISMARINE_SHARD)));
            case VOID_REAPER -> new Definition(result, key, List.of("VHV", "OSO", "EN "), Map.of(
                    'V', new Spec(CustomItem.VOID_SHARD, null),
                    'H', new Spec(CustomItem.VOID_HEART, null),
                    'O', new Spec(null, Material.OBSIDIAN),
                    'S', new Spec(null, Material.NETHERITE_SWORD),
                    'E', new Spec(null, Material.ENDER_EYE),
                    'N', new Spec(null, Material.NETHER_STAR)));
            default -> throw new IllegalArgumentException(result + " has no recipe");
        };
    }

    /** Reads one recipe from config, falling back to the default (with a warning) when invalid. */
    Definition load(CustomItem result) {
        Definition fallback = defaults(plugin, result);
        String path = PATHS.get(result);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null) {
            return fallback;
        }
        List<String> shape = sec.getStringList("shape");
        ConfigurationSection ing = sec.getConfigurationSection("ingredients");
        List<String> problems = new ArrayList<>();
        Map<Character, Spec> specs = new LinkedHashMap<>();
        if (shape.isEmpty() || shape.size() > 3) {
            problems.add("shape must have 1-3 rows");
        } else {
            int width = shape.get(0).length();
            for (String row : shape) {
                if (row.length() != width || width < 1 || width > 3) {
                    problems.add("every shape row must have the same length (1-3)");
                    break;
                }
            }
        }
        if (ing == null) {
            problems.add("ingredients section is missing");
        } else {
            for (String k : ing.getKeys(false)) {
                if (k.length() != 1 || k.charAt(0) == ' ') {
                    problems.add("ingredient key '" + k + "' must be a single character");
                    continue;
                }
                String raw = ing.getString(k, "");
                Spec spec = parseSpec(raw);
                if (spec == null) {
                    problems.add("ingredient '" + k + "' = '" + raw + "' is not a guildedsmp ingredient or vanilla item");
                } else {
                    specs.put(k.charAt(0), spec);
                }
            }
        }
        if (problems.isEmpty()) {
            boolean hasSword = false;
            for (String row : shape) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !specs.containsKey(c)) {
                        problems.add("shape uses '" + c + "' but no ingredient is defined for it");
                    }
                    Spec s = specs.get(c);
                    if (s != null && s.material() == Material.NETHERITE_SWORD) {
                        hasSword = true;
                    }
                }
            }
            if (!hasSword) {
                plugin.getLogger().warning("[config] " + path + " does not use a netherite sword.");
            }
        }
        if (!problems.isEmpty()) {
            plugin.getLogger().warning("[config] " + path + " is invalid (" + String.join("; ", problems) + "); using the default recipe.");
            return fallback;
        }
        return new Definition(result, fallback.key(), List.copyOf(shape), Map.copyOf(specs));
    }

    static Spec parseSpec(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith(CustomItem.NAMESPACE + ":")) {
            return CustomItem.byId(s).filter(c -> !c.legendary).map(c -> new Spec(c, null)).orElse(null);
        }
        Material m = Material.matchMaterial(s);
        if (m == null || !m.isItem() || m.isAir()) {
            return null;
        }
        return new Spec(null, m);
    }

    public void register(boolean resend) {
        unregister(false);
        if (!plugin.getConfig().getBoolean("recipes.enabled", true)) {
            return;
        }
        for (CustomItem result : CustomItem.legendaries()) {
            Definition def = load(result);
            ShapedRecipe recipe = new ShapedRecipe(def.key(), plugin.items().create(result));
            recipe.shape(def.shape().toArray(new String[0]));
            for (Map.Entry<Character, Spec> e : def.ingredients().entrySet()) {
                recipe.setIngredient(e.getKey(), new RecipeChoice.MaterialChoice(e.getValue().choiceMaterial()));
            }
            recipe.setCategory(CraftingBookCategory.EQUIPMENT);
            recipe.setGroup("guildedsmp_legendary");
            if (Bukkit.addRecipe(recipe, resend)) {
                registered.put(def.key(), def);
            } else {
                plugin.getLogger().warning("Could not register recipe " + def.key());
            }
        }
    }

    public void unregister(boolean resend) {
        for (NamespacedKey key : registered.keySet()) {
            Bukkit.removeRecipe(key, resend);
        }
        registered.clear();
    }

    public void discover(Player p) {
        if (!registered.isEmpty()) {
            p.discoverRecipes(registered.keySet());
        }
    }

    /** Validates a crafting grid against a definition (shape may be shifted or mirrored, like vanilla). */
    public boolean matches(Definition def, ItemStack[] matrix) {
        return matches(def.shape(), def.ingredients(), matrix, plugin.items());
    }

    static boolean matches(List<String> shape, Map<Character, Spec> specs, ItemStack[] matrix, CustomItems items) {
        int grid = (int) Math.round(Math.sqrt(matrix.length));
        if (grid * grid != matrix.length) {
            return false;
        }
        int h = shape.size();
        int w = shape.get(0).length();
        if (h > grid || w > grid) {
            return false;
        }
        for (boolean mirror : new boolean[]{false, true}) {
            for (int oy = 0; oy <= grid - h; oy++) {
                for (int ox = 0; ox <= grid - w; ox++) {
                    if (fits(shape, specs, matrix, items, grid, ox, oy, mirror)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean fits(List<String> shape, Map<Character, Spec> specs, ItemStack[] matrix, CustomItems items,
                                int grid, int ox, int oy, boolean mirror) {
        int h = shape.size();
        int w = shape.get(0).length();
        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                int sy = gy - oy;
                int sx = gx - ox;
                char c = ' ';
                if (sy >= 0 && sy < h && sx >= 0 && sx < w) {
                    c = shape.get(sy).charAt(mirror ? w - 1 - sx : sx);
                }
                if (!slotMatches(c == ' ' ? null : specs.get(c), matrix[gy * grid + gx], items)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean slotMatches(Spec spec, ItemStack item, CustomItems items) {
        boolean empty = item == null || item.getType().isAir() || item.getAmount() <= 0;
        if (spec == null) {
            return empty;
        }
        if (empty) {
            return false;
        }
        if (spec.custom() != null) {
            return items.identify(item).filter(t -> t == spec.custom()).isPresent();
        }
        // Vanilla slot: must be the plain material, never a custom (or custom-tagged) item.
        return item.getType() == spec.material() && !items.hasCustomTag(item);
    }

    static String prettify(Material m) {
        String[] words = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
