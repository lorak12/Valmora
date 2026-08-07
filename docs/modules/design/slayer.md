# Slayer — Design & Code

> **Version:** 0.2 | **API:** Paper 1.21.x | **Java:** 21
> **Status:** `SlayerModule` (dedicated Java module: `SlayerDefinition`, `SlayerListener`,
> `SlayerTier`, `SlayerStartEventFactory`, the `slayer_start` script event, `slayers/*.yml`) has
> been **removed**. Slayer content is now implemented entirely with existing primitives — quest
> packages, GUI, and mob definitions — with **no dedicated module, no `ValmoraAPI` accessor, and no
> `slayer_start` script event.**

---

## 1. Why This Changed

Every piece a bespoke slayer module used to provide already existed generically elsewhere:
multi-stage progress tracking → the quest module's objectives; boss spawning → mob definitions;
menu/coin-gating UI → the GUI module's `DISPLAY` component `states:`/`actions:` system. Rebuilding
slayers as pure data over those systems removes an entire module's worth of Java (listener, tier
matching, category resolution) with zero loss of player-facing functionality.

## 2. How a Slayer Chain Works

Each tier is an ordinary **quest** with two sequential objectives:

```yaml
zombie_slayer_t1:
  name: "<dark_purple>Zombie Slayer I"
  objectives:
    kill:
      type: KILL
      target: UNDEAD          # matches by MobCategory, same as any KILL objective
      amount: 5
      notify: 1
      events: "spawn_mob zombie_slayer_boss_1"   # fires once, on this objective's completion
    boss:
      type: KILL
      target: zombie_slayer_boss_1                # a specific custom mob id
      amount: 1
      events: "zombie_t1_reward"                   # economy_add + notify, defined under `events:`
```

1. **`kill`** — kill N mobs of a category (reuses `MobCategory`/`KILL` objective matching, see
   `docs/modules/design/quest.md` and `docs/modules/design/mob.md`). On completion it spawns the
   tier's boss via the ordinary `spawn_mob` script event — no slayer-specific spawn logic.
2. **`boss`** — kill that exact custom mob id. On completion it fires the tier's reward event list
   (coins, notifications) — plain quest objective rewards, nothing slayer-specific.

Boss mobs are ordinary `MobDefinition`s (`src/main/resources/mobs/slayer_bosses.yml`) with
`category: BOSS`, scaled `stats:`, and a `boss-bar:` block — see `docs/modules/design/mob.md`.

Shipped chains: Zombie (3 tiers), Spider (2 tiers), Wolf (2 tiers) —
`src/main/resources/quests/slayers/quests.yml`.

## 3. Starting/Repeating a Tier — the GUI

There is no `slayer_start` event and no dedicated start command. `src/main/resources/guis/
slayers.yml` is the only entry point: each tier button is a `DISPLAY` component with two `states:`

- **`available`** (`condition: "default"`, i.e. the fallback state) — shows cost/reward, and its
  `actions.left` gate on `$economy.purse$ >= <cost>$`, then `economy_remove <cost>` followed by
  `quest_cancel <id>` + `quest_start <id>` — this cancel-then-start pair is what makes re-running a
  completed tier safe and idempotent (cancel resets status without touching objective progress
  numbers; start re-zeroes every objective fresh).
- **`in_progress`** (`condition: "$quest.<id>.status$ == in_progress"`) — shows live
  `$quest.<id>.objective.<name>.progress$/…required$` values, no click action.

Coin costs are declared once per GUI, in `guis/slayers.yml`'s `on-open:` block (added 2026-08-07):
`variable set prop.cost_<n> <amount>` for each of the 7 tier buttons. Every place that previously
hardcoded the literal number — the `Cost:` lore line, the `$economy.purse$ >=` gate, the
`economy_remove` amount, and the "Not enough coins" fail message — now references `$prop.cost_<n>$`
instead, so a tier's price is a single edit. Reward amounts (coins on boss kill) are separate —
they still live per-tier in `quests/slayers/quest.yml`'s `events:` block (e.g. `zombie_t1_reward`),
since they're quest rewards, not GUI-gated costs, and are already only defined in one place each.

## 4. Known Gaps

*(2026-08-07: progress-indicator gap resolved — see below. Cost-table gap resolved, see §3.)*

- **Player-facing progress indicator outside the GUI already exists for the boss phase**: every
  boss `MobDefinition` in `slayer_bosses.yml` ships `boss-bar: enabled: true`, driven by
  `BossController` (`module/mob/BossController.java`) — a live, health-synced Adventure `BossBar`
  shown to all players within `range` blocks, refreshed every tick alongside `ON_TIMER`/`ON_HEALTH`
  abilities. This is real-time and event-pushed, not GUI-poll-driven, and was already shipped
  before this doc's gap note was written (docs drift). It only covers the `boss` objective, not the
  `kill` (trash-mob) phase.
- **The `kill` phase's progress indicator is the objective's own `notify: 1`**, which — per
  `quests/slayers/notifications.yml` overriding the `info` category to `io: actionbar` — sends an
  action-bar "target (current/required)" ping on every qualifying kill. Combined with the boss-bar
  above, both phases of a slayer tier already have a real-time indicator outside the GUI; no new
  code was needed.

## 5. Recipe: Adding a New Slayer Line

No generator exists — a new line/tier is three edits, all in existing files, following the exact
shape of the shipped Zombie/Spider/Wolf chains:

1. **Boss mob(s)** — add one `MobDefinition` entry per tier to `mobs/slayer_bosses.yml`:
   `category: BOSS`, scaled `stats:`, `glowing: true`, and a `boss-bar:` block (`enabled: true`,
   pick a `color`/`style`/`range`) so the tier gets a progress bar for free. Optional: `abilities:`
   for boss mechanics (see `docs/modules/design/mob.md`).
2. **Quest(s)** — add one two-objective quest per tier to `quests/slayers/quests.yml` (see §2 for
   the shape): a `kill` objective (`type: KILL`, `target: <MobCategory>`, `amount`, `notify: 1`,
   `events: "spawn_mob <boss_id>"`) followed by a `boss` objective (`type: KILL`,
   `target: <boss_id>`, `amount: 1`, `events: "<reward_event_name>"`). Add the reward event itself
   under `quest.yml`'s `events:` block (`economy_add <n>` + `notify ... category:slayer_complete`).
3. **GUI button** — add a new component/state pair to `guis/slayers.yml`'s layout: an
   `available`/`in_progress` `DISPLAY` pair (copy an existing tier button, retarget the quest IDs),
   plus one `variable set prop.cost_<n> <amount>` line in the shared `on-open:` block (§3) and
   `$prop.cost_<n>$` references in the new button's lore/condition/action/fail-action.

That's the whole recipe — no Java changes, no new module. `/valmora reload` picks up all three
files.
