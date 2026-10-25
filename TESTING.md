# Test report: Guilded SMP 1.0.0

_Run on 2026-10-24 · Windows 11 · JDK 26.0.2.1 · Gradle 9.8.2 · MockBukkit v26.2 4.117.0_

## Result

| Check | Result |
|---|---|
| Compile (`-Xlint:all`, release 25) | **Pass**, 0 warnings |
| Automated tests (`./gradlew test`) | **101 passed, 0 failed, 0 skipped** (19 classes) |
| `plugin.yml`: api-version `26.2`, all 15 commands bound to executors and tab-completers | **Pass** (tested) |
| `config.yml` loads with no validation warnings | **Pass** (tested) |
| Resource pack JSON, references, PNGs, OGGs, pack format 88 | **Pass** (tested) |
| JAR contents (92 classes + plugin.yml + config.yml, class version 69) | **Pass** (inspected) |
| ZIP contents (pack.mcmeta at root, all assets) and SHA-1 matches file | **Pass** (inspected) |

**What "tested" means here:** the real plugin JAR classes run on a *mock* server (MockBukkit)
with fake players, a fake world and the real Bukkit event pipeline. That checks the plugin's
logic, but not Paper's own behaviour, networking, or what a game client shows.

## Executed automated tests

| Area | Tests |
|---|---|
| Plugin load | enables with no config warnings; every command has executor + completer; all 3 recipes registered |
| Config & descriptor | api-version 26.2; creator/give/combat-bypass default to nobody; bypass is not in the admin tier; migration adds keys, keeps values, keeps the owner's drop table, writes a backup; invalid values fall back with 5 warnings; corrupt data file is quarantined, never overwritten; atomic write leaves no temp file |
| Square region | radius 100 includes all 4 edges and corners, excludes 101; off-centre and negative spawn; other worlds; outline covers each edge column exactly once (8r columns) |
| Spawn & protection | first `/spawn set` enables radius 100; radius is centred on spawn, not the admin; non-admins can't change it but can see status; remove/restore with protection paused then resumed; PvP blocked when either player is inside, with no tags from blocked hits; natural hostile spawns blocked inside only; the mob sweep only removes despawnable, unnamed, unleashed, non-boss hostiles; tagged players can't walk or pearl in but can walk out; outline uses marked, non-persistent red-wool displays, leaves world blocks untouched, and is removed on `/spawnprotect remove`; spawn persists to disk |
| Combat | melee and arrow tag both players; mobs, zero damage, self damage and creative don't tag; combat-log kills a tagged player; untagged or cleared players aren't punished; server shutdown never counts; `/home`, `/spawn`, `/tpa` and blocked commands refused while tagged; entering combat cancels a teleport warm-up |
| Combat timer (fake clock) | countdown text `⚔ IN COMBAT — 20s` → `13s`; refresh doesn't re-fire start; expiry clears everything; exactly one boss bar, removed on expiry |
| Teleport requests | accept teleports after warm-up; deny; cancel; expiry; self/duplicate/conflict/cooldown; combat cancels requests; target entering combat during warm-up aborts the teleport; requester leaving drops the request |
| Homes | set + teleport; limit 3; overwrite allowed at the limit; invalid names; delete; missing home; unsafe spot refused; unloaded world; home that became unsafe is refused; homes survive a restart |
| Operator protection | owners opped, UUID-pinned, given creator; lost op restored and nobody else opped; `/deop` of owners blocked (console, namespaced, any case) and others allowed; impostor with another UUID not protected; offline mode doesn't op by name; can be disabled; owners can't be muted or banned |
| Custom items | Sharpness VI (+ Fire Aspect II for Inferno), custom model strings; unique instance ids; ingredients identify and stack; renamed vanilla items with copied lore aren't custom; custom id on the wrong material rejected; menu copies aren't real items |
| Recipes | default counts exactly match the brief; genuine and mirrored grids match; renamed vanilla item rejected; wrong custom item rejected; legendary or tagged item in a vanilla slot rejected; missing/extra items rejected; stacks need only one per slot; config can't use a legendary as an ingredient; invalid shape falls back to default |
| Item protection (real events) | anvil: no rename, repair allowed, odd materials refused, legendary can't be the sacrifice, ingredients refused, vanilla anvil untouched; grindstone refuses custom items; custom ingredients can't be used in vanilla recipes; legendary result only from genuine ingredients |
| Owner-only rule (OWNER_NOTES.md) | only owners may craft, only once, only by a plain click; silent for everyone else; hidden from tab-complete; kept out of recipe books; absent from plugin.yml, config.yml and README |
| Legendary effects | Frostbite Slowness II 3 s, Void Reaper Weakness, Inferno fire ≥ 4 s; uncharged swings and failed rolls do nothing; a renamed normal sword has no powers; effects never reach players in spawn; Void Reaper Strength only on direct player kills (not environmental, not mobs by default); held effects applied and removed, an 8-minute potion is left alone; renamed swords give no held effects |
| Drops | Blaze drops a genuine Inferno Core on a lucky player kill; nothing without a player kill, on a failed roll, or for mobs without a table entry |
| Moderation | timed mute blocks message commands, unmute works; regular players can't moderate; tempban + unban; bad and too-long durations rejected |
| TAB | `GUILDED` renders exactly #00FFFF and `SMP` exactly #000000, with and without shadow, and no inherited colour; header/footer sent with real ping and `2/max`; updates to `1/max` after a leave; not re-sent when nothing changed; unknown ping shows `?` |
| Resource pack (static) | all JSON parses; pack.mcmeta format 88 with brand colours; every custom item goes base item definition → model → 16×N texture (animations have .mcmeta) with vanilla fallback; every sound cue exists as mono Ogg Vorbis with a subtitle; no orphan textures |
| Durations | single units, combinations, case, invalid input, formatting |

## Not verified (needs a real server and/or client)

These were **not** done, because no real Minecraft server was started (the EULA was not
accepted for this build) and no game client was available:

1. Plugin startup on a real Paper 26.2 server, and the console output during it.
2. How real Paper handles `setHealth(0)` during quit: drop count, and the respawn screen on rejoin.
3. Anything visual or audible in a 26.2 client: textures, models, animations, the red wool
   outline (including tracking distance), action bar and boss bar, TAB colours and shadow, sounds.
4. Resource pack download prompt, SHA-1 check by the client, and the vanilla fallback models.
5. Real multiplayer behaviour: latency, ping values, chunk loading during teleports, other plugins.
6. Behaviour behind a proxy (Velocity/BungeeCord) for operator protection.
7. Performance under load (many players, large spawn radius).

## Manual test plan (do this on a private test server first)

Use two accounts (A, B) and one owner account.

1. Start Paper 26.2 with the JAR; console shows `Guilded SMP 1.0.0 enabled.` with no errors.
2. Owner joins → is op. `/guilded opprotect` shows the pin. Console `deop <owner>` → refused.
3. `/spawn set` → red wool line visible at ±100 from spawn; no real blocks changed (break one: it's not there).
4. A hits B inside spawn → no damage. Outside → both see `⚔ IN COMBAT — 20s` counting down.
5. While tagged: `/spawn`, `/home`, `/tpa` refused; walking into spawn bounces you back.
6. A logs out while tagged → A dies once; items drop once (or are kept with keepInventory).
7. `/guilded give <you> inferno_core 2` etc. → craft each legendary; check the recipe viewer;
   try a renamed vanilla item instead of a core (no result).
8. Hold each sword: held effect appears and disappears; hits proc effects outside spawn only.
9. Enable the resource pack → textures, animations and sounds show; without it the items look
   like glinting vanilla items.
10. TAB list: GUILDED is cyan, SMP is black (with a light shadow); ping and player count are live.
11. `/sethome`, `/home`, `/tpa` flow with warm-up and cancel-on-move; restart → homes and spawn kept.
12. `/mute`, `/tempban 1m`, `/unban` on a test account.

## Re-running

```bash
./gradlew test          # tests only
./gradlew build dist    # tests + JAR + resource pack ZIP + SHA-1 into dist/
```
HTML report: `build/reports/tests/test/index.html`.
