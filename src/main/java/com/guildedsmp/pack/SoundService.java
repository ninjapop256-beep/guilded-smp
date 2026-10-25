package com.guildedsmp.pack;

import com.guildedsmp.GuildedSMP;
import com.guildedsmp.config.Settings;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Plays Guilded SMP sound cues. Players who loaded the resource pack hear the custom sounds;
 * everyone else hears a fitting vanilla sound instead of silence.
 */
public final class SoundService {

    public enum Cue {
        INFERNO_HIT("guildedsmp:legendary.inferno.ignite", Sound.ITEM_FIRECHARGE_USE, 0.9f, 1.0f),
        FROST_HIT("guildedsmp:legendary.frostbite.chill", Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 1.4f),
        VOID_HIT("guildedsmp:legendary.void.drain", Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.6f),
        VOID_KILL("guildedsmp:legendary.void.reap", Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.8f),
        FORGE("guildedsmp:legendary.forge", Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);

        public final String customKey;
        public final Sound vanilla;
        public final float volume;
        public final float pitch;

        Cue(String customKey, Sound vanilla, float volume, float pitch) {
            this.customKey = customKey;
            this.vanilla = vanilla;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private static final double HEAR_RADIUS_SQ = 24.0 * 24.0;

    private final GuildedSMP plugin;

    public SoundService(GuildedSMP plugin) {
        this.plugin = plugin;
    }

    public void play(Location at, Cue cue) {
        World w = at.getWorld();
        if (w == null) {
            return;
        }
        Settings.SoundMode mode = plugin.settings().resourcePack.soundMode();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > HEAR_RADIUS_SQ) {
                continue;
            }
            boolean custom = switch (mode) {
                case CUSTOM -> true;
                case VANILLA -> false;
                case AUTO -> plugin.resourcePack().hasPack(p);
            };
            if (custom) {
                p.playSound(at, cue.customKey, SoundCategory.PLAYERS, cue.volume, 1.0f);
            } else {
                p.playSound(at, cue.vanilla, SoundCategory.PLAYERS, cue.volume, cue.pitch);
            }
        }
    }
}
