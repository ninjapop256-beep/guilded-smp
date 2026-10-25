# Owner notes (private)

> For the server owners (FelixSyn, Itzbreadloaf) only. Nothing in here appears in game, in
> config.yml, in plugin.yml or in the README. Don't share this file or the repository.

## Mace rule

- Only **FelixSyn** and **Itzbreadloaf** can craft a mace, and only **one mace can ever be crafted on the server**.
- For everyone else (ops included) the crafting result just stays empty. There is no message,
  no permission node, no config entry, no console log. The mace recipe is also kept out of their
  recipe books, and Crafters can never make one.
- Owners must take the crafted mace with a **normal left/right click and an empty cursor**.
  Shift-clicking is blocked, so only one can be made per craft.
- The two accounts are recognised the same way as for operator protection: by the account UUID
  pinned the first time each name joins on an online-mode server. On an offline-mode server this
  only works if `operator-protection.allow-offline-mode` is `true`.

Owner-only command (it looks like an unknown subcommand to anyone else, and isn't tab-completed for them):

| Command | Effect |
|---|---|
| `/guilded mace` | How many have been crafted, plus the last five crafts (time, name, UUID) |
| `/guilded mace reset` | Reset the counter (e.g. if the mace was lost) |
| `/guilded mace limit <n>` | Change how many may be crafted (0 to 100) |

Stored in `plugins/GuildedSMP/data/craft-limits.yml` (count, limit, allowed names, history).
To change who may craft, edit `mace.allowed` there while the server is stopped. If that file is
unreadable, crafting is locked until it's fixed (it fails closed).
