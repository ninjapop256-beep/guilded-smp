package com.guildedsmp.spawn;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.storage.StoredLocation;
import com.guildedsmp.storage.YamlStore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spawn point and spawn-protection state, stored in data/spawn.yml (never in config.yml, so
 * config edits cannot wipe it). Protection only applies while a spawn is set and its world is
 * loaded; removing the spawn suspends protection until a spawn is set again.
 */
public final class SpawnManager implements Listener {

    public enum Status { ACTIVE, DISABLED, NO_SPAWN, WORLD_UNLOADED }

    private final GuildedSMP plugin;
    private final YamlStore store;
    private final List<Runnable> changeListeners = new ArrayList<>();
    private StoredLocation spawn;
    private boolean protectionEnabled;
    private boolean protectionConfigured;
    private int radius;
    private SquareRegion region;
    private boolean readOnly;

    public SpawnManager(GuildedSMP plugin, YamlStore store) {
        this.plugin = plugin;
        this.store = store;
        load();
        recompute();
    }

    private void load() {
        YamlConfiguration yaml = store.load();
        if (yaml == null) {
            readOnly = true;
            yaml = new YamlConfiguration();
        }
        spawn = StoredLocation.read(yaml.getConfigurationSection("spawn"));
        if (yaml.isSet("spawn") && spawn == null) {
            plugin.getLogger().warning("spawn.yml: the stored spawn is malformed; spawn is treated as not set.");
        }
        protectionConfigured = yaml.isSet("protection.enabled");
        protectionEnabled = yaml.getBoolean("protection.enabled", false);
        radius = yaml.getInt("protection.radius", plugin.settings().protection.defaultRadius());
        int max = plugin.settings().protection.maxRadius();
        if (radius < 1 || radius > max) {
            plugin.getLogger().warning("spawn.yml: protection radius " + radius + " is outside 1-" + max + "; clamped.");
            radius = Math.max(1, Math.min(max, radius));
        }
    }

    private void save() {
        if (readOnly) {
            plugin.getLogger().severe("spawn.yml could not be read at startup; spawn changes are not saved to protect it.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        if (spawn != null) {
            spawn.write(yaml.createSection("spawn"));
        }
        if (protectionConfigured) {
            yaml.set("protection.enabled", protectionEnabled);
            yaml.set("protection.radius", radius);
        }
        store.saveAsync(yaml);
    }

    public void onChange(Runnable listener) {
        changeListeners.add(listener);
    }

    private void changed() {
        recompute();
        for (Runnable r : changeListeners) {
            r.run();
        }
    }

    private void recompute() {
        World w = spawn == null ? null : spawn.world();
        region = (w == null || !protectionEnabled) ? null : new SquareRegion(w.getUID(), spawn.blockX(), spawn.blockZ(), radius);
    }

    public Optional<StoredLocation> spawn() {
        return Optional.ofNullable(spawn);
    }

    /** The spawn as a Bukkit location, or null when not set or its world is not loaded. */
    public Location spawnLocation() {
        return spawn == null ? null : spawn.toLocation();
    }

    public int radius() {
        return radius;
    }

    public boolean protectionEnabled() {
        return protectionEnabled;
    }

    /** The active protected square, or null when protection is off or suspended. */
    public SquareRegion region() {
        return region;
    }

    public Status status() {
        if (!protectionEnabled) {
            return Status.DISABLED;
        }
        if (spawn == null) {
            return Status.NO_SPAWN;
        }
        if (spawn.world() == null) {
            return Status.WORLD_UNLOADED;
        }
        return Status.ACTIVE;
    }

    /** Hot path (movement, damage): a cached region and two subtractions. */
    public boolean isProtected(Location loc) {
        SquareRegion r = region;
        if (r == null || loc == null) {
            return false;
        }
        World w = loc.getWorld();
        return w != null && r.contains(w.getUID(), loc.getBlockX(), loc.getBlockZ());
    }

    public void setSpawn(Location loc) {
        spawn = StoredLocation.of(loc);
        if (!protectionConfigured && plugin.settings().protection.enableOnFirstSpawnSet()) {
            protectionConfigured = true;
            protectionEnabled = true;
            radius = plugin.settings().protection.defaultRadius();
        }
        save();
        changed();
    }

    /** Removes the spawn point only. No world blocks or player data are touched. */
    public boolean removeSpawn() {
        if (spawn == null) {
            return false;
        }
        spawn = null;
        save();
        changed();
        return true;
    }

    public void setRadius(int newRadius) {
        radius = newRadius;
        protectionEnabled = true;
        protectionConfigured = true;
        save();
        changed();
    }

    public boolean disableProtection() {
        boolean was = protectionEnabled;
        protectionEnabled = false;
        protectionConfigured = true;
        save();
        changed();
        return was;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (spawn != null && region == null && protectionEnabled && spawn.world() != null) {
            changed();
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        if (region != null && region.worldId().equals(event.getWorld().getUID())) {
            region = null;
            for (Runnable r : changeListeners) {
                r.run();
            }
        }
    }
}
