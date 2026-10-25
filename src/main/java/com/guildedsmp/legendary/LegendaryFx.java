package com.guildedsmp.legendary;

import com.guildedsmp.items.CustomItem;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/** Particle effects for the legendary weapons. Counts are kept small to stay cheap for clients. */
final class LegendaryFx {

    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(0xFF6A1F), 1.1f);
    private static final Particle.DustOptions FROST = new Particle.DustOptions(Color.fromRGB(0xA8EEFF), 1.0f);
    private static final Particle.DustOptions VOID = new Particle.DustOptions(Color.fromRGB(0x7A2BFF), 1.2f);

    private LegendaryFx() {
    }

    static void hit(CustomItem weapon, LivingEntity victim) {
        World w = victim.getWorld();
        Location at = victim.getLocation().add(0, victim.getHeight() * 0.6, 0);
        switch (weapon) {
            case INFERNO_BLADE -> {
                w.spawnParticle(Particle.FLAME, at, 14, 0.3, 0.4, 0.3, 0.02);
                w.spawnParticle(Particle.LAVA, at, 3, 0.2, 0.2, 0.2, 0.0);
                w.spawnParticle(Particle.DUST, at, 10, 0.35, 0.45, 0.35, 0.0, EMBER);
            }
            case FROSTBITE -> {
                w.spawnParticle(Particle.SNOWFLAKE, at, 16, 0.35, 0.45, 0.35, 0.02);
                w.spawnParticle(Particle.ITEM_SNOWBALL, at, 6, 0.3, 0.3, 0.3, 0.05);
                w.spawnParticle(Particle.DUST, at, 8, 0.35, 0.45, 0.35, 0.0, FROST);
            }
            case VOID_REAPER -> {
                w.spawnParticle(Particle.REVERSE_PORTAL, at, 20, 0.3, 0.5, 0.3, 0.05);
                w.spawnParticle(Particle.DUST, at, 10, 0.35, 0.5, 0.35, 0.0, VOID);
            }
            default -> {
            }
        }
    }

    static void reap(LivingEntity killer) {
        World w = killer.getWorld();
        Location at = killer.getLocation().add(0, 1.0, 0);
        w.spawnParticle(Particle.PORTAL, at, 40, 0.5, 0.8, 0.5, 0.4);
        w.spawnParticle(Particle.DUST, at, 18, 0.4, 0.8, 0.4, 0.0, VOID);
    }

    /** A subtle trail near the held blade. */
    static void held(CustomItem weapon, LivingEntity holder) {
        World w = holder.getWorld();
        org.bukkit.util.Vector forward = holder.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() > 1.0E-4) {
            forward.normalize().multiply(0.45);
        }
        Location at = holder.getLocation().add(0, 1.0, 0).add(forward);
        switch (weapon) {
            case INFERNO_BLADE -> w.spawnParticle(Particle.SMALL_FLAME, at, 1, 0.08, 0.15, 0.08, 0.005);
            case FROSTBITE -> w.spawnParticle(Particle.SNOWFLAKE, at, 1, 0.08, 0.15, 0.08, 0.005);
            case VOID_REAPER -> w.spawnParticle(Particle.REVERSE_PORTAL, at, 2, 0.08, 0.15, 0.08, 0.005);
            default -> {
            }
        }
    }
}
