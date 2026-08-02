# Zone Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `zone` | **Config folder:** `plugins/Valmora/zones/`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

Zones are **named world regions** that change how the game behaves inside their boundaries. They are the backbone of location-scoped gameplay in Valmora:

- **Flags** control what is allowed inside: PvP, block breaking/placing, natural mob spawning, hunger, entry, teleportation, and leaf decay.
- **Resource nodes** turn ore blocks inside a zone into regenerating mines with custom drops and tool-power requirements.
- **Mob spawners** periodically spawn custom mobs inside a zone (with an alive cap).
- **Fishing zones** link a region to a custom fishing loot table.
- **Enter/exit actions** run scripted events when a player crosses the boundary.
- **Notifications** show the zone's display name in the action bar on entry and in the scoreboard.

A zone is defined by an **inclusive axis-aligned box** (`min` and `max` corners) plus optional extra boxes for non-rectangular shapes. A zone belongs to exactly one world. Zones live in `plugins/Valmora/zones/*.yml` — one file can contain many zones.

Everything in this module is **admin-facing**. Regular players experience zones passively; the only player-facing command permission is the admin command set below.

---

## Player Guide

### How zones feel in-game

1. **Entering a zone** shows its `display-name` in the action bar for 3 seconds (`ZoneListener.java:78`) and updates the scoreboard "Zone:" line. Outside any zone the scoreboard shows `Wilderness` (`ScoreboardUI.java:201-206`).
2. **Flags change behavior automatically.** Examples shipped with the plugin:
   - In the `mine` zone, PvP is off, hunger is frozen (`allow.hunger: false`), and natural mobs do not spawn.
   - In `mob_spawn`, the two spawners summon `test_zombie` and `test_skeleton` mobs near their anchors.
   - In Shardworks, `DEEPSLATE_IRON_ORE`, `AMETHYST_CLUSTER`, and `ANCIENT_DEBRIS` regenerate after mining, require a **Breaking Power** of 7/8/9, and drop the custom items `raw_ferrite`, `raw_lumicite`, and `raw_aetherium`.
3. **Mining resource nodes** is different from vanilla:
   - Each break is gated by `required-power` — a too-weak tool cancels the break and tells you *"This ore requires a more powerful tool."*
   - Drops are placed **directly into your inventory** (no need to pick them up).
   - Nodes can have multiple **stages**: e.g. coal ore drops `COAL` and becomes `COBBLESTONE`, then drops cobble and becomes `BEDROCK` before regenerating back to ore after `regen-delay` ticks.
   - Drop quantities scale with your **Mining Fortune** stat, and the **Mining Spread** stat can AOE-mine adjacent matching blocks.
4. **Fishing in a fishing zone** uses the zone's linked loot table instead of vanilla loot (see the Fishing module docs, `docs/modules/user/fishing.md`).
5. **Teleportation** may be blocked in some zones (only enforced by scripted `teleport` events — warps are not affected).
6. **PvP zones** (`allow.pvp: true`) allow player-vs-player damage; everywhere else PvP is cancelled.

### Player commands

There are **no player-facing zone commands** — `/zone` is admin-only (`valmora.admin`, `plugin.yml:46-49`). Players never need to type a zone command.

---

## Admin Guide

### Permissions

| Permission | Effect |
|---|---|
| `valmora.admin` | Required for every `/zone` sub-command and for `/valmora reload` (which re-reads all zones). Declared at `plugin.yml:48` and enforced in `ZoneCommand.java:48-51`. |

### The `/zone` command

All sub-commands are player-only (console gets *"Only players can use this command."* — `ZoneCommand.java:44-46`).

```
/zone                                  → help menu
/zone wand                             → give yourself the selection wand
/zone pos1 | pos2                      → set selection corner at your feet
/zone clear                            → clear your selection
/zone create <id> [display-name]       → create a zone from your selection
/zone delete <id>                      → delete a zone
/zone info <id>                        → show details (bounds, flags, spawners)
/zone list                             → list all zones
/zone flag <id> <flag> <true|false>    → toggle a flag
/zone spawner add <zoneId> <mobId> [spawnRadius] [maxAlive] [interval]
/zone spawner remove <zoneId> <spawnerId>
/zone spawner list <zoneId>
/zone visualize                        → toggle yellow zone-border particles
```

Tab completion is provided for sub-commands, zone IDs, flag names, mob IDs (on `spawner add`), and spawner IDs (on `spawner remove`) (`ZoneCommand.java:329-367`).

### Workflow 1 — Defining a zone (in-game)

1. Run `/zone wand`. A golden axe named **Zone Wand** is added to your inventory.
2. **Left-click** a block to set **Pos1**, **right-click** a block to set **Pos2** (`ZoneWandListener.java:23-50`). A live preview shows Pos1 in blue, Pos2 in red, and the resulting box in green.
3. Optionally fine-tune with `/zone pos1` / `/zone pos2` (set at your feet) or `/zone clear`.
4. Run `/zone create my_area My Area` — the zone is registered and written to `plugins/Valmora/zones/my_area.yml`.
5. Run `/zone flag my_area pvp true` (or any of the eight flags) to configure it.
6. Run `/zone visualize` to see the borders as yellow particles.
7. Add mob spawners (below) or hand-edit the file for resource blocks/fishing/actions, then `/valmora reload`.

> **Warning:** `/zone flag` and `/zone spawner` rewrite the zone file. Keys that are only editable by hand — `fishing-loot-table`, `resource-blocks`, `enter-actions`, `exit-actions` — are **not** preserved by these commands (`ZoneManager.saveZoneToFile`). Edit those first, or re-apply them after any command, or keep them in a separate zone file that you don't touch with commands.

### Workflow 2 — Mob spawners

```
/zone spawner add <zoneId> <mobId> [spawnRadius=3] [maxAlive=5] [interval=400]
/zone spawner list <zoneId>
/zone spawner remove <zoneId> <spawnerId>
```

- The spawner is anchored at **your current position** (`ZoneCommand.java:275-277`).
- `mobId` must exist in the mob registry (`ZoneCommand.java:265-269`).
- Every `interval` ticks, the zone tries to spawn the mob near the anchor, but **stops while `maxAlive` mobs of that type are alive within the counting radius**.
- Spawned mobs get a hidden "home" tag (`valmora:mob_home`); if a mob wanders out of the zone, the plugin orders it back (behavior task every 40 ticks).

### Workflow 3 — Resource nodes

There is **no in-game command** for resource blocks — define them by editing YAML (see [Configuration Reference](#configuration-reference)) and run `/valmora reload`. Example from the shipped `mine` zone:

```yaml
mine:
  display-name: "<gray>Mine"
  world: world
  min: [-100, 131, -100]
  max: [-79, 153, -89]
  allow:
    pvp: false
    natural-mob-spawning: false
    block-breaking: false
    block-placing: false
    hunger: false
    entry: true
    teleportation: true
    leaf-decay: false
  resource-blocks:
    COAL_ORE:
      regen-delay: 200
      stages:
        - drops:
            - item: COAL
              min: 1
              max: 1
              chance: 1.0
          next: COBBLESTONE
        - drops:
            - item: COBBLESTONE
              min: 1
              max: 1
              chance: 1.0
          next: BEDROCK
```

This makes every `COAL_ORE` block inside the box a two-stage node: first break drops 1 coal and turns to cobblestone; second break drops 1 cobblestone and turns to bedrock; after 200 ticks it regenerates to coal ore. Use a **custom item id** (like `raw_ferrite`) instead of a vanilla material to drop Valmora items, and add `required-power` to gate the node behind a Breaking Power tool:

```yaml
DEEPSLATE_IRON_ORE:
  regen-delay: 400
  required-power: 7
  stages:
    - drops:
        - { item: raw_ferrite, min: 2, max: 4, chance: 1.0 }
      next: DEEPSLATE
```

> Tools need the Breaking Power stat — vanilla tools have none, so they cannot mine `required-power > 0` nodes. Shipped example: `items/shardworks_pickaxes.yml` (pickaxes with Breaking Power 7/8/10).

### Workflow 4 — Enter/exit script actions

```yaml
my_zone:
  display-name: "<gold>My Zone"
  world: world
  min: [0, 60, 0]
  max: [10, 70, 10]
  enter-actions:
    - notify title:"<red>Entering" subtitle:"My Zone"
  exit-actions:
    - notify title:"<gray>Leaving"
```

Any script DSL lines are run when a player enters/exits (see the Script module docs for available events).

### Reloading

```
/valmora reload        # requires valmora.admin
```

Re-reads every `plugins/Valmora/zones/*.yml`, unregisters/registers all listeners, and restarts the four zone tasks. Note that player-selection state and spawner timing reset on reload.

---

## Configuration Reference

File location: `plugins/Valmora/zones/*.yml`. Each **top-level key is a zone ID**; you may put multiple zones in one file. Case does not matter for zone IDs (looked up case-insensitively), but lowercase is recommended.

### Full schema (with defaults)

```yaml
<zone-id>:
  display-name: "<green><id>"       # MiniMessage name; default "<green><id>"
  world: world                       # Bukkit world; default "world"
  min: [x, y, z]                     # REQUIRED inclusive min corner
  max: [x, y, z]                     # REQUIRED inclusive max corner

  allow:                             # optional section
    pvp: false                       # PvP damage allowed
    natural-mob-spawning: false      # natural/slime/spawner mob spawns
    block-breaking: false            # players may break blocks
    block-placing: false             # players may place blocks
    hunger: true                     # hunger depletes normally
    entry: true                      # players may enter
    teleportation: true              # teleports allowed
    leaf-decay: true                 # leaves decay normally

  extra-boxes:                       # optional extra sub-regions
    - min: [x, y, z]
      max: [x, y, z]

  fishing-loot-table: <table-id>     # optional fishing/*.yml table

  mob-spawners:                      # optional spawner map
    <spawner-id>:
      mob: zombie                    # Valmora mob ID; default "zombie"
      x: 0                           # anchor coords; defaults 0 / 64 / 0
      y: 64
      z: 0
      spawn-interval: 200            # ticks between attempts; default 200
      max-alive: 5                   # alive cap in radius; default 5
      radius: 20.0                   # alive-count radius; default 20.0
      spawn-radius: 3                # placement scatter; default 3

  resource-blocks:                   # optional resource-node map
    <MATERIAL>:                      # e.g. COAL_ORE, AMETHYST_CLUSTER
      regen-delay: 600               # ticks to regenerate; default 600
      required-power: 0.0            # min Breaking Power; default 0.0
      stages:                        # ordered break stages
        - drops:                     # items rolled at this stage
            - item: COBBLESTONE      # custom item id OR vanilla material; default "COBBLESTONE"
              min: 1                 # min amount; default 1
              max: 1                 # max amount; default 1
              chance: 1.0            # probability 0.0–1.0; default 1.0
          next: STONE                # material after this stage; default absent ⇒ AIR

  enter-actions: []                  # optional script DSL lines on entry
  exit-actions: []                   # optional script DSL lines on exit
```

### Zone identity

| Key | Default | Type | Meaning |
|---|---|---|---|
| `<zone-id>` | — | map key | Zone ID. Used in `/zone`, quest `REACH_ZONE` objectives, and the `$zone.id$` script variable. Stored lowercase. |
| `display-name` | `"<green><id>"` | string (MiniMessage) | Shown in the action bar on entry, in `/zone info`, and on the scoreboard. Use MiniMessage formatting (`<gold>`, `<bold>`, etc.). |
| `world` | `"world"` | string | The Bukkit world this zone applies to. Locations in other worlds are never inside the zone. |
| `min` | *(none — required)* | list `[x, y, z]` | Inclusive minimum corner of the primary box. Fewer than 3 values ⇒ the zone fails to load. |
| `max` | *(none — required)* | list `[x, y, z]` | Inclusive maximum corner of the primary box. |

### Flags (`allow:`)

| Key | Default | Meaning |
|---|---|---|
| `allow.pvp` | `false` | If `false`, damage between two players inside the zone is cancelled. |
| `allow.natural-mob-spawning` | `false` | If `false`, natural/slime-split/spawner mob spawns are cancelled. Zone mob spawners, commands, scripts, and fishing creatures still spawn. |
| `allow.block-breaking` | `false` | If `false`, players cannot break blocks — **except** configured resource nodes and their intermediate stages, which always remain breakable. |
| `allow.block-placing` | `false` | If `false`, players cannot place blocks. |
| `allow.hunger` | `true` | If `false`, the player's hunger bar is frozen (no food loss). |
| `allow.entry` | `true` | If `false`, players are pushed back when they try to walk into the zone. |
| `allow.teleportation` | `true` | If `false`, scripted `teleport` events are blocked (warps and other teleports are NOT affected). |
| `allow.leaf-decay` | `true` | If `false`, leaves in the zone never decay. |

> Legacy: if the `allow:` section is omitted, a `pvp-enabled:` key is honored and the other seven flags fall back to their defaults.

### Shape

| Key | Default | Type | Meaning |
|---|---|---|---|
| `extra-boxes` | *(none)* | list of `{min: [x,y,z], max: [x,y,z]}` | Additional sub-boxes that count as part of the zone, enabling non-rectangular shapes. A location is "in the zone" if it is inside the primary box OR any extra box. Malformed entries are skipped. |

### Fishing linkage

| Key | Default | Type | Meaning |
|---|---|---|---|
| `fishing-loot-table` | *(none)* | string | ID of a fishing loot table (`plugins/Valmora/fishing/*.yml`). Fishing inside the zone uses this table; absent ⇒ the table literally named `default` is used. |

### Mob spawners (`mob-spawners:`)

| Key | Default | Type | Meaning |
|---|---|---|---|
| `mob-spawners.<id>` | — | map key | Spawner ID (used by `/zone spawner remove`). |
| `mob` | `"zombie"` | string | Valmora mob ID to spawn. Unknown IDs are skipped. |
| `x`, `y`, `z` | `0`, `64`, `0` | int | Anchor block. Spawned mobs "belong" to this anchor (they are ordered back here if they leave the zone). |
| `spawn-interval` | `200` | int (ticks) | Ticks between spawn attempts (20 ticks = 1 second). |
| `max-alive` | `5` | int | Maximum number of that mob type simultaneously alive within `radius`. |
| `radius` | `20.0` | double | Radius (blocks) in which mobs are counted for the `max-alive` cap. |
| `spawn-radius` | `3` | int | Radius (blocks) around the anchor where spawns are placed (and the wander radius for spawned mobs). |

### Resource nodes (`resource-blocks:`)

| Key | Default | Type | Meaning |
|---|---|---|---|
| `resource-blocks.<MATERIAL>` | — | material key | Block type that becomes a resource node (e.g. `COAL_ORE`, `AMETHYST_CLUSTER`, `ANCIENT_DEBRIS`). Material names are case-insensitive. Unknown materials are skipped with a warning. |
| `regen-delay` | `600` | int (ticks) | Time after the last stage before the original block regenerates. **Ticks** (600 = 30 seconds), not seconds. |
| `required-power` | `0.0` | double | Minimum **Breaking Power** stat to mine this node. Below it the break is cancelled with a warning message. |
| `stages` | *(none)* | list | Ordered break stages. If absent, a legacy flat `drops:` list is treated as a single stage that turns the block to air. |
| `stages[].drops` | — | list | Drops rolled independently (each checks `chance`) when this stage is mined. |
| `stages[].drops[].item` | `"COBBLESTONE"` | string | A Valmora **custom item id** (e.g. `raw_ferrite`) or a vanilla **material name** (e.g. `COAL`, `DIAMOND`). |
| `stages[].drops[].min` | `1` | int | Minimum amount given. |
| `stages[].drops[].max` | `1` | int | Maximum amount given (random between `min` and `max`). |
| `stages[].drops[].chance` | `1.0` | double | Probability (0.0–1.0) the drop is given. |
| `stages[].next` | *(none)* | string (material) | Material the block becomes after this stage is mined. Absent ⇒ `AIR`. On the final stage this is the "depleted" look while the regen timer runs (e.g. `BEDROCK`, `COBBLESTONE`). |

### Script actions

| Key | Default | Type | Meaning |
|---|---|---|---|
| `enter-actions` | *(none)* | list of strings | Script DSL lines executed when a player enters the zone. |
| `exit-actions` | *(none)* | list of strings | Script DSL lines executed when a player exits the zone. |

### Script variables

Inside script expressions, the `zone` namespace exposes the player's current zone (see the Script module docs for how to use variables):

- `$zone.id$` — zone ID, or empty outside any zone.
- `$zone.current$` / `$zone.name$` — display name, or `Wilderness` outside any zone.
- `$zone.pvp$` — `true` if PvP is enabled in the current zone, else `false`.
