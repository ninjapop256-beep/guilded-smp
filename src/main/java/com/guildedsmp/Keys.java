package com.guildedsmp;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent-data keys. All live in the {@code guildedsmp} namespace. */
public final class Keys {

    /** Stable custom item id, e.g. "inferno_blade". This, not the display name, identifies an item. */
    public final NamespacedKey itemId;
    /** Item format version, for future migrations. */
    public final NamespacedKey itemVersion;
    /** Random per-item id for legendaries (helps audits of duplicated items). */
    public final NamespacedKey instanceId;
    /** Marks boundary display entities so stale ones can always be found and removed. */
    public final NamespacedKey boundaryMarker;

    public Keys(Plugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.itemVersion = new NamespacedKey(plugin, "item_version");
        this.instanceId = new NamespacedKey(plugin, "instance_id");
        this.boundaryMarker = new NamespacedKey(plugin, "spawn_boundary");
    }
}
