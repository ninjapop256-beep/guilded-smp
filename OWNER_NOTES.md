# Owner notes (private)

> For FelixSyn and Itzbreadloaf only. Don't share this file or the repository.

## Secret operator status

FelixSyn and Itzbreadloaf are kept opped, but nothing in game or in the README says so.
- `/deop FelixSyn` (or Itzbreadloaf), from a player, the console, RCON or a command block, prints
  the normal vanilla reply `Made FelixSyn no longer a server operator`. Nothing actually changes.
- Op is quietly restored on startup, on join, right after any command containing `deop`, and every
  30 s. None of this is logged.
- You also get the `guilded.creator` tier, and moderators get `can't be moderated by you` if they try
  `/mute` or `/tempban` on you.
- `/guilded opprotect` (only works for you two; everyone else sees "Unknown subcommand") shows the status.
- Names are pinned to your account UUIDs the first time you join on an online-mode server.
  On an offline-mode server it only works with `operator-protection.allow-offline-mode: true`.
- **Limits:** anyone with access to `ops.json`, the server console process or the files can still
  see or change ops directly; the plugin just puts op back afterwards. The names are also visible
  in `config.yml` under `operator-protection` to anyone who can read the plugin's files.
- To really de-op one of you: set `operator-protection.enabled: false`, `/guilded reload`, then `/deop`.

## Mace limit

- **Anyone** can craft a mace, but only **one** can ever be crafted on the server. After that the
  crafting result simply stays empty for everyone (no message). Crafters (auto-crafters) can never
  make one, and it must be taken with a normal click (no shift-click).
- Owner-only commands (others see "Unknown subcommand"):

| Command | Effect |
|---|---|
| `/guilded mace` | How many have been crafted, and the last five crafts (time, name, UUID) |
| `/guilded mace reset` | Reset the counter (e.g. if the mace was lost) |
| `/guilded mace limit <n>` | Change how many may be crafted (0-100) |

Stored in `plugins/GuildedSMP/data/craft-limits.yml`.
