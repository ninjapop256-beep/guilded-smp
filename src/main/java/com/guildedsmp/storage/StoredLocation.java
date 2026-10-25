package com.guildedsmp.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

/**
 * A location that remembers its world by UUID and name, so it can be stored while the
 * world is unloaded and resolved safely later.
 */
public record StoredLocation(String worldName, UUID worldId, double x, double y, double z, float yaw, float pitch) {

    public static StoredLocation of(Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new StoredLocation(w.getName(), w.getUID(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    /** Returns the world if it is loaded, else null. */
    public World world() {
        World w = worldId == null ? null : Bukkit.getWorld(worldId);
        if (w == null && worldName != null) {
            w = Bukkit.getWorld(worldName);
        }
        return w;
    }

    /** Returns a Bukkit location, or null when the world is not loaded. */
    public Location toLocation() {
        World w = world();
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yaw) && Float.isFinite(pitch)
                && Math.abs(x) <= 30_000_000 && Math.abs(z) <= 30_000_000 && Math.abs(y) <= 20_000;
    }

    public void write(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("world-uuid", worldId == null ? null : worldId.toString());
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", (double) yaw);
        section.set("pitch", (double) pitch);
    }

    /** Reads a stored location; returns null when the section is missing or malformed. */
    public static StoredLocation read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String world = section.getString("world");
        String uuidRaw = section.getString("world-uuid");
        UUID uuid = null;
        if (uuidRaw != null) {
            try {
                uuid = UUID.fromString(uuidRaw);
            } catch (IllegalArgumentException ignored) {
                // fall back to the world name
            }
        }
        if (world == null && uuid == null) {
            return null;
        }
        if (!section.isSet("x") || !section.isSet("y") || !section.isSet("z")) {
            return null;
        }
        StoredLocation loc = new StoredLocation(world, uuid,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        return loc.isFinite() ? loc : null;
    }
}
