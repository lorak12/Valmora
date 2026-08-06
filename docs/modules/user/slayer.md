# Slayer — User Documentation

> There is no dedicated slayer module or command. Slayer content is built entirely from the quest
> and GUI systems — see `docs/modules/design/slayer.md` for the implementation.

## Playing a Slayer Chain

1. Open the slayer menu — bound to whatever GUI-open trigger your server sets up (the shipped
   `guis/slayers.yml` has no default command/item binding; an admin wires one in, e.g. via an NPC
   or a hotbar item).
2. Click an **available** tier button. If you have enough coins, it's charged immediately and the
   quest starts.
3. Kill the required number of the listed mob category — progress shows live in the button's lore.
4. Once the kill count is met, the tier's **boss** spawns automatically. Defeat it to complete the
   tier and collect the coin reward.
5. Completed tiers show as **available** again — clicking re-charges the cost and restarts the
   tier from zero (there's no separate "replay" flow; starting again is the same click).

## Content Authoring

A slayer chain is:

1. A **quest** per tier, in a quest package (see `docs/modules/user/quest.md`), with a `KILL`
   objective for the trash-mob phase (`events: "spawn_mob <boss-id>"` on completion) followed by a
   second `KILL` objective targeting the boss's exact mob id.
2. A **boss mob** definition per tier in `plugins/Valmora/mobs/*.yml` (see
   `docs/modules/user/mob.md`) — `category: BOSS`, stats scaled for the tier, optional `boss-bar:`.
3. A **button** in a GUI (see `docs/modules/user/gui.md`) with `available`/`in_progress` states
   that charge coins and call `quest_cancel <id>` + `quest_start <id>` to (re)start the tier, and
   read `$quest.<id>.objective.<name>.progress$` to show live progress.

The shipped `plugins/Valmora/quests/slayers/` package (Zombie/Spider/Wolf, 2-3 tiers each) and
`guis/slayers.yml` are the reference example — copy and adapt them for a new slayer line rather
than starting from scratch.

There is currently no separate "Slayer XP" or leveling track — rewards are plain coins via the
quest's own reward events. Add a stat/skill-point reward event to a tier's completion list if you
want progression beyond coins.
