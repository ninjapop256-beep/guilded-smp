package com.guildedsmp;

/** Permission nodes. The tiers (player &lt; moderator &lt; admin &lt; creator) are wired in plugin.yml. */
public final class Perms {

    public static final String PLAYER = "guilded.player";
    public static final String MODERATOR = "guilded.moderator";
    public static final String ADMIN = "guilded.admin";
    public static final String CREATOR = "guilded.creator";

    public static final String TPA = "guilded.tpa";
    public static final String TPA_BYPASS_COOLDOWN = "guilded.tpa.bypasscooldown";
    public static final String HOME = "guilded.home";
    public static final String HOMES_UNLIMITED = "guilded.homes.unlimited";
    public static final String HOMES_LIMIT_PREFIX = "guilded.homes.limit.";
    public static final String SPAWN = "guilded.spawn";
    public static final String SPAWN_FIND = "guilded.spawn.find";
    public static final String SPAWN_SET = "guilded.spawn.set";
    public static final String SPAWN_REMOVE = "guilded.spawn.remove";
    public static final String SPAWNPROTECT_VIEW = "guilded.spawnprotect.view";
    public static final String SPAWNPROTECT_MODIFY = "guilded.spawnprotect.modify";
    public static final String TELEPORT_BYPASS_WARMUP = "guilded.teleport.bypasswarmup";
    public static final String COMBAT_BYPASS = "guilded.combat.bypass";
    public static final String COMBAT_ADMIN = "guilded.combat.admin";
    public static final String MUTE = "guilded.mute";
    public static final String TEMPBAN = "guilded.tempban";
    public static final String UNBAN = "guilded.unban";
    public static final String MODERATION_EXEMPT = "guilded.moderation.exempt";
    public static final String RECIPES = "guilded.recipes";
    public static final String RELOAD = "guilded.reload";
    public static final String GIVE = "guilded.give";
    public static final String OPPROTECT_VIEW = "guilded.opprotect.view";

    private Perms() {
    }
}
