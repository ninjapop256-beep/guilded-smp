package com.guildedsmp.spawn;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The square must be centred on spawn and include its edges: |x - cx| <= r and |z - cz| <= r. */
class SquareRegionTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void radius100IncludesAllFourEdgesAndExcludesOneBeyond() {
        SquareRegion r = new SquareRegion(WORLD, 0, 0, 100);
        assertTrue(r.contains(WORLD, 0, 0));
        assertTrue(r.contains(WORLD, 100, 0));
        assertTrue(r.contains(WORLD, -100, 0));
        assertTrue(r.contains(WORLD, 0, 100));
        assertTrue(r.contains(WORLD, 0, -100));
        assertTrue(r.contains(WORLD, 100, 100));
        assertTrue(r.contains(WORLD, -100, -100));
        assertFalse(r.contains(WORLD, 101, 0));
        assertFalse(r.contains(WORLD, -101, 0));
        assertFalse(r.contains(WORLD, 0, 101));
        assertFalse(r.contains(WORLD, 0, -101));
        assertFalse(r.contains(WORLD, 101, 101));
    }

    @Test
    void offCentreAndNegativeSpawn() {
        SquareRegion r = new SquareRegion(WORLD, -250, 1234, 50);
        assertEquals(-300, r.minX());
        assertEquals(-200, r.maxX());
        assertEquals(1184, r.minZ());
        assertEquals(1284, r.maxZ());
        assertTrue(r.contains(WORLD, -300, 1184));
        assertTrue(r.contains(WORLD, -200, 1284));
        assertFalse(r.contains(WORLD, -301, 1234));
        assertFalse(r.contains(WORLD, -199, 1234));
        assertFalse(r.contains(WORLD, -250, 1183));
        assertFalse(r.contains(WORLD, -250, 1285));
    }

    @Test
    void otherWorldsAreNeverProtected() {
        SquareRegion r = new SquareRegion(WORLD, 0, 0, 100);
        assertFalse(r.contains(OTHER, 0, 0));
    }

    @Test
    void edgeDetection() {
        SquareRegion r = new SquareRegion(WORLD, 10, 10, 3);
        assertTrue(r.onEdge(7, 10));
        assertTrue(r.onEdge(13, 13));
        assertFalse(r.onEdge(10, 10));
        assertFalse(r.onEdge(14, 10));
    }

    @Test
    void outlineRunsCoverEveryEdgeColumnExactlyOnce() {
        for (SquareRegion r : List.of(new SquareRegion(WORLD, 0, 0, 100), new SquareRegion(WORLD, -37, 15, 20),
                new SquareRegion(WORLD, 7, -9, 1))) {
            Set<Long> seen = new HashSet<>();
            int total = 0;
            for (long key : BoundaryOutline.perimeterChunks(r)) {
                int cx = (int) (key & 0xFFFFFFFFL);
                int cz = (int) (key >>> 32);
                for (int[] run : BoundaryOutline.edgeRuns(r, cx, cz)) {
                    for (int i = 0; i < run[2]; i++) {
                        int x = run[3] == 1 ? run[0] + i : run[0];
                        int z = run[3] == 1 ? run[1] : run[1] + i;
                        assertTrue(r.onEdge(x, z), "not on edge: " + x + "," + z);
                        assertTrue(seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)), "duplicate column " + x + "," + z);
                        total++;
                    }
                }
            }
            assertEquals(8 * r.radius(), total, "perimeter of radius " + r.radius());
        }
    }
}
