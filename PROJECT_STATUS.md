# Project status

_Last updated: 2026-10-24 · version 1.0.0_

## Summary

Guilded SMP 1.0.0 is feature-complete, compiles with zero warnings, and passes 101 automated
tests (MockBukkit server + static checks). **It has not been started on a real Paper server or
tested with real clients.** That was a deliberate choice: running a server would have meant
accepting the Minecraft EULA, which the owner declined for this build. Everything that needs a
live server or a game client is listed as unverified in [TESTING.md](TESTING.md).

There was no earlier Guilded SMP project on this machine, so this is a new codebase. Features
the original brief described as "existing" were built new and are marked *implemented* below.

## Verified target environment

Checked on 2026-10-24 against PaperMC's own services:

| Item | Value | Source |
|---|---|---|
| Minecraft | **26.2** | `fill.papermc.io` lists 26.2 as `SUPPORTED`; newest 26.2 build 134 is `STABLE` (2026-10-14) |
| Server API | `io.papermc.paper:paper-api:26.2.build.134-stable` | `repo.papermc.io` maven-metadata |
| Java | **25 minimum** | Paper's version metadata (`java.version.minimum: 25`) |
| Plugin `api-version` | `'26.2'` | Paper docs: valid range 1.13 to 26.3 |
| Resource pack format | **88** | minecraft.wiki "Java Edition 26.2" and "Pack format" pages |
| Test framework | MockBukkit `mockbukkit-v26.2:4.117.0` (built on paper-api 26.2.build.111) | Maven Central |
| Local build | JDK 26.0.2.1, Gradle 9.8.2, bytecode targets Java 25 (class version 69) | measured |

Paper 26.3 also exists. The plugin targets 26.2 as requested; it has not been checked against 26.3.

## Architecture

One Paper plugin (`com.guildedsmp`), plain `plugin.yml` commands, no external dependencies.

```
GuildedSMP (main)            wires everything, reload/disable lifecycle
├── config/                  Settings (validated snapshot), ConfigMigrator, Messages (MiniMessage)
├── storage/                 YamlStore (atomic write, corrupt-file quarantine), IoExecutor (1 writer thread)
├── identity/                IdentityRegistry: trusted name -> account UUID pins
├── opprotect/               OperatorProtection
├── combat/                  CombatManager (tags + UI, one shared timer), CombatListener (tags, combat log)
├── teleport/                TeleportService (warm-up, re-checks, safe destination), TpaManager, TpaCommands
├── home/                    HomeManager, HomeCommands
├── spawn/                   SpawnManager, SquareRegion, SpawnProtectionListener, BoundaryOutline, commands
├── items/                   CustomItem (ids), CustomItems (build/identify), ItemProtectionListener
├── legendary/               LegendaryCombatListener (all on-hit/on-kill effects), PassiveEffects, FX
├── recipes/                 LegendaryRecipes (register + validate), RecipeListener, RecipeMenu
├── drops/                   IngredientDrops
├── moderation/              MuteManager, ChatListener, ModerationCommands
├── tab/                     TabListManager
├── pack/                    ResourcePackManager, SoundService
├── rules/                   owner-only crafting rule (see OWNER_NOTES.md)
└── command/                 /guilded
resourcepack/                original pack source (zipped by the build)
tools/                       generators for every texture, sound and pack JSON
```

Key design decisions:
- **Item identity** = persistent-data id + matching base material. Names and lore are never trusted.
- **Skinning** uses `custom_model_data` strings with a vanilla fallback, so players without the
  pack see the vanilla base item instead of a missing-texture cube.
- **Recipes** are normal shaped recipes (they show in the recipe book), and every craft is
  re-validated slot by slot on preview *and* on the click.
- **Ordering:** spawn protection cancels at `LOW`; combat tags and legendary effects run at
  `MONITOR` with `ignoreCancelled`, so a blocked hit can never tag or proc.
- **Teleports:** every plugin teleport goes through one service. Combat is checked at request,
  after the warm-up, and after the chunk loads; a generation counter discards stale async results.
- **Threading:** all game state on the main thread. Chat (async) only reads a concurrent mute
  map and an immutable message map. Disk writes happen on one IO thread, from pre-serialized strings.
- **Data** lives in `data/*.yml`, separate from `config.yml`.

## Feature inventory

| Feature | Status | Notes |
|---|---|---|
| Homes (`/home`, `/sethome`, `/delhome`, `/homes`) | Implemented, unit-tested | Limits, names, safety, unloaded worlds, persistence |
| Teleport requests (`/tpa`, `/tpaccept`, `/tpdeny`, `/tpacancel`) | Implemented, unit-tested | Expiry, cooldown, conflicts, combat cancel, re-validation |
| Mute commands | Implemented, unit-tested | Timed or permanent; chat and message commands |
| Timed bans (`/tempban`, `/unban`) | Implemented, unit-tested | Paper profile ban list |
| Admin and creator permission tiers | Implemented, unit-tested | player < moderator < admin < creator |
| Owner-only crafting restriction | Implemented, unit-tested | Private; see OWNER_NOTES.md |
| Diamond armor crafting/equipment limits | Not implemented, by owner decision | The owner chose "no limit" |
| Operator protection | Implemented, unit-tested | UUID pinning, deop blocking, offline-mode safety |
| Inferno Blade / Frostbite / Void Reaper | Implemented, unit-tested | Effects, held effects, kill trigger, item rules |
| Legendary recipes | Implemented, unit-tested | Anti-spoof validation, crafter block, recipe viewer |
| Six custom ingredients + drops | Implemented, unit-tested | Configurable drop table |
| Spawn (`/spawn`, set, remove, find) | Implemented, unit-tested | |
| Spawn protection (square, PvP, mobs, tagged entry) | Implemented, unit-tested | |
| Red wool boundary (block displays) | Implemented, unit-tested in MockBukkit | **Not seen in a real client** |
| Combat tagging + UI + combat logging | Implemented, unit-tested | Combat-log kill **not verified on a live server** |
| TAB header/footer with exact colours | Implemented, unit-tested | **Not seen in a real client** |
| Original resource pack | Implemented, statically validated | **Not loaded in a real client** |
| Config validation and migration | Implemented, unit-tested | |

## Remaining blockers and risks

1. **No live-server run.** Startup on a real Paper 26.2 server, every in-game interaction and
   client rendering are unverified. Recommended first step: a private test server (see TESTING.md).
2. **Combat-log kill** uses `Player#setHealth(0)` during `PlayerQuitEvent`. This is the common
   approach and passed in MockBukkit, but MockBukkit can't show how real Paper 26.2 handles drops
   for a player who is leaving. Test it with two accounts before relying on it.
3. **Vanilla item definitions** in the pack (`assets/minecraft/items/*.json`) reproduce the
   vanilla fallback models from the documented 26.x format. They have not been compared with the
   actual 26.2 client jar, because downloading it was out of scope.
4. **Resource pack hosting:** there is no hosted URL yet. Someone has to upload the ZIP to a
   public direct-download host before players can receive it automatically.
5. **Proxy detection** for operator protection reads Paper's proxy settings through an API Paper
   marks deprecated-for-removal. If it is removed, the plugin falls back to "offline", which is the safe side.
