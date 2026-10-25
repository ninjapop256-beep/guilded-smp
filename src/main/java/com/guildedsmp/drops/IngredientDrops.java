package com.guildedsmp.drops;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Settings;
import com.guildedsmp.items.CustomItem;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boss;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Custom ingredients drop from mobs, using the chances in config (drops.table).
 * By default only kills credited to a player count. Boss drops (Ender Dragon, Wither) go straight
 * into the killer's inventory so they can't fall into the End portal; overflow drops at their feet.
 */
public final class IngredientDrops implements Listener {

    private final GuildedSMP plugin;
    private DoubleSupplier random = () -> ThreadLocalRandom.current().nextDouble();

    public IngredientDrops(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    public void setRandom(DoubleSupplier random) {
        this.random = random;
    }

    /** Which custom items can drop from an entity type, with their base chances. */
    Map<CustomItem, Double> rulesFor(EntityType type) {
        Map<CustomItem, Double> out = new HashMap<>();
        for (Map.Entry<String, List<Settings.DropRule>> e : plugin.settings().drops.table().entrySet()) {
            CustomItem item = CustomItem.byId(e.getKey()).orElse(null);
            if (item == null || item.legendary) {
                continue;
            }
            for (Settings.DropRule rule : e.getValue()) {
                if (rule.entity() == type) {
                    out.merge(item, rule.chance(), Math::max);
                }
            }
        }
        return out;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Settings.Drops cfg = plugin.settings().drops;
        if (!cfg.enabled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) {
            return;
        }
        Player killer = dead.getKiller();
        if (cfg.requirePlayerKill() && killer == null) {
            return;
        }
        Map<CustomItem, Double> rules = rulesFor(dead.getType());
        if (rules.isEmpty()) {
            return;
        }
        ItemStack weapon = killer == null ? null : killer.getInventory().getItemInMainHand();
        int looting = weapon == null || weapon.getType().isAir() ? 0 : weapon.getEnchantmentLevel(Enchantment.LOOTING);
        List<ItemStack> won = new ArrayList<>();
        for (Map.Entry<CustomItem, Double> rule : rules.entrySet()) {
            double chance = Math.min(1.0, rule.getValue() + looting * cfg.lootingBonusPerLevel());
            if (random.getAsDouble() < chance) {
                won.add(plugin.items().create(rule.getKey()));
            }
        }
        if (won.isEmpty()) {
            return;
        }
        if (dead instanceof Boss && killer != null && cfg.bossDropsToKiller()) {
            for (ItemStack overflow : killer.getInventory().addItem(won.toArray(new ItemStack[0])).values()) {
                killer.getWorld().dropItemNaturally(killer.getLocation(), overflow);
            }
            plugin.messages().send(killer, "drops.boss-reward");
        } else {
            event.getDrops().addAll(won);
        }
    }
}
