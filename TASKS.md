# Tasks

Legend: `[x]` done · `[ ]` open · `[-]` not doing (with reason)

## Setup
- [x] Verify Minecraft 26.2 / Paper API / Java version availability (Paper 26.2 build 134, Java 25)
- [x] New Gradle project (wrapper 9.8.2), Java 25 bytecode, zero-warning build
- [x] MockBukkit 26.2 test harness (JDK 26 needs extra test-JVM flags; see build.gradle.kts)
- [x] CI workflow (GitHub Actions, JDK 25)

## Systems
- [x] Central config with validation, defaults, migration and backups
- [x] Safe data storage (atomic writes, corrupt-file quarantine, single IO thread)
- [x] Permission tiers: player, moderator, admin, creator
- [x] Operator protection (Itzbreadloaf, FelixSyn) with UUID pinning and deop blocking
- [x] Homes with limits, validation, safety checks, persistence
- [x] TPA with expiry, cooldowns, conflict handling, combat cancellation
- [x] Spawn set / remove / find / teleport
- [x] Square spawn protection centred on spawn
- [x] Non-destructive red wool outline (block displays)
- [x] Combat tagging, countdown UI, teleport restrictions, combat logging
- [x] Three legendary swords with effects, held effects and kill trigger
- [x] Legendary recipes with genuine-ingredient validation and recipe viewer
- [x] Six custom ingredients with configurable mob drops
- [x] Item protection: anvil, grindstone, smithing, enchanting, brewing, crafting, crafters
- [x] Mute and timed-ban commands
- [x] TAB header (exact #00FFFF / #000000) and live ping/player footer
- [x] Owner-only crafting rule (see OWNER_NOTES.md)
- [-] Diamond armor limits: the owner chose "no limit"

## Resource pack
- [x] Original textures for 3 swords and 6 ingredients (3 animated) + pack icon
- [x] Models, item definitions with vanilla fallback, pack.mcmeta (format 88)
- [x] 5 original synthesized sounds (mono Ogg Vorbis) + subtitles
- [x] Static validation tests and a deterministic ZIP + SHA-1 in the build
- [ ] Host the ZIP on a public direct-download URL and fill in `resource-pack.url` / `sha1`

## Verification
- [x] 101 automated tests passing
- [ ] Start the JAR on a real Paper 26.2 server and check the console for errors
- [ ] In-game checklist in TESTING.md ("Manual test plan") with at least two accounts
- [ ] Look at every texture, sound, the outline and the TAB colours in a real 26.2 client
- [ ] Confirm combat-log death drops exactly once on real Paper
