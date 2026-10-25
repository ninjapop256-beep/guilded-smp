package com.guildedsmp.util;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;

/** Works out which player (if any) is responsible for a damaging entity. */
public final class Attackers {

    private Attackers() {
    }

    /** A player hitting directly, or the player who fired a projectile (arrow, trident, potion, ...). */
    public static Player directOrProjectile(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    /**
     * Broader attribution used for safe-zone protection: also lingering-potion clouds,
     * TNT lit by a player, and a player's tamed pets.
     */
    public static Player responsiblePlayer(Entity damager) {
        Player p = directOrProjectile(damager);
        if (p != null) {
            return p;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player owner) {
            return owner;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player owner) {
            return owner;
        }
        if (damager instanceof Tameable pet && pet.isTamed() && pet.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }
}
