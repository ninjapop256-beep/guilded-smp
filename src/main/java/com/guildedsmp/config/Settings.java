package com.guildedsmp.config;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable, validated snapshot of config.yml. Every value falls back to a safe default
 * (with a console warning) when it is missing, of the wrong type, or out of range.
 */
public final class Settings {

    public enum CombatDisplay { ACTION_BAR, BOSS_BAR, BOTH, NONE }

    public enum CombatLogPenalty { KILL, NONE }

    public enum KillTrigger { PLAYERS, PLAYERS_AND_HOSTILES }

    public enum SoundMode { AUTO, CUSTOM, VANILLA }

    public record OpProtection(boolean enabled, List<String> protectedPlayers, boolean blockDeopCommands,
                               int checkIntervalSeconds, boolean allowOfflineMode, boolean grantCreatorPermission) {
    }

    public record CombatLog(boolean enabled, CombatLogPenalty penalty, boolean broadcast,
                            Set<PlayerQuitEvent.QuitReason> penalizeReasons) {
    }

    public record Combat(int tagDurationSeconds, CombatDisplay display, String displayFormat, String bossBarColor,
                         boolean notifyOnTag, boolean notifyOnExpire, boolean blockCommandTeleports,
                         boolean blockPortals, Set<String> blockedCommands, CombatLog combatLog) {
    }

    public record Teleport(int warmupSeconds, boolean cancelOnMove, boolean cancelOnDamage) {
    }

    public record Tpa(int requestTimeoutSeconds, int cooldownSeconds) {
    }

    public record Homes(int defaultLimit, Map<String, Integer> permissionLimits, int maxNameLength) {
    }

    public record Spawn(boolean teleportOnFirstJoin, boolean respawnAtSpawnWithoutBed) {
    }

    public record Outline(boolean enabled, Material block, float height, boolean fullBright, float viewRange,
                          int refreshIntervalSeconds, int maxEntities) {
    }

    public record Protection(int defaultRadius, int maxRadius, boolean enableOnFirstSpawnSet, boolean blockPvp,
                             boolean blockHostileMobDamage, boolean blockHostileSpawns,
                             Set<CreatureSpawnEvent.SpawnReason> hostileSpawnReasons, boolean blockHostileTargeting,
                             boolean removeWanderingHostiles, int hostileSweepIntervalSeconds,
                             boolean blockCombatTaggedEntry, Outline outline) {
    }

    public record Inferno(double igniteChance, int igniteSeconds, boolean fireResistanceWhileHeld) {
    }

    public record Frostbite(double slownessChance, int slownessSeconds, int slownessAmplifier,
                            boolean speedWhileHeld, int speedAmplifier) {
    }

    public record VoidReaper(double weaknessChance, int weaknessSeconds, int weaknessAmplifier,
                             int killStrengthSeconds, int killStrengthAmplifier, KillTrigger killTrigger) {
    }

    public record Legendaries(boolean requireFullAttackCharge, int passiveCheckIntervalTicks,
                              int passiveEffectDurationTicks, boolean allowAnvilRepair, boolean allowAnvilBooks,
                              boolean allowRename, boolean heldParticles, boolean announceCrafts,
                              Inferno inferno, Frostbite frostbite, VoidReaper voidReaper) {
    }

    public record DropRule(EntityType entity, double chance) {
    }

    public record Drops(boolean enabled, boolean requirePlayerKill, double lootingBonusPerLevel,
                        boolean bossDropsToKiller, Map<String, List<DropRule>> table) {
    }

    public record TabList(boolean enabled, int refreshIntervalTicks, boolean smpShadow, int smpShadowArgb,
                          List<String> headerExtraLines, String footerFormat, int pingGood, int pingOkay,
                          boolean disableOnConflict) {
    }

    public record ResourcePack(boolean enabled, String url, String sha1, boolean required, String prompt,
                               int sendDelayTicks, SoundMode soundMode) {
    }

    public record Moderation(Set<String> mutedBlockedCommands, long maxTempbanSeconds) {
    }

    public final OpProtection opProtection;
    public final Combat combat;
    public final Teleport teleport;
    public final Tpa tpa;
    public final Homes homes;
    public final Spawn spawn;
    public final Protection protection;
    public final Legendaries legendaries;
    public final Drops drops;
    public final TabList tab;
    public final ResourcePack resourcePack;
    public final Moderation moderation;
    private final List<String> warnings;

    private Settings(Reader r) {
        // --- operator protection
        List<String> names = new ArrayList<>();
        for (String raw : r.stringList("operator-protection.protected-players", List.of("Itzbreadloaf", "FelixSyn"))) {
            String trimmed = raw == null ? "" : raw.trim();
            String namePart = trimmed.contains(":") ? trimmed.substring(0, trimmed.indexOf(':')) : trimmed;
            if (namePart.matches("[A-Za-z0-9_]{1,16}")) {
                names.add(trimmed);
            } else if (!trimmed.isEmpty()) {
                r.warn("operator-protection.protected-players: '" + trimmed + "' is not a valid Minecraft username; ignored.");
            }
        }
        opProtection = new OpProtection(
                r.bool("operator-protection.enabled", true),
                List.copyOf(names),
                r.bool("operator-protection.block-deop-commands", true),
                r.integer("operator-protection.check-interval-seconds", 30, 5, 3600),
                r.bool("operator-protection.allow-offline-mode", false),
                r.bool("operator-protection.grant-creator-permission", true));

        // --- combat
        Set<PlayerQuitEvent.QuitReason> reasons = EnumSet.noneOf(PlayerQuitEvent.QuitReason.class);
        for (String s : r.stringList("combat.combat-log.penalize-reasons", List.of("DISCONNECTED", "TIMED_OUT"))) {
            try {
                reasons.add(PlayerQuitEvent.QuitReason.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                r.warn("combat.combat-log.penalize-reasons: unknown quit reason '" + s + "'.");
            }
        }
        combat = new Combat(
                r.integer("combat.tag-duration-seconds", 20, 1, 600),
                r.enumValue("combat.display", CombatDisplay.class, CombatDisplay.ACTION_BAR),
                r.string("combat.display-format", "<red><bold>⚔ IN COMBAT</bold></red> <dark_gray>—</dark_gray> <white><seconds>s</white>"),
                r.string("combat.boss-bar-color", "RED").toUpperCase(Locale.ROOT),
                r.bool("combat.notify-on-tag", true),
                r.bool("combat.notify-on-expire", true),
                r.bool("combat.block-command-teleports", true),
                r.bool("combat.block-portals", false),
                lowerSet(r.stringList("combat.blocked-commands", List.of())),
                new CombatLog(
                        r.bool("combat.combat-log.enabled", true),
                        r.enumValue("combat.combat-log.penalty", CombatLogPenalty.class, CombatLogPenalty.KILL),
                        r.bool("combat.combat-log.broadcast", true),
                        Collections.unmodifiableSet(reasons)));

        teleport = new Teleport(
                r.integer("teleport.warmup-seconds", 3, 0, 60),
                r.bool("teleport.cancel-on-move", true),
                r.bool("teleport.cancel-on-damage", true));

        tpa = new Tpa(
                r.integer("tpa.request-timeout-seconds", 60, 5, 600),
                r.integer("tpa.cooldown-seconds", 30, 0, 3600));

        Map<String, Integer> limits = new LinkedHashMap<>();
        ConfigurationSection limitSec = r.section("homes.limits");
        if (limitSec != null) {
            for (String key : limitSec.getKeys(false)) {
                if (!key.matches("[a-z0-9_-]{1,32}")) {
                    r.warn("homes.limits." + key + ": key must be lowercase letters, digits, _ or -; ignored.");
                    continue;
                }
                int v = r.integer("homes.limits." + key, -1, 0, 1000);
                if (v >= 0) {
                    limits.put(key, v);
                }
            }
        }
        homes = new Homes(r.integer("homes.default-limit", 3, 0, 1000), Collections.unmodifiableMap(limits),
                r.integer("homes.max-name-length", 16, 1, 32));

        spawn = new Spawn(r.bool("spawn.teleport-on-first-join", true), r.bool("spawn.respawn-at-spawn-without-bed", true));

        Set<CreatureSpawnEvent.SpawnReason> spawnReasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String s : r.stringList("spawn-protection.hostile-spawn-reasons",
                List.of("NATURAL", "JOCKEY", "REINFORCEMENTS", "PATROL", "VILLAGE_INVASION"))) {
            try {
                spawnReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                r.warn("spawn-protection.hostile-spawn-reasons: unknown spawn reason '" + s + "'.");
            }
        }
        Material outlineBlock = Material.matchMaterial(r.string("spawn-protection.outline.block", "RED_WOOL"));
        if (outlineBlock == null || !outlineBlock.isBlock()) {
            r.warn("spawn-protection.outline.block must be a block material; using RED_WOOL.");
            outlineBlock = Material.RED_WOOL;
        }
        int maxRadius = r.integer("spawn-protection.max-radius", 5000, 1, 100_000);
        protection = new Protection(
                Math.min(r.integer("spawn-protection.default-radius", 100, 1, 100_000), maxRadius),
                maxRadius,
                r.bool("spawn-protection.enable-on-first-spawn-set", true),
                r.bool("spawn-protection.block-pvp", true),
                r.bool("spawn-protection.block-hostile-mob-damage", true),
                r.bool("spawn-protection.block-hostile-spawns", true),
                Collections.unmodifiableSet(spawnReasons),
                r.bool("spawn-protection.block-hostile-targeting", true),
                r.bool("spawn-protection.remove-wandering-hostiles", true),
                r.integer("spawn-protection.hostile-sweep-interval-seconds", 5, 1, 300),
                r.bool("spawn-protection.block-combat-tagged-entry", true),
                new Outline(
                        r.bool("spawn-protection.outline.enabled", true),
                        outlineBlock,
                        (float) r.decimal("spawn-protection.outline.height", 1.0, 0.0625, 4.0),
                        r.bool("spawn-protection.outline.full-bright", true),
                        (float) r.decimal("spawn-protection.outline.view-range", 1.0, 0.1, 10.0),
                        r.integer("spawn-protection.outline.refresh-interval-seconds", 60, 0, 3600),
                        r.integer("spawn-protection.outline.max-entities", 2048, 16, 20_000)));

        legendaries = new Legendaries(
                r.bool("legendaries.require-full-attack-charge", true),
                r.integer("legendaries.passive-check-interval-ticks", 10, 2, 100),
                r.integer("legendaries.passive-effect-duration-ticks", 60, 20, 600),
                r.bool("legendaries.allow-anvil-repair", true),
                r.bool("legendaries.allow-anvil-enchanted-books", true),
                r.bool("legendaries.allow-rename", false),
                r.bool("legendaries.held-particles", true),
                r.bool("legendaries.announce-crafts", true),
                new Inferno(
                        r.decimal("legendaries.inferno-blade.ignite-chance", 0.25, 0.0, 1.0),
                        r.integer("legendaries.inferno-blade.ignite-seconds", 4, 1, 60),
                        r.bool("legendaries.inferno-blade.fire-resistance-while-held", true)),
                new Frostbite(
                        r.decimal("legendaries.frostbite.slowness-chance", 0.30, 0.0, 1.0),
                        r.integer("legendaries.frostbite.slowness-seconds", 3, 1, 60),
                        r.integer("legendaries.frostbite.slowness-amplifier", 1, 0, 9),
                        r.bool("legendaries.frostbite.speed-while-held", true),
                        r.integer("legendaries.frostbite.speed-amplifier", 0, 0, 4)),
                new VoidReaper(
                        r.decimal("legendaries.void-reaper.weakness-chance", 0.25, 0.0, 1.0),
                        r.integer("legendaries.void-reaper.weakness-seconds", 4, 1, 60),
                        r.integer("legendaries.void-reaper.weakness-amplifier", 0, 0, 9),
                        r.integer("legendaries.void-reaper.kill-strength-seconds", 8, 1, 120),
                        r.integer("legendaries.void-reaper.kill-strength-amplifier", 0, 0, 4),
                        r.enumValue("legendaries.void-reaper.kill-trigger", KillTrigger.class, KillTrigger.PLAYERS)));

        Map<String, List<DropRule>> table = new LinkedHashMap<>();
        ConfigurationSection tableSec = r.section("drops.table");
        if (tableSec != null) {
            for (String itemId : tableSec.getKeys(false)) {
                ConfigurationSection itemSec = tableSec.getConfigurationSection(itemId);
                if (itemSec == null) {
                    r.warn("drops.table." + itemId + " must be a map of ENTITY_TYPE: chance.");
                    continue;
                }
                List<DropRule> rules = new ArrayList<>();
                for (String entityKey : itemSec.getKeys(false)) {
                    EntityType type;
                    try {
                        type = EntityType.valueOf(entityKey.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        r.warn("drops.table." + itemId + "." + entityKey + ": unknown entity type; ignored.");
                        continue;
                    }
                    double chance = r.decimal("drops.table." + itemId + "." + entityKey, -1, 0.0, 1.0);
                    if (chance >= 0) {
                        rules.add(new DropRule(type, chance));
                    }
                }
                table.put(itemId.toLowerCase(Locale.ROOT), List.copyOf(rules));
            }
        }
        drops = new Drops(
                r.bool("drops.enabled", true),
                r.bool("drops.require-player-kill", true),
                r.decimal("drops.looting-bonus-per-level", 0.01, 0.0, 1.0),
                r.bool("drops.boss-drops-to-killer", true),
                Collections.unmodifiableMap(table));

        String shadowHex = r.string("tablist.smp-shadow-color", "#A0A0A0");
        int shadowArgb = 0xFFA0A0A0;
        if (shadowHex.matches("#[0-9A-Fa-f]{6}")) {
            shadowArgb = 0xFF000000 | Integer.parseInt(shadowHex.substring(1), 16);
        } else if (shadowHex.matches("#[0-9A-Fa-f]{8}")) {
            shadowArgb = (int) Long.parseLong(shadowHex.substring(1), 16);
        } else {
            r.warn("tablist.smp-shadow-color must look like #RRGGBB or #AARRGGBB; using #A0A0A0.");
        }
        int pingGood = r.integer("tablist.ping-good-ms", 80, 1, 10_000);
        tab = new TabList(
                r.bool("tablist.enabled", true),
                r.integer("tablist.refresh-interval-ticks", 40, 10, 1200),
                r.bool("tablist.smp-shadow", true),
                shadowArgb,
                r.stringList("tablist.header-extra-lines", List.of()),
                r.string("tablist.footer-format", "<gray>Ping: <ping> <dark_gray>|</dark_gray> Players Online: <white><online>/<max></white></gray>"),
                pingGood,
                Math.max(pingGood, r.integer("tablist.ping-okay-ms", 160, 1, 10_000)),
                r.bool("tablist.disable-on-conflict", true));

        String url = r.string("resource-pack.url", "").trim();
        String sha1 = r.string("resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT);
        boolean packEnabled = r.bool("resource-pack.enabled", false);
        if (packEnabled && (!url.startsWith("https://") && !url.startsWith("http://"))) {
            r.warn("resource-pack.enabled is true but resource-pack.url is not an http(s) URL; the pack will not be sent.");
            packEnabled = false;
        }
        if (packEnabled && !sha1.matches("[0-9a-f]{40}")) {
            r.warn("resource-pack.sha1 must be the 40-character SHA-1 of the ZIP; the pack will not be sent.");
            packEnabled = false;
        }
        resourcePack = new ResourcePack(packEnabled, url, sha1,
                r.bool("resource-pack.required", false),
                r.string("resource-pack.prompt", "<#00FFFF>Guilded SMP</#00FFFF> <gray>uses a custom resource pack for legendary items.</gray>"),
                r.integer("resource-pack.send-delay-ticks", 20, 0, 400),
                r.enumValue("resource-pack.sounds", SoundMode.class, SoundMode.AUTO));

        String maxBan = r.string("moderation.max-tempban", "365d");
        long maxBanSeconds = com.guildedsmp.util.Durations.parse(maxBan).map(java.time.Duration::getSeconds).orElseGet(() -> {
            r.warn("moderation.max-tempban is not a valid duration; using 365d.");
            return 365L * 86_400L;
        });
        moderation = new Moderation(
                lowerSet(r.stringList("moderation.muted-blocked-commands",
                        List.of("msg", "tell", "w", "whisper", "me", "say", "r", "reply", "mail", "teammsg", "tm"))),
                maxBanSeconds);

        warnings = List.copyOf(r.warnings);
    }

    public List<String> warnings() {
        return warnings;
    }

    public static Settings load(ConfigurationSection root, Logger logger) {
        Settings s = new Settings(new Reader(root));
        for (String w : s.warnings) {
            logger.warning("[config] " + w);
        }
        return s;
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> out = new java.util.HashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                String s = v.trim().toLowerCase(Locale.ROOT);
                out.add(s.startsWith("/") ? s.substring(1) : s);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** Type-checked, range-checked reads that collect warnings instead of throwing. */
    static final class Reader {
        private final ConfigurationSection root;
        private final List<String> warnings = new ArrayList<>();

        Reader(ConfigurationSection root) {
            this.root = root;
        }

        void warn(String message) {
            warnings.add(message);
        }

        ConfigurationSection section(String path) {
            return root.getConfigurationSection(path);
        }

        boolean bool(String path, boolean def) {
            Object v = root.get(path);
            if (v == null) {
                return def;
            }
            if (v instanceof Boolean b) {
                return b;
            }
            warn(path + " must be true or false; using " + def + ".");
            return def;
        }

        int integer(String path, int def, int min, int max) {
            Object v = root.get(path);
            if (v == null) {
                return def;
            }
            if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
                warn(path + " must be a whole number; using " + def + ".");
                return def;
            }
            long x = n.longValue();
            if (x < min || x > max) {
                warn(path + " must be between " + min + " and " + max + " (was " + x + "); using " + def + ".");
                return def;
            }
            return (int) x;
        }

        double decimal(String path, double def, double min, double max) {
            Object v = root.get(path);
            if (v == null) {
                return def;
            }
            if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                warn(path + " must be a number; using " + def + ".");
                return def;
            }
            double x = n.doubleValue();
            if (x < min || x > max) {
                warn(path + " must be between " + min + " and " + max + " (was " + x + "); using " + def + ".");
                return def;
            }
            return x;
        }

        String string(String path, String def) {
            Object v = root.get(path);
            if (v == null) {
                return def;
            }
            if (v instanceof String s) {
                return s;
            }
            return String.valueOf(v);
        }

        List<String> stringList(String path, List<String> def) {
            if (!root.isSet(path)) {
                return def;
            }
            if (!root.isList(path)) {
                warn(path + " must be a list; using the default.");
                return def;
            }
            return root.getStringList(path);
        }

        <E extends Enum<E>> E enumValue(String path, Class<E> type, E def) {
            String raw = string(path, def.name());
            try {
                return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warn(path + ": '" + raw + "' is not one of " + java.util.Arrays.toString(type.getEnumConstants()) + "; using " + def + ".");
                return def;
            }
        }
    }
}
