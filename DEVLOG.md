# Dev log

## 2026-10-24: 1.0.0

**Discovery.** No earlier Guilded SMP project existed on this machine, so the project was started
fresh in `Documents/GuildedSMP`. The owner decided: no local test server (the Minecraft EULA was
not accepted for this build); the restricted crafting rule from OWNER_NOTES.md; no diamond armor limit.

**Environment.** Checked PaperMC's API: 26.2 is supported, latest stable build 134, Java 25
minimum. Matching `paper-api 26.2.build.134-stable` is on repo.papermc.io. Resource pack format
for 26.2 is 88 (minecraft.wiki). Local tools: JDK 26.0.2.1, JDK 21, git, gh, Python 3.12 with
Pillow/numpy/soundfile, ffmpeg. Gradle 9.8.2 was downloaded and its checksum verified
(sha256 `21bd5da2…9607`) to generate the wrapper.

**API notes found while building.**
- Paper 26.2 ships Adventure 5.2.0. `World#getPVP`, `GameRule.PVP` and Bukkit's
  `EntityKnockbackByEntityEvent` are deprecated for removal; the code uses `GameRules.PVP` and
  Paper's `EntityPushedByEntityAttackEvent`.
- Vanilla resets attack charge before damage is dealt, so the charge is captured in
  `PrePlayerAttackEntityEvent`.

**Implementation.** All systems listed in PROJECT_STATUS.md. Highlights:
- One teleport service with three-point combat re-checks and stale-result protection.
- Spawn protection at LOW priority; tags/effects at MONITOR + ignoreCancelled.
- Outline from block displays, one per same-height run per chunk, never persistent, with
  stale-marker cleanup on chunk load.
- Owner-only crafting rule kept out of all player/admin-facing text; its package was renamed to
  something neutral after noticing the original name was visible in the JAR listing.

**Assets.** Pixel art drawn as hand-placed 16×16 grids (plus procedural orbs) in
`tools/generate_textures.py`. Reviewed through zoomed previews; the Void Heart, Ember Crystal and
Void Shard were redrawn, and the Void Reaper got a scythe hook so the three swords read differently.
Sounds are synthesized from sine waves and filtered noise (`tools/generate_sounds.py`); levels
were checked: peaks 0.73 to 0.97, no clipping.

**Testing.** MockBukkit needed three test-only workarounds on JDK 26:
`--sun-misc-unsafe-memory-access=allow`, `--add-opens java.base/java.lang`, and
`-Dnet.bytebuddy.safe=false`. ByteBuddy's own error message says to set it to `true`, but its
code checks for `false`. The main class also can't be `final`, because MockBukkit subclasses it.

Some MockBukkit gaps were filled in the tests only (`isStopping`, `getBanList(BanListType)`,
Adventure TAB header capture, `getHolder(false)`, 9-slot workbench matrix). Two production
changes came out of testing, both correct on real servers:
- Teleport directly when the destination chunk is already loaded; only load it asynchronously when needed.
- Reading Looting from an empty hand returns 0 instead of throwing.

A review pass then caught a bug: the spawn mob sweep skipped mobs where `Entity#isPersistent()`
was true. That flag means "saved to disk" and is true for almost every mob, so nothing would ever
have been removed. It now uses only vanilla's despawn flag (`getRemoveWhenFarAway`), and a test
covers the rule. The sweep also walks only loaded chunks now, so a large radius costs no more than
a small one, and the combat countdown no longer re-renders its text when the number hasn't changed.

Result: 101 tests in 19 classes, 0 failures, 0 skipped. Clean build has 0 compiler warnings with `-Xlint:all`.

**Packaging.** `dist/GuildedSMP-1.0.0.jar` (92 classes, Java 25 bytecode) and
`dist/GuildedSMP-ResourcePack-1.0.0.zip` (deterministic; SHA-1 in the `.sha1` file).
