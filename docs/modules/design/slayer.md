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

Coin costs/rewards are hardcoded per-button in `guis/slayers.yml` (100/250 for tier 1, scaling up)
— there's no shared "slayer cost table," each tier's numbers live directly in its own GUI button
and its own quest reward events.

## 4. Known Gaps

- **No player-facing progress indicator outside the GUI** — unlike the old module (which had
  `MobCategory`-driven listener hooks for things like boss-bar-on-hit), everything here is
  GUI-poll-driven (`update-interval` re-render) rather than event-pushed.
- Reusing this pattern for a new slayer line means copying the two-objective quest shape, adding a
  boss `MobDefinition`, and adding a GUI button by hand — there is no generator/template, and no
  admin-facing walkthrough beyond `docs/modules/user/slayer.md`.
