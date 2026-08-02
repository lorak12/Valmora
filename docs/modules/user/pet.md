# Pet Module — User Documentation

> **Version:** 0.1 | **Server:** Paper 1.21.x | **Java:** 21
> **Module ID:** `pets` | **Interaction:** right-click a pet item | **No command**

---

## Overview

The Pet module lets you carry **companion pets** that follow you — well, *stand beside you* —
grant **passive stat bonuses**, **gain experience**, and **react to combat**. Everything is
driven by **pet items**: an item tagged as a pet (stored in your inventory) is summoned by
right-clicking it.

**How it works, in short:**

- Pets are **items**. A pet item carries hidden data identifying which pet it is, its level,
  and its XP — so your progress lives on the item and survives restarts.
- **Right-click** a pet item in your hand to **summon** it next to you; right-click the same
  item again to **unsummon** it.
- While summoned, the pet **adds stats to you** (from the pet's `base-stats` plus
  `stats-per-level × level`).
- Pets gain XP from **kills** and from **skill XP**, and **level up** (max level 200). On
  level-up you get a chat message; at special milestone levels (e.g. 25, 50, 100) the pet
  grants bonus rewards.
- Pets fire **abilities** when you kill mobs, when you attack, and when you take damage —
  each pet lists which triggers it reacts to.

**Honest limitation:** the pet does **not** move or follow you yet. It is summoned 1 block to
your east and stands there. If you walk away, it stays put. See the design doc's
[Unfinished Things / TODOs](../design/pet.md#unfinished-things--todos) — this is a known
missing feature, not a config option.

---

## Player Guide

### Obtaining a pet

- Pets come as **items**. The module itself does not (yet) hand any out — there is no
  `/pet` command and no item that grants one automatically.
- A pet item is any item that has been tagged with the internal pet id (`valmora:pet_id`). If
  the server hands you a pet (or you get one from a future update, a shop, or a collection
  reward such as "Zombie Pet unlocked"), it works by right-click.
- The three pets shipped with the default config are the **Baby Wolf**, the **Baby Sheep**,
  and the **Ender Dragon**. Admins can define more (see [Admin Guide](#admin-guide)).

### Summoning & unsummoning

1. Hold a pet item in your **main hand** (hotbar).
2. **Right-click** (air or block) → the pet is summoned 1 block east of you, with a
   `<gold>` name tag above it, and you see:
   `You summoned your Baby Wolf (Lvl 1)`.
3. Right-click the **same item again** → the pet is despawned and you see `Pet unsummoned.`

Rules to know:

- **One pet at a time.** If you already have a pet summoned and right-click a *different* pet
  item, you get: `You already have a pet active. Unsummon it first.`
- Only the **main hand** works; offhand clicks do nothing.
- The pet is tied to the **inventory slot** it was summoned from — moving the item while
  summoned may confuse the binding.
- **Logging out despawns your pet.** You must re-summon after rejoining. (One quirk: if you
  quit with a pet active, the game may think a pet is still "active" for your next click —
  right-click the same pet item once to clear it, then again to summon.)

### Stat bonuses

While a pet is active, it adds stats to your character **for as long as it is summoned**.
The bonus is recalculated automatically whenever your stats are recomputed.

Each pet grants a **flat base** plus a **per-level bonus**. Examples at level 1 vs. level 100:

| Pet | Stat | Lvl 1 | Lvl 100 |
| --- | --- | --- | --- |
| Baby Wolf | Strength | 5.5 | 55 |
| Baby Wolf | Crit Chance | 2.1 | 12 |
| Baby Sheep | Defense | 8.4 | 48 |
| Baby Sheep | Health | 15.8 | 95 |
| Ender Dragon | Strength | 52 | 250 |
| Ender Dragon | Crit Damage | 31 | 130 |
| Ender Dragon | Damage | 21 | 120 |

These come from the pet definitions; see the full table in
[Configuration Reference](#configuration-reference). Note: stat values shown assume the pet
level listed — the bonus is `base + perLevel × level`.

### XP, leveling, and milestones

Pets earn XP from two sources:

| Source | Pet XP earned |
| --- | --- |
| You kill any mob | `10` XP (flat, per kill) |
| You earn skill XP | `10%` of the skill XP gained |

Leveling up:

- XP needed to go from level **L** to level **L+1** is `100 × L²`. Level 1→2 costs 100 XP,
  level 2→3 costs 400, level 10→11 costs 10,000, etc.
- The **maximum level is 200**.
- On each level-up you see: `✦ Pet leveled up to Level N!` and your stats are recalculated.

**Milestones** are special levels at which a pet fires bonus rewards (usually `stat_modify`
grants, which are **permanent** stat increases to your character). The shipped pets' milestone
levels and rewards are listed in the
[Configuration Reference](#configuration-reference) below. If you gain enough XP to cross a
milestone level, the reward is applied at that exact level.

### Pet abilities

Each pet can react to three **triggers**:

| Trigger | When it fires |
| --- | --- |
| `ON_KILL` | You (with pet active) kill a mob |
| `ON_HIT` | You (with pet active) attack a mob |
| `ON_DEFEND` | A mob attacks **you** (with pet active) |

Shipped examples:

- **Baby Wolf** — on kill: `+5 HP from Baby Wolf!`; when defending: `Baby Wolf growls!`
- **Baby Sheep** — when defending: `Baby Sheep shields you!`
- **Ender Dragon** — on kill: grants **+2 Strength for ~10 seconds** (delayed revert)

What abilities do is fully defined by server config — see the reference below.

### Quick tips

- Keep your pet item in your hotbar and re-summon after every log-in (pets despawn on quit).
- Milestone grants are permanent; the passive stat bonus stops the moment you unsummon.
- The Ender Dragon is a full dragon entity — summon it somewhere open so you can see it.

---

## Admin Guide

### Permissions

- **There is no pet permission and no pet command.** Players summon purely by right-clicking
  a pet item.
- General admin commands (`/valmora reload` to hot-reload the module, item/mob admin tools)
  use `valmora.admin` as usual. `/valmora reload` will reload pet definitions from disk.
- A pet item is identified by the `valmora:pet_id` PersistentDataContainer tag. **Nothing in
  the plugin currently creates such items** — you must tag an item yourself (e.g. via a
  placeholder item and a script that sets the PDC, or a small admin tool), or wait for the
  item-distribution work tracked in `docs/todo.md:73`.

### Defining pets

Pet definitions live in **`plugins/Valmora/pets/*.yml`** (auto-copied from the jar on first
run; edits are not overwritten on restart). Every file is a list of pet sections; the section
key is the pet id used by the item tag.

Each pet needs:

- a `name` (shown on the summon tag and in messages),
- an `entity-type` (the Bukkit mob type to spawn, e.g. `WOLF`, `SHEEP`, `ENDER_DRAGON`),
- `base-stats` and/or `stats-per-level` for the passive bonus,
- optionally `abilities` (trigger + script event list) and `milestones` (level → script event
  list).

**Example — minimal pet:**

```yaml
my_cat:
  name: "Shadow Cat"
  entity-type: CAT
  base-stats:
    speed: 5
    ferocity: 2
  stats-per-level:
    ferocity: 0.2
```

**Example — with abilities and milestones:**

```yaml
baby_wolf:
  name: "Baby Wolf"
  entity-type: WOLF
  base-stats:
    strength: 5
    crit_chance: 2
  stats-per-level:
    strength: 0.5
    crit_chance: 0.1
  abilities:
    - trigger: ON_KILL
      events:
        - "notify chat <green>+5 HP from Baby Wolf!"
    - trigger: ON_DEFEND
      events:
        - "notify chat <gray>Baby Wolf growls!"
  milestones:
    "25":
      - "stat_modify add crit_chance 5"
      - "notify chat <gold>Your Baby Wolf reached milestone Level 25! +5 Crit Chance"
    "50":
      - "stat_modify add strength 15"
      - "notify chat <gold>Your Baby Wolf reached milestone Level 50! +15 Strength"
```

**Script DSL used by pets:**

- `notify chat <message>` — send a chat message (MiniMessage colors supported).
- `stat_modify add <stat_id> <value>` — permanently add to a **base** stat
  (`stat_modify set` and `stat_modify reset` also exist).
- `run_script delay:<ticks> <...>` — run further events after a delay (used for temporary
  buffs, e.g. Ender Dragon's +2 Strength for ~10s).

Stat ids in pet stats must match `plugins/Valmora/stats/core.yml` (e.g. `strength`,
`crit_chance`, `crit_damage`, `defense`, `health`, `damage`, `ability_damage`).

### Integration notes

- **`pet` script variables** are available in any script expression:
  `$pet.id$`, `$pet.name$`, `$pet.level$`, `$pet.xp$`, `$pet.max_xp$`, `$pet.active$`
  (`$pet.active$` is true/false; the others are `"none"`/`"None"`/0 when no pet is active).
- **Hot reload:** `/valmora reload` (requires `valmora.admin`) re-parses `pets/*.yml`,
  unregisters/re-registers the listener, and despawns any currently summoned pets. Invalid
  entries are logged as `Failed to parse pet '<id>': ...` and skipped — the server keeps
  running.
- **Persistence:** pet level/XP are stored **on the item** (PDC). They persist with your
  inventory/profile saves. The summoned pet and its active-slot binding are **not** saved —
  pets despawn on quit/reload.
- **Known scope:** pets are static (no follow AI), one pet at a time, main-hand right-click
  only. These are current implementation limits, not configuration options.

---

## Configuration Reference

**Location:** `plugins/Valmora/pets/*.yml`

### Pet section keys

| Key | Type | Default | Explanation |
| --- | --- | --- | --- |
| `name` | String | pet id | Display name (used on the name tag, summon/level messages, `$pet.name$`). |
| `entity-type` | String | `WOLF` | Bukkit entity type to spawn (case-insensitive). Invalid values silently fall back to `WOLF`. |
| `base-stats` | map (stat → number) | empty | Flat passive stat bonus at **every** level. |
| `stats-per-level` | map (stat → number) | empty | Bonus **per level**, added as `value × level` on top of `base-stats`. |
| `abilities` | list | empty | Each entry: `trigger` (`ON_KILL`/`ON_HIT`/`ON_DEFEND`) + `events` (list of script DSL strings). Unknown triggers are ignored. |
| `milestones` | map (level → list) | empty | Exact levels that fire bonus script events when reached (e.g. `"25"`). Non-numeric keys are ignored. |

### Fixed rules (not configurable)

| Rule | Value |
| --- | --- |
| XP required per level | `100 × level²` |
| Max pet level | `200` |
| XP per mob kill | `10` |
| Skill-XP share | `10%` |
| Summon position | 1 block east of player |
| Pet movement | none (AI disabled) |

### Default pets shipped (`baby_wolf.yml`)

#### Baby Wolf (`baby_wolf`)

- **Type:** `WOLF`
- **Base stats:** Strength `+5`, Crit Chance `+2`
- **Per level:** Strength `+0.5`, Crit Chance `+0.1`
- **Abilities:** `ON_KILL` → "`+5 HP from Baby Wolf!`"; `ON_DEFEND` → "`Baby Wolf growls!`"
- **Milestones:**
  - Level **25** → permanent `+5 Crit Chance`
  - Level **50** → permanent `+15 Strength`
  - Level **100** → permanent `+25 Strength`, `+20 Crit Damage`

#### Baby Sheep (`baby_sheep`)

- **Type:** `SHEEP`
- **Base stats:** Defense `+8`, Health `+15`
- **Per level:** Defense `+0.4`, Health `+0.8`
- **Abilities:** `ON_DEFEND` → "`Baby Sheep shields you!`"
- **Milestones:**
  - Level **25** → permanent `+10 Defense`
  - Level **50** → permanent `+30 Health`

#### Ender Dragon (`ender_dragon_pet`)

- **Type:** `ENDER_DRAGON`
- **Base stats:** Strength `+50`, Crit Damage `+30`, Damage `+20`
- **Per level:** Strength `+2.0`, Crit Damage `+1.0`, Damage `+1.0`
- **Abilities:** `ON_KILL` → `+2 Strength` for ~10 seconds (delayed revert)
- **Milestones:**
  - Level **50** → permanent `+50 Strength`
  - Level **100** → permanent `+100 Ability Damage`
