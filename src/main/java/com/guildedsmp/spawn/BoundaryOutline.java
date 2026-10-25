package com.guildedsmp.spawn;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws the spawn-protection square as a line of red wool using BlockDisplay entities.
 * <p>
 * Nothing in the world is changed: displays are client-side visuals with no collision. They are
 * spawned with {@code persistent=false}, so they are never saved to disk and cannot pile up across
 * restarts; any marked display found on chunk load (e.g. after a crash) is removed. One display
 * covers a run of edge blocks with the same ground height inside one chunk, so a flat 100-radius
 * border costs roughly 50 entities instead of 800.
 */
public final class BoundaryOutline implements Listener {

    /** One stretched display: start column, length along the axis, and Y. */
    record Segment(int x, int y, int z, int length, boolean alongX) {
    }

    private final GuildedSMP plugin;
    private final Map<Long, List<UUID>> displaysByChunk = new HashMap<>();
    private final Map<Long, List<Segment>> segmentsByChunk = new HashMap<>();
    private SquareRegion region;
    private BukkitTask refreshTask;
    private int entityCount;
    private boolean warnedLimit;
    private boolean spawnFailedWarned;

    public BoundaryOutline(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    private Settings.Outline cfg() {
        return plugin.settings().protection.outline();
    }

    public boolean isActive() {
        return region != null && entityCount > 0;
    }

    public int entityCount() {
        return entityCount;
    }

    /** Rebuild from scratch: call on enable, reload, spawn change or radius change. */
    public void rebuild() {
        clear();
        SquareRegion r = plugin.spawn().region();
        if (r == null || !cfg().enabled()) {
            return;
        }
        region = r;
        World w = Bukkit.getWorld(r.worldId());
        if (w == null) {
            return;
        }
        for (long key : perimeterChunks(r)) {
            int cx = (int) (key & 0xFFFFFFFFL);
            int cz = (int) (key >>> 32);
            if (w.isChunkLoaded(cx, cz)) {
                trySpawnChunk(w, cx, cz);
            }
        }
        int refresh = cfg().refreshIntervalSeconds();
        if (refresh > 0) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLoaded, refresh * 20L, refresh * 20L);
        }
    }

    /** Removes every display this plugin spawned. */
    public void clear() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (List<UUID> ids : displaysByChunk.values()) {
            for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
        }
        displaysByChunk.clear();
        segmentsByChunk.clear();
        entityCount = 0;
        warnedLimit = false;
        region = null;
    }

    /** Removes stale marked displays in every loaded world (startup safety net). */
    public void purgeStale() {
        for (World w : Bukkit.getWorlds()) {
            for (BlockDisplay d : w.getEntitiesByClass(BlockDisplay.class)) {
                if (isOurs(d) && !isTracked(d.getUniqueId())) {
                    d.remove();
                }
            }
        }
    }

    private boolean isOurs(Entity e) {
        return e.getPersistentDataContainer().has(plugin.keys().boundaryMarker, PersistentDataType.BYTE);
    }

    private boolean isTracked(UUID id) {
        for (List<UUID> ids : displaysByChunk.values()) {
            if (ids.contains(id)) {
                return true;
            }
        }
        return false;
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cz << 32) | (cx & 0xFFFFFFFFL);
    }

    static Set<Long> perimeterChunks(SquareRegion r) {
        Set<Long> out = new HashSet<>();
        for (int cx = r.minX() >> 4; cx <= r.maxX() >> 4; cx++) {
            out.add(chunkKey(cx, r.minZ() >> 4));
            out.add(chunkKey(cx, r.maxZ() >> 4));
        }
        for (int cz = r.minZ() >> 4; cz <= r.maxZ() >> 4; cz++) {
            out.add(chunkKey(r.minX() >> 4, cz));
            out.add(chunkKey(r.maxX() >> 4, cz));
        }
        return out;
    }

    /** Edge columns of the square that fall inside the given chunk, as straight runs. */
    static List<int[]> edgeRuns(SquareRegion r, int cx, int cz) {
        int x0 = cx << 4;
        int x1 = x0 + 15;
        int z0 = cz << 4;
        int z1 = z0 + 15;
        List<int[]> runs = new ArrayList<>(); // {startX, startZ, length, alongX(1/0)}
        // North and south edges (constant Z) own the corners.
        for (int z : new java.util.LinkedHashSet<>(List.of(r.minZ(), r.maxZ()))) {
            int from = Math.max(r.minX(), x0);
            int to = Math.min(r.maxX(), x1);
            if (z >= z0 && z <= z1 && from <= to) {
                runs.add(new int[]{from, z, to - from + 1, 1});
            }
        }
        // West and east edges (constant X), without the corners.
        for (int x : new java.util.LinkedHashSet<>(List.of(r.minX(), r.maxX()))) {
            int from = Math.max(r.minZ() + 1, z0);
            int to = Math.min(r.maxZ() - 1, z1);
            if (x >= x0 && x <= x1 && from <= to) {
                runs.add(new int[]{x, from, to - from + 1, 0});
            }
        }
        return runs;
    }

    private List<Segment> computeSegments(World w, int cx, int cz) {
        List<Segment> segments = new ArrayList<>();
        for (int[] run : edgeRuns(region, cx, cz)) {
            boolean alongX = run[3] == 1;
            int startX = run[0];
            int startZ = run[1];
            int segStart = 0;
            int segY = Integer.MIN_VALUE;
            for (int i = 0; i <= run[2]; i++) {
                int y = Integer.MIN_VALUE;
                if (i < run[2]) {
                    int bx = alongX ? startX + i : startX;
                    int bz = alongX ? startZ : startZ + i;
                    y = w.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                }
                if (i == run[2] || y != segY) {
                    if (i > segStart && segY != Integer.MIN_VALUE) {
                        int sx = alongX ? startX + segStart : startX;
                        int sz = alongX ? startZ : startZ + segStart;
                        segments.add(new Segment(sx, segY, sz, i - segStart, alongX));
                    }
                    segStart = i;
                    segY = y;
                }
            }
        }
        return segments;
    }

    /** Drawing the outline is cosmetic: a failure is logged once and never breaks protection. */
    private void trySpawnChunk(World w, int cx, int cz) {
        try {
            spawnChunk(w, cx, cz);
        } catch (RuntimeException e) {
            if (!spawnFailedWarned) {
                spawnFailedWarned = true;
                plugin.getLogger().warning("Could not draw the spawn outline: " + e);
            }
        }
    }

    private void spawnChunk(World w, int cx, int cz) {
        long key = chunkKey(cx, cz);
        removeChunk(key);
        List<Segment> segments = computeSegments(w, cx, cz);
        List<UUID> ids = new ArrayList<>();
        BlockData block = cfg().block().createBlockData();
        for (Segment s : segments) {
            if (entityCount >= cfg().maxEntities()) {
                if (!warnedLimit) {
                    warnedLimit = true;
                    plugin.getLogger().warning("Spawn outline reached max-entities (" + cfg().maxEntities()
                            + "); part of the border is not drawn. Raise spawn-protection.outline.max-entities if needed.");
                }
                break;
            }
            Location at = new Location(w, s.x(), s.y() + 0.001, s.z());
            try {
                BlockDisplay d = w.spawn(at, BlockDisplay.class, display -> configure(display, block, s));
                ids.add(d.getUniqueId());
                entityCount++;
            } catch (RuntimeException e) {
                if (!spawnFailedWarned) {
                    spawnFailedWarned = true;
                    plugin.getLogger().warning("Could not spawn spawn-outline display entities: " + e);
                }
                break;
            }
        }
        displaysByChunk.put(key, ids);
        segmentsByChunk.put(key, segments);
    }

    private void configure(BlockDisplay d, BlockData block, Segment s) {
        d.setPersistent(false);
        d.setBlock(block);
        float h = cfg().height();
        Vector3f scale = s.alongX() ? new Vector3f(s.length(), h, 1f) : new Vector3f(1f, h, s.length());
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), scale, new Quaternionf()));
        d.setDisplayWidth(Math.max(1, s.length()));
        d.setDisplayHeight(h);
        d.setViewRange(cfg().viewRange());
        d.setShadowRadius(0f);
        if (cfg().fullBright()) {
            d.setBrightness(new Display.Brightness(15, 15));
        }
        d.getPersistentDataContainer().set(plugin.keys().boundaryMarker, PersistentDataType.BYTE, (byte) 1);
    }

    private void removeChunk(long key) {
        List<UUID> ids = displaysByChunk.remove(key);
        segmentsByChunk.remove(key);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
            entityCount--;
        }
        entityCount = Math.max(0, entityCount);
    }

    /** Re-checks ground height in loaded border chunks and redraws only chunks that changed. */
    void refreshLoaded() {
        if (region == null) {
            return;
        }
        World w = Bukkit.getWorld(region.worldId());
        if (w == null) {
            return;
        }
        for (Long key : new ArrayList<>(segmentsByChunk.keySet())) {
            int cx = (int) (key & 0xFFFFFFFFL);
            int cz = (int) (key >>> 32);
            if (!w.isChunkLoaded(cx, cz)) {
                removeChunk(key);
                continue;
            }
            try {
                List<Segment> now = computeSegments(w, cx, cz);
                if (!now.equals(segmentsByChunk.get(key))) {
                    trySpawnChunk(w, cx, cz);
                }
            } catch (RuntimeException e) {
                // cosmetic only; keep the existing line
            }
        }
    }

    private boolean isBorderChunk(World w, int cx, int cz) {
        SquareRegion r = region;
        if (r == null || !w.getUID().equals(r.worldId())) {
            return false;
        }
        int x0 = cx << 4;
        int z0 = cz << 4;
        boolean xOverlap = x0 + 15 >= r.minX() && x0 <= r.maxX();
        boolean zOverlap = z0 + 15 >= r.minZ() && z0 <= r.maxZ();
        boolean touchesXEdge = (r.minX() >= x0 && r.minX() <= x0 + 15) || (r.maxX() >= x0 && r.maxX() <= x0 + 15);
        boolean touchesZEdge = (r.minZ() >= z0 && r.minZ() <= z0 + 15) || (r.maxZ() >= z0 && r.maxZ() <= z0 + 15);
        return (touchesXEdge && zOverlap) || (touchesZEdge && xOverlap);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk c = event.getChunk();
        if (!isBorderChunk(c.getWorld(), c.getX(), c.getZ())) {
            return;
        }
        World w = c.getWorld();
        int cx = c.getX();
        int cz = c.getZ();
        // Spawn on the next tick: adding entities during the load event itself is unsafe.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (region != null && w.isChunkLoaded(cx, cz) && !displaysByChunk.containsKey(chunkKey(cx, cz))) {
                trySpawnChunk(w, cx, cz);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk c = event.getChunk();
        if (region != null && c.getWorld().getUID().equals(region.worldId())) {
            removeChunk(chunkKey(c.getX(), c.getZ()));
        }
    }

    /** Safety net: a marked display loaded from disk can only be a leftover, so remove it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof BlockDisplay && isOurs(e) && !isTracked(e.getUniqueId())) {
                e.remove();
            }
        }
    }
}
