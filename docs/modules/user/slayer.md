# Slayer Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `slayer` | **Config folder:** `plugins/Valmora/slayers/`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The Slayer module adds **tiered kill-challenge quests**. A player pays coins to activate a slayer tier, kills a set number of mobs from a target category, and a **boss mob** then spawns at their location. Defeating the boss completes the tier and pays out the configured rewards.

```
Pay coins  →  Kill N mobs  →  Boss spawns  →  Kill boss  →  Rewards
```

Key facts:

- Slayer progress is stored **per profile**, so it survives relogging and server restarts.
- Every slayer has its own **tiers** (Tier 1, Tier 2, …) with escalating cost, kill count, and rewards.
- A player can only have **one active slayer task at a time** — you must finish (or lose) it before starting another.
- The module ships with three example slayers: **Zombie**, **Spider**, and **Wolf**.

> **Heads up for stock installs:** the bundled example slayers reference boss mobs (`zombie`, `spider`, `wolf`) that do **not** ship with the plugin. Until an admin defines those mobs, reaching the kill target logs a warning and **no boss spawns** (the tier cannot be completed). See [Admin Guide](#admin-guide).

---

## Player Guide

### How slayer quests work

1. **Start a task.** A task is started by the `slayer_start` script event — typically triggered by an NPC, a quest, or a GUI button (your server decides). It costs the tier's `cost` in coins, deducted immediately. Example start message:

   ```
   [Slayer] Task started: Zombie Slayer (Tier 1)
   Kill 5 undead mobs to summon the boss.
   ```

2. **Kill mobs.** Every matching mob you kill while the task is active counts. A yellow progress message is shown on each kill:

   ```
   [Slayer] Kill 2/5
   ```

   Only **your own kills** count (the mob must be killed by you), and only mobs matching the tier's `target-category` (e.g. undead, spiders, wolves).

3. **Fight the boss.** Once you reach the required kill count, a boss mob spawns 2 blocks south of you:

   ```
   [Slayer] The Zombie Slayer boss has appeared! Defeat it!
   ```

4. **Claim rewards.** Killing the boss completes the tier:

   ```
   [Slayer] Quest complete! You defeated the Zombie Slayer boss!
   ```

   The configured rewards (coins, items, XP — server-defined) are then granted.

### Rules and restrictions

- **One task at a time.** If you already have an active task, starting another is refused.
- **Kill target, then boss.** Ordinary mob kills stop mattering once the boss has spawned — you must kill that specific boss.
- **Only you can finish it.** The boss is bound to the player who spawned it. Killing a boss spawned by someone else (or a stale boss from a previous task) does not complete your quest.
- **Cost is charged upfront** and is **not refunded** if you abandon the task or fail to finish it.

> **What happens if you die or log out?** Your progress (task, kills, spawned boss) is saved. If the boss entity is gone when you return, there is currently **no way to respawn it or cancel the task** — the tier will stay active until a server admin helps. This is a known limitation.

### Tiers, costs & rewards (default config)

The bundled `slayers/zombie.yml` defines the following tiers:

| Slayer | Tier | Cost | Target | Kills | Boss | Reward (default) |
|---|---|---|---|---|---|---|
| Zombie Slayer | 1 | 100 coins | Undead | 5 | `zombie` | 250 coins |
| Zombie Slayer | 2 | 500 coins | Undead | 15 | `zombie` | 1,000 coins |
| Zombie Slayer | 3 | 2,000 coins | Undead | 30 | `zombie` | 5,000 coins |
| Spider Slayer | 1 | 100 coins | Spiders | 5 | `spider` | 250 coins |
| Spider Slayer | 2 | 500 coins | Spiders | 20 | `spider` | 1,500 coins |
| Wolf Slayer | 1 | 100 coins | Wolves | 5 | `wolf` | 250 coins |
| Wolf Slayer | 2 | 1,000 coins | Wolves | 25 | `wolf` | 3,000 coins |

> Rewards shown are what the bundled config grants via `economy_add`. Your server may change or add rewards (items, XP, titles) freely.

### Commands

There is **no player command** for slayer quests. Tasks are started through the `slayer_start` script event, which your server wires into NPCs, quests, or GUIs. The relevant DSL event is:

```
slayer_start <slayer-id> <tier>
slayer_start zombie_slayer 1
```

---

## Admin Guide

### Setup

1. Drop the plugin into `plugins/`, start the server once. The `slayers/` folder is created automatically and `slayers/zombie.yml` is copied from the jar on first run (`Valmora.java:469-484`).
2. Edit files under `plugins/Valmora/slayers/` — each `.yml` file defines one or more slayers. Server edits are **not** overwritten on later restarts.
3. Create the **boss mobs** your slayers reference in `plugins/Valmora/mobs/` (see the Mobs system in `docs/USER_DOCS.md` §7 or `docs/VALMORA_DOCUMENTATION.md` §24). The bundled examples expect mob ids `zombie`, `spider`, and `wolf`, which do **not** ship with the plugin — define them or repoint `boss-mob`.
4. Wire `slayer_start` into your content (NPC dialogue, quests, GUI clicks) so players can actually start tasks.
5. Reload with `/valmora reload` (permission `valmora.admin`) to apply YAML changes without a restart.

### Permissions

The Slayer module defines **no permissions of its own** and **no commands**. Only the generic `/valmora reload` (`valmora.admin`) applies, and any script events fired by slayer completion respect whatever permissions their parent systems require.

### How to define a slayer

Each **top-level key** in a `slayers/*.yml` file is one slayer. Under it, `tiers` maps tier numbers to tier definitions. A complete example:

```yaml
zombie_slayer:
  name: "Zombie Slayer"
  tiers:
    1:
      cost: 100
      target-category: UNDEAD
      kills-required: 5
      boss-mob: zombie
      completion-events:
        - "economy_add 250"
        - "notify <gold>[Slayer] Zombie Slayer T1 complete! +250 coins io:chat"
    2:
      cost: 500
      target-category: UNDEAD
      kills-required: 15
      boss-mob: zombie
      completion-events:
        - "economy_add 1000"
        - "notify <gold>[Slayer] Zombie Slayer T2 complete! +1000 coins io:chat"
```

### Defining the boss mob

`boss-mob` must be the **id of a Valmora mob** from `mobs/*.yml`. Example boss (put in `mobs/zombie_boss.yml`):

```yaml
zombie:
  category: BOSS
  type: ZOMBIE
  name: "<red>Zombie Slayer Boss"
  level: 10
  health: 2000.0
  damage: 30.0
  speed: 0.28
  stats:
    health: 2000
    damage: 30
  persistent: true
  boss-bar:
    enabled: true
    color: RED
    style: SOLID
```

The boss inherits everything the Mob system supports (stats, resistances, boss bar, abilities). It spawns with a slayer tag automatically (`Keys.SLAYER_BOSS_KEY`).

### Target categories

`target-category` decides which mob kills count. Supported values:

| Category | Counts kills of |
|---|---|
| `MONSTER` | All `Monster` entities (hostile mobs generally) |
| `ILLAGER` | Illager entities (Vindicator, Pillager, etc.) |
| `ANIMAL` | All `Animals` entities (passive mobs) |
| `ALL` / `ANY` | Every killed entity |
| `UNDEAD` | Entity types whose name contains ZOMBIE, SKELETON, PHANTOM, DROWNED, WITHER, STRAY, or HUSK |
| other text | An exact match on the entity's **type name** (e.g. `SPIDER`, `WOLF`) **or** a Valmora mob id stored on the entity (e.g. a custom mob id) |

> ⚠️ `HOSTILE` is the *default* if the field is omitted, but it is **not** handled as a category — a tier without an explicit `target-category` will never count kills. Always set it explicitly.

> Note on matching: `SPIDER` matches mobs whose type is exactly `SPIDER` — **`CAVE_SPIDER` does not count**. `WOLF` matches wolves (including hostile wolves).

### Completion events

`completion-events` is a list of script DSL event strings executed when the boss dies. Anything in the script event system works (see `docs/USER_DOCS.md` §26 for the full list). Common examples:

```yaml
completion-events:
  - "economy_add 250"                              # add coins to purse
  - "economy_remove 100"                           # subtract coins
  - "give DIAMOND:5 notify"                        # give items
  - "notify <gold>[Slayer] Done! io:chat"          # chat message
  - "notify <gold>+500 Slayer XP io:actionbar"     # actionbar message
  - "point slayer_xp add 250"                      # add a point counter
  - "tag add zombie_slayer_t1_done"                # set a profile tag
  - "variable set player.var.zombie_tiers 1"       # set a custom variable
```

Events run with the **killing player** as the caster (e.g. `economy_add` and `notify` target that player).

---

## Configuration Reference

Config folder: `plugins/Valmora/slayers/`. One YAML file may contain multiple slayers.

### `<slayer-id>` (top-level)

| Field | Type | Default | Required | Description |
|---|---|---|---|---|
| `name` | String (MiniMessage) | the slayer id | No | Display name shown in task-started, boss-appeared, and completion messages. |
| `tiers` | Map | — | **Yes** | Map of tier number → tier definition. Keys must be integers. |

### `tiers.<tier>` (tier definition)

| Field | Type | Default | Required | Description |
|---|---|---|---|---|
| `cost` | Double | `0` | No | Coins charged when the tier is started. The player must have this balance. Set to `0` to make the tier free. |
| `target-category` | String | `HOSTILE` | No | Which mob kills count (see table above). **`HOSTILE` is not functional — always set this explicitly.** |
| `kills-required` | Integer | `5` | No | Number of matching kills required before the boss spawns. |
| `boss-mob` | String | `""` | No | Id of a Valmora mob (from `mobs/*.yml`) to spawn as the boss. If blank or unresolvable, **no boss spawns** and the tier can never be completed. |
| `completion-events` | List\<String\> | `[]` | No | Script events fired when the boss is killed (rewards). See [Completion events](#completion-events). |

### Full schema reference

```yaml
<slayer-id>:
  name: "<display name with MiniMessage>"   # optional, defaults to the id
  tiers:
    <tier-number>:                          # required, integer key (1, 2, 3 ...)
      cost: <coins>                         # optional, default 0
      target-category: <CATEGORY>           # optional, default "HOSTILE" (see warning)
      kills-required: <int>                 # optional, default 5
      boss-mob: <mob-id>                    # optional, default "" (empty)
      completion-events:                    # optional, default empty
        - "<script event string>"
```

### Shipped example — `slayers/zombie.yml`

```yaml
zombie_slayer:
  name: "Zombie Slayer"
  tiers:
    1:
      cost: 100
      target-category: UNDEAD
      kills-required: 5
      boss-mob: zombie
      completion-events:
        - "economy_add 250"
        - "notify chat <gold>[Slayer] Zombie Slayer T1 complete! +250 coins"
    2:
      cost: 500
      target-category: UNDEAD
      kills-required: 15
      boss-mob: zombie
      completion-events:
        - "economy_add 1000"
        - "notify chat <gold>[Slayer] Zombie Slayer T2 complete! +1000 coins"
    3:
      cost: 2000
      target-category: UNDEAD
      kills-required: 30
      boss-mob: zombie
      completion-events:
        - "economy_add 5000"
        - "notify chat <gold>[Slayer] Zombie Slayer T3 complete! +5000 coins"
spider_slayer:
  name: "Spider Slayer"
  tiers:
    1:
      cost: 100
      target-category: SPIDER
      kills-required: 5
      boss-mob: spider
      completion-events:
        - "economy_add 250"
        - "notify chat <gold>[Slayer] Spider Slayer T1 complete! +250 coins"
    2:
      cost: 500
      target-category: SPIDER
      kills-required: 20
      boss-mob: spider
      completion-events:
        - "economy_add 1500"
        - "notify chat <gold>[Slayer] Spider Slayer T2 complete! +1500 coins"
wolf_slayer:
  name: "Wolf Slayer"
  tiers:
    1:
      cost: 100
      target-category: WOLF
      kills-required: 5
      boss-mob: wolf
      completion-events:
        - "economy_add 250"
        - "notify chat <gold>[Slayer] Wolf Slayer T1 complete! +250 coins"
    2:
      cost: 1000
      target-category: WOLF
      kills-required: 25
      boss-mob: wolf
      completion-events:
        - "economy_add 3000"
        - "notify chat <gold>[Slayer] Wolf Slayer T2 complete! +3000 coins"
```

> **Recommended edits before going live:** (1) create mob definitions for `zombie`, `spider`, and `wolf` (or repoint `boss-mob`); (2) change `notify chat <gold>...` to the documented `notify <gold>... io:chat` form so the literal word "chat" doesn't appear in messages.
