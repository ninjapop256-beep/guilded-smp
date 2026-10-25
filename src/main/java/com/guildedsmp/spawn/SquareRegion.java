package com.guildedsmp.spawn;

import java.util.UUID;

/**
 * A square of block columns centred on the spawn block.
 * A column (x, z) is inside when {@code |x - centerX| <= radius && |z - centerZ| <= radius},
 * so radius 100 covers 201 x 201 columns, edges included. Block coordinates are floored
 * world coordinates (Location#getBlockX), which handles negative coordinates correctly.
 */
public record SquareRegion(UUID worldId, int centerX, int centerZ, int radius) {

    public SquareRegion {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0");
        }
    }

    public boolean contains(UUID world, int blockX, int blockZ) {
        return worldId.equals(world)
                && Math.abs((long) blockX - centerX) <= radius
                && Math.abs((long) blockZ - centerZ) <= radius;
    }

    public int minX() {
        return centerX - radius;
    }

    public int maxX() {
        return centerX + radius;
    }

    public int minZ() {
        return centerZ - radius;
    }

    public int maxZ() {
        return centerZ + radius;
    }

    /** True when the column lies on the outer ring of the square. */
    public boolean onEdge(int blockX, int blockZ) {
        boolean inside = Math.abs((long) blockX - centerX) <= radius && Math.abs((long) blockZ - centerZ) <= radius;
        return inside && (blockX == minX() || blockX == maxX() || blockZ == minZ() || blockZ == maxZ());
    }
}
