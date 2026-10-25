package com.guildedsmp.spawn;

import com.guildedsmp.PluginTestBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnProtectionTest extends PluginTestBase {

    private PlayerMock admin;

    @BeforeEach
    void setSpawn() {
        admin = player("Admin", 0.5, 0.5);
        admin.setOp(true);
        admin.performCommand("spawn set");
        drain(admin);
    }

    private Location at(double x, double z) {
        return new Location(world, x, 64, z);
    }

    @Test
    void firstSpawnSetEnablesARadius100SquareCentredOnSpawn() {
        SpawnManager spawn = plugin.spawn();
        assertEquals(SpawnManager.Status.ACTIVE, spawn.status());
        assertEquals(100, spawn.radius());
        assertTrue(spawn.isProtected(at(100.9, 100.9)));
        assertTrue(spawn.isProtected(at(-99.5, -99.5)), "block -100");
        assertTrue(spawn.isProtected(at(-100.0, 0)));
        assertFalse(spawn.isProtected(at(101.0, 0)));
        assertFalse(spawn.isProtected(at(-100.01, 0)), "block -101");
        assertFalse(spawn.isProtected(at(0, 101.2)));
        assertFalse(spawn.isProtected(at(0, -101)));
    }

    @Test
    void radiusIsCentredOnSpawnNotOnTheAdmin() {
        floor(400, 400);
        admin.teleport(at(400.5, 400.5));
        admin.performCommand("spawnprotect 50");
        SquareRegion r = plugin.spawn().region();
        assertEquals(0, r.centerX());
        assertEquals(0, r.centerZ());
        assertEquals(50, r.radius());
        assertTrue(said(drain(admin), "X -50 to 50"));
    }

    @Test
    void nonAdminsCannotChangeProtectionButCanSeeStatus() {
        PlayerMock steve = player("Steve", 500.5, 500.5);
        steve.performCommand("spawnprotect 10");
        assertEquals(100, plugin.spawn().radius());
        assertTrue(said(drain(steve), "permission"));
        steve.performCommand("spawnprotect status");
        assertTrue(said(drain(steve), "radius 100"));
        steve.performCommand("spawn set");
        assertTrue(said(drain(steve), "permission"));
    }

    @Test
    void removeAndRestore() {
        admin.performCommand("spawnprotect remove");
        assertFalse(plugin.spawn().isProtected(at(0, 0)));
        admin.performCommand("spawnprotect 30");
        assertTrue(plugin.spawn().isProtected(at(30, 0)));
        admin.performCommand("spawn remove");
        assertEquals(SpawnManager.Status.NO_SPAWN, plugin.spawn().status());
        assertFalse(plugin.spawn().isProtected(at(0, 0)), "suspended without a spawn");
        admin.teleport(at(0.5, 0.5));
        admin.performCommand("spawn set");
        assertTrue(plugin.spawn().isProtected(at(30, 0)), "resumes with the same radius");
    }

    @Test
    void pvpIsBlockedWhenEitherPlayerIsInside() {
        PlayerMock inside = player("Inside", 5.5, 5.5);
        PlayerMock outside = player("Outside", 101.5, 5.5);
        EntityDamageByEntityEvent e1 = melee(outside, inside, 3.0);
        assertTrue(e1.isCancelled());
        EntityDamageByEntityEvent e2 = melee(inside, outside, 3.0);
        assertTrue(e2.isCancelled(), "attacking out of the safe zone is blocked too");
        assertFalse(plugin.combat().isTagged(inside), "blocked hits never tag");
        assertFalse(plugin.combat().isTagged(outside));

        PlayerMock far = player("Far", 102.5, 5.5);
        assertFalse(melee(far, outside, 3.0).isCancelled(), "PvP outside works");
        assertTrue(plugin.combat().isTagged(outside));
    }

    @Test
    void hostileNaturalSpawnsAreBlockedInsideOnly() {
        Zombie z = world.spawn(at(3, 3), Zombie.class);
        CreatureSpawnEvent natural = new CreatureSpawnEvent(z, CreatureSpawnEvent.SpawnReason.NATURAL);
        server.getPluginManager().callEvent(natural);
        assertTrue(natural.isCancelled());
        CreatureSpawnEvent spawner = new CreatureSpawnEvent(z, CreatureSpawnEvent.SpawnReason.SPAWNER);
        server.getPluginManager().callEvent(spawner);
        assertFalse(spawner.isCancelled());
        Zombie outside = world.spawn(at(300, 300), Zombie.class);
        CreatureSpawnEvent naturalOutside = new CreatureSpawnEvent(outside, CreatureSpawnEvent.SpawnReason.NATURAL);
        server.getPluginManager().callEvent(naturalOutside);
        assertFalse(naturalOutside.isCancelled());
    }

    /** A mob stand-in answering only what the sweep asks (MockBukkit lacks the despawn flag). */
    private static org.bukkit.entity.Entity mob(Class<? extends org.bukkit.entity.Entity> type, boolean despawnable,
                                               boolean named, boolean leashed) {
        return (org.bukkit.entity.Entity) java.lang.reflect.Proxy.newProxyInstance(SpawnProtectionTest.class.getClassLoader(),
                new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRemoveWhenFarAway" -> despawnable;
                    case "customName" -> named ? net.kyori.adventure.text.Component.text("Bob") : null;
                    case "isLeashed", "isInsideVehicle" -> leashed && method.getName().equals("isLeashed");
                    case "getPassengers" -> java.util.List.of();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @Test
    void sweepOnlyRemovesHostilesTheGameWouldDespawnAnyway() {
        assertTrue(SpawnProtectionListener.isRemovableHostile(mob(Zombie.class, true, false, false)));
        assertFalse(SpawnProtectionListener.isRemovableHostile(mob(Zombie.class, false, false, false)), "persistence required");
        assertFalse(SpawnProtectionListener.isRemovableHostile(mob(Zombie.class, true, true, false)), "name-tagged");
        assertFalse(SpawnProtectionListener.isRemovableHostile(mob(Zombie.class, true, false, true)), "leashed");
        assertFalse(SpawnProtectionListener.isRemovableHostile(mob(org.bukkit.entity.Wither.class, true, false, false)), "bosses");
        assertFalse(SpawnProtectionListener.isRemovableHostile(mob(org.bukkit.entity.Cow.class, true, false, false)), "passive mobs");
    }

    @Test
    void taggedPlayersCannotWalkOrTeleportInButCanLeave() {
        PlayerMock a = player("A", 101.5, 0.5);
        PlayerMock b = player("B", 102.5, 0.5);
        melee(b, a, 2.0);
        assertTrue(plugin.combat().isTagged(a));

        PlayerMoveEvent walkIn = new PlayerMoveEvent(a, at(101.5, 0.5), at(100.5, 0.5));
        server.getPluginManager().callEvent(walkIn);
        assertTrue(walkIn.isCancelled());

        PlayerTeleportEvent pearl = new PlayerTeleportEvent(a, at(101.5, 0.5), at(10, 10), PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        server.getPluginManager().callEvent(pearl);
        assertTrue(pearl.isCancelled());

        PlayerMoveEvent walkOut = new PlayerMoveEvent(a, at(99.5, 0.5), at(101.5, 0.5));
        server.getPluginManager().callEvent(walkOut);
        assertFalse(walkOut.isCancelled(), "never trapped inside");

        PlayerMoveEvent untagged = new PlayerMoveEvent(admin, at(101.5, 0.5), at(100.5, 0.5));
        server.getPluginManager().callEvent(untagged);
        assertFalse(untagged.isCancelled());
    }

    @Test
    void outlineUsesMarkedDisplaysAndNeverChangesBlocks() {
        for (int cx = -7; cx <= 6; cx++) {
            for (int cz = -7; cz <= 6; cz++) {
                world.getChunkAt(cx, cz).load();
            }
        }
        world.getBlockAt(100, 70, 0).setType(Material.OAK_PLANKS); // a "player build" on the border
        plugin.outline().rebuild();
        List<BlockDisplay> displays = world.getEntitiesByClass(BlockDisplay.class).stream()
                .filter(d -> d.getPersistentDataContainer().has(plugin.keys().boundaryMarker, PersistentDataType.BYTE))
                .toList();
        assertFalse(displays.isEmpty(), "outline drawn");
        assertEquals(displays.size(), plugin.outline().entityCount());
        for (BlockDisplay d : displays) {
            assertEquals(Material.RED_WOOL, d.getBlock().getMaterial());
            assertFalse(d.isPersistent(), "never saved to disk");
        }
        assertEquals(Material.OAK_PLANKS, world.getBlockAt(100, 70, 0).getType(), "world blocks untouched");
        assertEquals(Material.AIR, world.getBlockAt(-100, 90, 50).getType());

        admin.performCommand("spawnprotect remove");
        long left = world.getEntitiesByClass(BlockDisplay.class).stream().filter(org.bukkit.entity.Entity::isValid).count();
        assertEquals(0, left, "outline removed with protection");
    }

    @Test
    void spawnPersistsToDisk() {
        assertNotNull(plugin.spawn().spawn().orElse(null));
        server.getPluginManager().disablePlugin(plugin);
        java.io.File f = new java.io.File(plugin.getDataFolder(), "data/spawn.yml");
        assertTrue(f.isFile());
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        assertEquals("world", y.getString("spawn.world"));
        assertEquals(100, y.getInt("protection.radius"));
        assertTrue(y.getBoolean("protection.enabled"));
    }
}
