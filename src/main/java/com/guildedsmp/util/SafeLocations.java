package com.guildedsmp.util;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.EnumSet;
import java.util.Set;

/** Checks teleport destinations for hazards. Must be called on the main thread with the chunk loaded. */
public final class SafeLocations {

    /** How strict the destination check is. */
    public enum Mode {
        /** Homes: the exact spot, or up to 3 blocks above/below it. */
        EXACT_OR_NEARBY,
        /** Spawn: the exact spot, else the surface at the same X/Z. */
        SURFACE,
        /** TPA: the target is standing there; only reject hazardous blocks. */
        LENIENT
    }

    private static final Set<Material> HAZARDS = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CACTUS, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.POWDER_SNOW, Material.END_PORTAL, Material.NETHER_PORTAL,
            Material.END_GATEWAY, Material.POINTED_DRIPSTONE);

    private SafeLocations() {
    }

    public static boolean isHazard(Material m) {
        return HAZARDS.contains(m);
    }

    static boolean isClear(Block b) {
        Material m = b.getType();
        return !HAZARDS.contains(m) && (m.isAir() || b.isPassable());
    }

    static boolean isGround(Block b) {
        Material m = b.getType();
        if (HAZARDS.contains(m)) {
            return false;
        }
        return m.isSolid() || m == Material.WATER || Tag.CLIMBABLE.isTagged(m);
    }

    static boolean inWorldBounds(World w, int y) {
        return y > w.getMinHeight() && y < w.getMaxHeight() - 1;
    }

    /** Feet and head are clear, the block below is something to stand on, nothing hazardous. */
    public static boolean isSafeStanding(Location loc) {
        World w = loc.getWorld();
        if (w == null || !inWorldBounds(w, loc.getBlockY())) {
            return false;
        }
        Block feet = w.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return isClear(feet) && isClear(feet.getRelative(BlockFace.UP)) && isGround(feet.getRelative(BlockFace.DOWN));
    }

    /** Only checks for hazards at feet/head height (target players may be swimming or climbing). */
    public static boolean isHazardFree(Location loc) {
        World w = loc.getWorld();
        if (w == null || !inWorldBounds(w, loc.getBlockY())) {
            return false;
        }
        Block feet = w.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return isClear(feet) && isClear(feet.getRelative(BlockFace.UP));
    }

    /** Returns a safe location for the mode, or null when none is acceptable. */
    public static Location resolve(Location target, Mode mode) {
        if (target == null || target.getWorld() == null) {
            return null;
        }
        switch (mode) {
            case LENIENT -> {
                return isHazardFree(target) ? target : null;
            }
            case EXACT_OR_NEARBY -> {
                if (isSafeStanding(target)) {
                    return target;
                }
                for (int dy = 1; dy <= 3; dy++) {
                    Location up = target.clone().add(0, dy, 0);
                    if (isSafeStanding(up)) {
                        return up;
                    }
                    Location down = target.clone().add(0, -dy, 0);
                    if (isSafeStanding(down)) {
                        return down;
                    }
                }
                return null;
            }
            case SURFACE -> {
                if (isSafeStanding(target)) {
                    return target;
                }
                World w = target.getWorld();
                int y = w.getHighestBlockYAt(target.getBlockX(), target.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                Location surface = new Location(w, target.getBlockX() + 0.5, y, target.getBlockZ() + 0.5,
                        target.getYaw(), target.getPitch());
                return isSafeStanding(surface) ? surface : null;
            }
            default -> {
                return null;
            }
        }
    }
}
