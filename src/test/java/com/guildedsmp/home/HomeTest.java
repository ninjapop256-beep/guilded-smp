package com.guildedsmp.home;

import com.guildedsmp.PluginTestBase;
import com.guildedsmp.storage.IoExecutor;
import com.guildedsmp.storage.StoredLocation;
import com.guildedsmp.storage.YamlStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeTest extends PluginTestBase {

    @Test
    void setAndTeleportHome() {
        PlayerMock p = player("Homer", 1000.5, 1000.5);
        p.performCommand("sethome base");
        assertTrue(said(drain(p), "Home base set"));
        floor(1200, 1200);
        p.teleport(new Location(world, 1200.5, 64, 1200.5));
        p.performCommand("home base");
        server.getScheduler().performTicks(61);
        assertEquals(1000, p.getLocation().getBlockX());
        assertTrue(said(drain(p), "Teleported to home base"));
    }

    @Test
    void limitsAndNames() {
        PlayerMock p = player("Homer", 1000.5, 1000.5);
        for (String n : new String[]{"a", "b", "c"}) {
            p.performCommand("sethome " + n);
        }
        drain(p);
        p.performCommand("sethome d");
        assertTrue(said(drain(p), "home limit"));
        p.performCommand("sethome a");
        assertTrue(said(drain(p), "moved here"), "overwriting an existing home is allowed at the limit");
        p.performCommand("sethome bad!name");
        assertTrue(said(drain(p), "Home names may use"));
        p.performCommand("delhome b");
        assertTrue(said(drain(p), "deleted"));
        p.performCommand("home nope");
        assertTrue(said(drain(p), "no home called nope"));
    }

    @Test
    void unsafeSpotsAndUnloadedWorldsAreHandled() {
        PlayerMock p = player("Homer", 1000.5, 1000.5);
        world.getBlockAt(1000, 63, 1000).setType(Material.AIR); // standing over a hole
        p.performCommand("sethome air");
        assertTrue(said(drain(p), "can't set a home here"));

        plugin.homes().set(p, "gone", new StoredLocation("deleted_world", UUID.randomUUID(), 1, 64, 1, 0, 0));
        p.performCommand("home gone");
        assertTrue(said(drain(p), "isn't loaded"));
    }

    @Test
    void homeIsRefusedWhenItBecameUnsafe() {
        PlayerMock p = player("Homer", 1000.5, 1000.5);
        p.performCommand("sethome pit");
        for (int dy = -4; dy <= 4; dy++) {
            world.getBlockAt(1000, 64 + dy, 1000).setType(dy == 0 || dy == 1 ? Material.LAVA : Material.AIR);
        }
        floor(1100, 1100);
        p.teleport(new Location(world, 1100.5, 64, 1100.5));
        drain(p);
        p.performCommand("home pit");
        server.getScheduler().performTicks(61);
        assertEquals(1100, p.getLocation().getBlockX());
        assertTrue(said(drain(p), "isn't safe"));
    }

    @Test
    void homesSurviveARestart() {
        PlayerMock p = player("Homer", 1000.5, 1000.5);
        p.performCommand("sethome keep");
        UUID id = p.getUniqueId();
        server.getPluginManager().disablePlugin(plugin); // flushes pending writes
        File file = new File(plugin.getDataFolder(), "data/homes.yml");
        assertTrue(file.isFile());
        IoExecutor io = new IoExecutor(plugin.getLogger());
        HomeManager reloaded = new HomeManager(plugin, new YamlStore(file, io, plugin.getLogger()));
        StoredLocation home = reloaded.get(id, "keep");
        assertNotNull(home);
        assertEquals(1000, home.blockX());
        assertEquals("world", home.worldName());
        io.shutdown();
    }
}
