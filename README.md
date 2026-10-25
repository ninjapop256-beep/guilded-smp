# Guilded SMP

The core plugin for the **Guilded SMP** Minecraft server: homes, teleport requests, spawn and a
protected spawn zone, PvP combat tagging, three legendary swords with their own crafting
ingredients, moderation tools, operator protection, and a branded TAB list. It also ships an
original resource pack with textures, models and sounds made for this project.

| | |
|---|---|
| Minecraft / server | **Paper 26.2** (built against `paper-api 26.2.build.134-stable`) |
| Java | **25 or newer** (Paper 26.2's minimum) |
| Resource pack | format **88** (Java Edition 26.2) |
| Dependencies | none (Paper only) |

> Status: compiled and covered by 101 automated tests. It has **not** been run on a live
> server with real players yet. See [TESTING.md](TESTING.md) for what is and isn't verified.

---

## Installation

1. Use a **Paper 26.2** server on **Java 25+**.
2. Copy `GuildedSMP-1.0.0.jar` into the server's `plugins/` folder.
3. Start the server once. It creates `plugins/GuildedSMP/config.yml` and `plugins/GuildedSMP/data/`.
4. In game, as an operator: stand where spawn should be and run `/spawn set`.
   Spawn protection turns on automatically with radius 100 (change it with `/spawnprotect <radius>`).
5. Optional: set up the resource pack (see [Resource pack](#resource-pack)).

**Updating:** replace the JAR and restart. Your `config.yml` keeps every value you set; new
settings are merged in and a backup `config.yml.bak-<time>` is written first. Files in `data/`
are never touched by updates or config changes.

Avoid `/reload`. Use `/guilded reload` to apply config changes.

---

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/tpa <player>` | Ask to teleport to a player | `guilded.tpa` |
| `/tpaccept [player]` | Accept a request (newest if no name) | `guilded.tpa` |
| `/tpdeny [player]` | Deny a request | `guilded.tpa` |
| `/tpacancel` | Cancel your outgoing request | `guilded.tpa` |
| `/home [name]` | Teleport home (default `home`) | `guilded.home` |
| `/sethome [name]` | Set a home where you stand | `guilded.home` |
| `/delhome <name>` | Delete a home | `guilded.home` |
| `/homes` | List homes (click a name to go there) | `guilded.home` |
| `/spawn` | Teleport to spawn | `guilded.spawn` |
| `/spawn find` | Spawn world, coordinates and your distance | `guilded.spawn.find` |
| `/spawn set` | Set spawn to your position | `guilded.spawn.set` |
| `/spawn remove` | Remove spawn (protection pauses until it is set again) | `guilded.spawn.remove` |
| `/spawnprotect` | Help and current status | `guilded.spawnprotect.view` |
| `/spawnprotect status` | State, radius and exact boundaries | `guilded.spawnprotect.view` |
| `/spawnprotect <radius>` | Protect a square of that radius around spawn | `guilded.spawnprotect.modify` |
| `/spawnprotect remove` | Turn spawn protection off | `guilded.spawnprotect.modify` |
| `/mute <player> [duration\|perm] [reason]` | Mute (e.g. `10m`, `2h`, `7d`, `1d12h`) | `guilded.mute` |
| `/unmute <player>` | Unmute | `guilded.mute` |
| `/tempban <player> <duration> [reason]` | Timed ban (uses the server's ban list) | `guilded.tempban` |
| `/unban <player>` | Lift a ban | `guilded.unban` |
| `/guilded help` | Player command overview | everyone |
| `/guilded recipes [item]` | Read-only recipe viewer | `guilded.recipes` |
| `/guilded combat [status\|clear] [player]` | Your combat status; admins can clear a tag | `guilded.combat.admin` to clear |
| `/guilded reload` | Reload config.yml | `guilded.reload` |
| `/guilded give <player> <item> [amount]` | Give a custom item | `guilded.give` |
| `/guilded items` | List custom item ids | `guilded.give` |
| `/guilded opprotect` | Operator-protection status | `guilded.opprotect.view` |
| `/guilded version` | Plugin and server version | everyone |

`/gsmp` is an alias for `/guilded`. All commands tab-complete players, homes and options.
Durations accept `s`, `m`, `h`, `d`, `w` and combinations such as `1d12h`.

---

## Permissions

Four tiers, each including the one below it:

| Tier | Default | Includes |
|---|---|---|
| `guilded.player` | everyone | tpa, homes, `/spawn`, `/spawn find`, spawn-protection status, recipe viewer |
| `guilded.moderator` | op | player + mute, tempban, unban |
| `guilded.admin` | op | moderator + spawn set/remove, spawn-protection changes, reload, combat admin, no teleport warm-up, no tpa cooldown, moderation-exempt, op-protection status |
| `guilded.creator` | nobody | admin + `guilded.give` (custom items). Given automatically to the protected owner accounts. |

Other nodes:

| Node | Default | Notes |
|---|---|---|
| `guilded.combat.bypass` | nobody | **Emergency override:** ignore combat teleport and spawn-entry limits. Not part of any tier; grant it explicitly if needed. |
| `guilded.homes.unlimited` | nobody | No home limit |
| `guilded.homes.limit.<name>` | nobody | Home limit from `homes.limits.<name>` (defaults: `vip` = 5, `mvp` = 10) |
| `guilded.moderation.exempt` | admins | Moderators can't mute or tempban this player (creators still can) |
| `guilded.tpa.bypasscooldown`, `guilded.teleport.bypasswarmup` | admins | |

Example with LuckPerms:

```
/lp group vip permission set guilded.homes.limit.vip true
/lp group mod permission set guilded.moderator true
/lp user SomeAdmin permission set guilded.combat.bypass true
```

---

## Legendary weapons

All three are Netherite Swords with **Sharpness VI**, stored as a normal enchantment above the
survival limit. Each one is identified by a hidden custom id, not by its name, so a renamed sword
can never act as a legendary. Effects only trigger on a **fully charged** swing, only on targets
the hit was allowed to damage, and **never** inside spawn protection.

| Sword | Extra enchantment | On hit (defaults) | Passive / on kill |
|---|---|---|---|
| **Inferno Blade** | Fire Aspect II | 25% chance: set the target on fire for 4 s | Fire Resistance while held |
| **Frostbite** | none | 30% chance: Slowness II for 3 s | Speed I while held |
| **Void Reaper** | none | 25% chance: Weakness I for 4 s | Strength I for 8 s after a direct melee **player** kill |

Every chance, duration and strength is set under `legendaries:` in config.yml. The held effects
are short effects that refresh while you hold the sword and are removed when you switch away. A
longer or stronger effect from a potion or beacon is never replaced or removed.

Item rules:
- **Anvil:** repair with netherite ingots or a plain netherite sword, and apply enchanted books
  (both configurable). Renaming is off by default (`legendaries.allow-rename`). A legendary
  can never be the *sacrifice* item, so Sharpness VI can't be copied onto a normal sword.
- **Grindstone, smithing table, enchanting table:** custom items are refused.
- **Crafting:** custom items can't be used in any other recipe, including the vanilla repair recipe.
- **Crafters (auto-crafters):** can't craft legendaries or use custom ingredients.

## Recipes

Crafting table (3×3). Mirrored layouts also work. Ingredients must be **genuine**: a renamed
vanilla item is rejected. Open `/guilded recipes` to see each layout in game.

**Inferno Blade**: 1 Netherite Sword, 2 Inferno Core, 2 Blaze Rod, 1 Magma Block, 1 Ember Crystal, 1 Blaze Powder
```
[Inferno Core] [Ember Crystal] [Inferno Core]
[Blaze Rod]    [Netherite Sword] [Blaze Rod]
[Blaze Powder] [Magma Block]   [ empty ]
```
**Frostbite**: 1 Netherite Sword, 2 Frost Core, 2 Packed Ice, 1 Blue Ice, 1 Frozen Crystal, 1 Prismarine Shard
```
[Frost Core] [Frozen Crystal]  [Frost Core]
[Packed Ice] [Netherite Sword] [Packed Ice]
[Blue Ice]   [Prismarine Shard] [ empty ]
```
**Void Reaper**: 1 Netherite Sword, 2 Void Shard, 1 Eye of Ender, 2 Obsidian, 1 Void Heart, 1 Nether Star
```
[Void Shard] [Void Heart]      [Void Shard]
[Obsidian]   [Netherite Sword] [Obsidian]
[Eye of Ender] [Nether Star]   [ empty ]
```
Layouts and ingredients can be changed under `recipes:`. An invalid recipe in config falls back
to the default with a console warning.

## Ingredients and where to get them

Ingredients drop from mobs killed by a player (`drops.require-player-kill`). Each Looting level
adds +1% (`drops.looting-bonus-per-level`). Boss drops go straight into the killer's inventory.

| Ingredient | Default drop chance | Looks like (without the pack) |
|---|---|---|
| Inferno Core | Blaze 5% | Magma Cream (glinting) |
| Ember Crystal | Magma Cube 3%, Ghast 10% | Resin Brick (glinting) |
| Frost Core | Stray 6% | Heart of the Sea (glinting) |
| Frozen Crystal | Stray 2%, Polar Bear 8% | Prismarine Crystals (glinting) |
| Void Shard | Enderman 1.5%, Shulker 8% | Echo Shard (glinting) |
| Void Heart | Ender Dragon 100%, Warden 25% | Popped Chorus Fruit (glinting) |

Change these under `drops.table`. Admins with `guilded.give` can also hand them out with `/guilded give`.

---

## Spawn and spawn protection

- `/spawn set` stores the spawn in `data/spawn.yml`. The first time, protection is switched on
  with `spawn-protection.default-radius` (100).
- The protected area is a **square centred on the spawn block**. Radius `r` covers every block
  column where `|x - spawnX| ≤ r` and `|z - spawnZ| ≤ r`, edges included, so radius 100 is 201×201.
  The centre is always the configured spawn, never the position of the admin running the command.
- `/spawn remove` pauses protection; setting a spawn again resumes it with the same radius.

Inside the zone:
- No PvP damage, fire, knockback, harmful splash/lingering potions, fishing-rod pulls or
  player wind charges, whether the attacker or the victim is the one inside.
- Legendary effects never apply, and blocked hits never create a combat tag.
- Hostile mobs don't spawn naturally, can't target or hurt players, and wanderers that would
  despawn anyway are removed (named, leashed and persistent mobs are left alone).
- Combat-tagged players can't walk, ride, fly or teleport in. Players already inside can always leave.

Only the parts listed above are blocked. Building, mining, containers and farm mobs are not
affected. The vanilla `spawn-protection` in `server.properties` is separate.

**Red wool outline.** The border is drawn with *block display* entities showing red wool. It
**never places or replaces real blocks**: displays have no collision, are never saved to disk,
and are redrawn when chunks load. Things to know:
- Clients only see displays within entity tracking range, so distant edges appear as you approach.
- The line sits on top of the highest block in each column (on the nether roof in the Nether)
  and is re-checked every 60 s (`outline.refresh-interval-seconds`).
- One display covers a run of same-height blocks, so a flat radius-100 border uses about 50
  entities. Hard cap: `outline.max-entities`.

## Combat tagging

- A PvP hit that **actually deals damage** outside spawn tags both players for 20 s
  (`combat.tag-duration-seconds`). Each new hit refreshes the timer. Melee and player-fired
  projectiles count; mobs, fall damage, fire, poison and self-damage don't.
- The countdown `⚔ IN COMBAT — 20s` shows on the action bar (or boss bar, `combat.display`).
- While tagged: no `/spawn`, `/home`, `/tpa`/`/tpaccept`, no command-caused teleports from other
  plugins (`block-command-teleports`), and no listed commands (`combat.blocked-commands`).
  Starting combat cancels any teleport warm-up and teleport request. Checks run again at the
  moment the teleport happens.
- **Combat logging:** disconnecting while tagged kills the player once, using the server's normal
  death rules (items drop once, or are kept with `keepInventory`). Server shutdowns and expired
  tags never count. Which disconnect reasons count is set in `combat.combat-log.penalize-reasons`.
- **Emergency override:** `/guilded combat clear <player>` (admins), or the
  `guilded.combat.bypass` permission.

## Operator protection

`Itzbreadloaf` and `FelixSyn` are kept opped (`operator-protection.protected-players`).
- Op status is restored on startup, on join, right after any command containing `deop`, and every 30 s.
- `/deop <protected name>` is cancelled from players, the console, RCON and command blocks.
- Each name is pinned to the account UUID that first joins with it on a server that verifies
  accounts. After that, only that account counts, even if the name changes. You can also pin
  directly with `Name:uuid`.
- **Offline-mode servers:** anyone can join with any name, so name-only entries are **not**
  auto-opped there (a warning is logged). Use a verified proxy, pin UUIDs, or (not recommended)
  set `allow-offline-mode: true`.
- Protected accounts get the `guilded.creator` tier and can't be muted or temp-banned through this plugin.
- `setOp` is only called when op was actually lost, so there are no write loops or repeated disk writes.

**Limitations:** a plugin can't stop someone with access to the server files (`ops.json`), the
console process, the host machine, or another plugin that edits ops directly. It restores op
status afterwards; it is not tamper-proof.

**To legitimately de-op a protected account:** set `operator-protection.enabled: false`, run
`/guilded reload`, then `/deop`. Remove the name from the list if the change should be permanent.

## Moderation

- `/mute` blocks chat and the commands in `moderation.muted-blocked-commands` (msg, tell, me, ...).
  Mutes are stored in `data/mutes.yml` and expire by themselves.
- `/tempban` uses Paper's own ban list with an expiry, so vanilla `/pardon` and `banned-players.json`
  stay consistent. The longest allowed ban is `moderation.max-tempban` (365 days).

## TAB list

The header reads **GUILDED SMP** with exact colours: `GUILDED` = `#00FFFF`, `SMP` = `#000000`.
They are two separate text components, with no gradient and nothing inherited. Black is hard
to read on the dark TAB background, so by default `SMP` gets a light text **shadow**; the
letters themselves stay `#000000`. Turn this off with `tablist.smp-shadow: false`.

The footer shows each viewer's own ping and the online count, e.g. `Ping: 42 ms | Players Online: 8/50`.
It refreshes every 2 s (`tablist.refresh-interval-ticks`) and after joins and leaves, and is only
re-sent when the text changes.

If another TAB plugin is installed (TAB, CMI, AnimatedTab, ...), Guilded SMP leaves the TAB
list alone and logs a warning (`tablist.disable-on-conflict`).

---

## Resource pack

`GuildedSMP-ResourcePack-1.0.0.zip` contains original textures (3 swords, 6 ingredients, three of
them animated), models, 5 sounds and a pack icon, all made for this project.

How it works: each custom item carries a `custom_model_data` string such as
`guildedsmp:inferno_blade`. The pack overrides the vanilla item definition of the base item
(`assets/minecraft/items/netherite_sword.json` etc.) to pick the custom model for that string,
and **falls back to the vanilla model** for everything else. Without the pack, items look like
their glinting vanilla base, with the custom name and lore, and work exactly the same.

**Single player / testing:** put the ZIP in `.minecraft/resourcepacks/` and enable it.

**Server-wide** (choose one):
1. Upload the ZIP somewhere that serves a **direct, public download**. A private GitHub repo
   won't work, because clients can't log in.
2. Either set it in `server.properties`:
   ```
   resource-pack=<direct download URL>
   resource-pack-sha1=<contents of GuildedSMP-ResourcePack-1.0.0.zip.sha1>
   require-resource-pack=false
   ```
   or in `config.yml`:
   ```yaml
   resource-pack:
     enabled: true
     url: "<direct download URL>"
     sha1: "<the 40-character SHA-1>"
     required: false
   ```
   Use only one of the two. The SHA-1 is written next to the ZIP by the build. Re-check it
   whenever the pack changes.

Sounds: players who loaded the pack hear the custom sounds; everyone else hears a fitting vanilla
sound (`resource-pack.sounds: AUTO`).

The pack overrides 7 vanilla item definitions (netherite sword, magma cream, resin brick, heart of
the sea, prismarine crystals, echo shard, popped chorus fruit). Another pack that overrides the
same files will conflict; put Guilded SMP higher in the list.

---

## Configuration

Everything lives in `plugins/GuildedSMP/config.yml`, and each setting is commented there.
Main sections: `operator-protection`, `combat`, `teleport`, `tpa`, `homes`, `spawn`,
`spawn-protection`, `legendaries`, `recipes`, `drops`, `tablist`, `resource-pack`,
`moderation`, `messages`.

- Invalid values (wrong type or out of range) are reported in the console and replaced by safe defaults.
- All player-facing text is under `messages:` (MiniMessage format).

Data files (`plugins/GuildedSMP/data/`):

| File | Contents |
|---|---|
| `homes.yml` | Homes per player UUID |
| `spawn.yml` | Spawn location and protection state |
| `mutes.yml` | Active mutes |
| `identities.yml` | Name → account UUID pins for protected accounts |

Writes go to a temp file that is then moved over the original, so a crash mid-write can't corrupt
data. A file that can't be read is renamed to `*.corrupt-<time>` and kept. While that happens,
the plugin won't overwrite your data.

---

## Building from source

Requires a JDK 25+ (JDK 26 works). Gradle is included.

```bash
./gradlew build dist
```

Outputs in `dist/`: `GuildedSMP-1.0.0.jar`, `GuildedSMP-ResourcePack-1.0.0.zip` and its `.sha1`.
The tests (`./gradlew test`) run the real plugin on a MockBukkit server.

The art and sounds are generated by the scripts in `tools/` (Python 3 with Pillow, numpy and
soundfile). Their output is already committed; re-run them only to change the assets:

```bash
python tools/generate_textures.py
python tools/generate_sounds.py
python tools/generate_pack_json.py
```

## Project documents

[PROJECT_STATUS.md](PROJECT_STATUS.md) · [TASKS.md](TASKS.md) · [DEVLOG.md](DEVLOG.md) · [TESTING.md](TESTING.md)
