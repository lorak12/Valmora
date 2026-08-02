# Resource Module — User Documentation

> **Module ID:** `resource` | **Display name:** "Resource System" | **Version:** 0.1
> **Server:** Paper 1.21.x | **Config lives in:** `plugins/Valmora/zones/*.yml`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

The **Resource System** is Valmora's regenerating mining-block engine. It lets a server admin define *resource nodes* inside zones — blocks that:

- drop custom Valmora items or vanilla materials when mined,
- can require a minimum **Breaking Power** before they can be mined at all,
- progress through multiple stages before fully depleting,
- regenerate to their original state after a short delay,
- reward **Mining Fortune** (more drops) and **Mining Spread** (mine several blocks at once).

The Resource module itself has no separate configuration folder. Everything is defined under the **`resource-blocks:`** key of a zone definition file. On the shipped demo server this is the **Shardworks** mining zone (`zones/shardworks.yml`) with its tiered pickaxes (`items/shardworks_pickaxes.yml`).

---

## 2. Player Guide

### 2.1 What you see in game

Inside a configured mining zone (e.g. Shardworks), ores behave differently from vanilla:

- **Power-gated ores.** Some nodes show a red message when you lack the required tool:

  > This ore requires a more powerful tool.

  To mine those ores you must raise your **Breaking Power** stat — normally by equipping a better Valmora pickaxe (see §2.4). A plain vanilla diamond or netherite pickaxe has **no** Breaking Power and cannot break any Shardworks ore.

- **Custom drops.** Mining a node drops the zone's configured items directly into your inventory instead of vanilla ore drops. Drops are rolled per-entry with their own chance and amount range.
- **Stage progression.** A node may "degrade" several times before disappearing. For example a coal node becomes cobblestone on the first break, bedrock on the second, then regenerates back to coal.
- **Regeneration.** After a node fully depletes it respawns as its original ore after a short delay — you can farm the same spot indefinitely.

### 2.2 Relevant stats

Your mining effectiveness comes from the profile **stats** system (see `stats/core.yml`):

| Stat | Default | What it does in the Resource System |
| --- | --- | --- |
| `breaking_power` | 0 | Must be **≥** a node's `required-power` or you cannot mine it. |
| `mining_fortune` | 0 | Each point of Fortune gives `+1%` drop quantity: `final = max(base, round(base × (1 + fortune/100)))`. 100 Fortune = double drops. |
| `mining_spread` | 0 | Grants AOE mining. The whole stat value is floored to a radius — `mining_spread` 1 mines **1** additional matching block per break, 2 mines 2, etc. Neighbor blocks only count if they are the same material and are also resource nodes; each is mined through the same power gate and Fortune scaling, so under-powered neighbors are skipped silently. |

These stats are typically granted by item stats (see the Shardworks pickaxes below), the **Geomancy** progression tree, or the **Fortune** enchantment.

### 2.3 What players can control

- Nothing needs to be set up or configured by players — nodes, drops, and timers are defined by the server admin.
- Mining is fully passive: break blocks, collect drops, and let Fortune/Spread amplify your yield.

### 2.4 Example — the Shardworks tool progression

The demo zones define three ores with increasing power gates (`zones/shardworks.yml`):

| Ore | Required Power | Drops |
| --- | --- | --- |
| `DEEPSLATE_IRON_ORE` | 7 | `raw_ferrite` (2–4) |
| `AMETHYST_CLUSTER` | 8 | `raw_lumicite` (1–3) |
| `ANCIENT_DEBRIS` | 9 | `raw_aetherium` (1–2) |

The matching pickaxes (`items/shardworks_pickaxes.yml`) provide the stats to unlock each tier:

| Pickaxe | Breaking Power | Mining Speed | Mining Fortune | Mining Spread |
| --- | --- | --- | --- | --- |
| Ferrite Pickaxe | 7 | 120 | 5 | 0 |
| Lumicite Pickaxe | 8 | 150 | 15 | 1 |
| Aetherium Pickaxe | 10 | 200 | 30 | 3 |

So progression is: get Ferrite → mine iron ore → craft Lumicite → mine amethyst → craft Aetherium → mine ancient debris. A vanilla pickaxe cannot start the chain at all.

---

## 3. Admin Guide

### 3.1 Setup steps

1. **Place zone files** in `plugins/Valmora/zones/` (one or more `.yml` files, each containing one or more zone definitions).
2. Add a `resource-blocks:` map to the zone(s) you want to make minable.
3. Run `/valmora reload` (requires `valmora.admin`) to load the new definitions.
4. Verify in the console that zones loaded: `Successfully loaded N Zones.`

### 3.2 Permissions

The Resource System adds **no permissions of its own**. Relevant existing permissions:

| Permission | Purpose |
| --- | --- |
| `valmora.admin` | Required for `/zone` management and `/valmora reload` (`plugin.yml:46-49`, `57-60`). |

There is currently **no in-game command to add or edit resource blocks** — the `/zone` command supports `create|delete|info|list|wand|pos1|pos2|clear|flag|spawner|visualize` but no `resource` subcommand. Resource nodes are edited by hand in the YAML files, then reloaded.

### 3.3 Minimum working example

File: `plugins/Valmora/zones/silver_mine.yml`

```yaml
silver_mine:
  display-name: "<gray>Silver Mine"
  world: world
  min: [-80, 50, 60]
  max: [-30, 100, 120]

  resource-blocks:
    IRON_ORE:
      regen-delay: 300          # ticks (300 = 15 seconds)
      required-power: 3
      stages:
        - drops:
            - item: silver_ingot      # custom Valmora item id (lowercase)
              min: 1
              max: 3
              chance: 1.0
            - item: COBBLESTONE       # vanilla material fallback
              min: 1
              max: 1
              chance: 0.5
          next: STONE
        - drops:
            - item: STONE
              min: 1
              max: 1
              chance: 1.0
          next: BEDROCK
```

Behavior: players with Breaking Power ≥ 3 mine `IRON_ORE` → guaranteed 1–3 `silver_ingot` plus a 50% chance of 1 cobblestone → the block becomes STONE. Mining the STONE gives 1 stone → becomes BEDROCK (depleted). After 300 ticks the original `IRON_ORE` regenerates.

### 3.4 Legacy (single-stage) format

If you omit `stages`, a top-level `drops:` list is treated as a single stage and the block goes straight to air before regenerating:

```yaml
resource-blocks:
  COAL_ORE:
    regen-delay: 200
    drops:
      - item: COAL
        min: 1
        max: 3
        chance: 1.0
```

### 3.5 Using custom items as drops

Drop `item` values resolve in this order:

1. **Custom Valmora item id** — looked up in the item registry (lowercased). Example: `raw_ferrite`.
2. **Vanilla material name** — matched as a `Material` (uppercased) and auto-translated to a Valmora item (gains rarity + stat metadata). Example: `COAL`, `IRON_INGOT`.

If neither matches, the drop is silently skipped.

### 3.6 Stat wiring

The mining stats the Resource System reads are mapped in `config.yml`:

```yaml
mining:
  mining-fortune-stat: mining_fortune
  mining-speed-stat: mining_speed
  breaking-power-stat: breaking_power
  mining-spread-stat: mining_spread
```

These ids must exist in your `stats/*.yml` definitions. The defaults are shipped in `stats/core.yml` (`mining_fortune`, `breaking_power`, `mining_spread`, and `mining_speed`).

### 3.7 Interaction with zone flags and other systems

- **`block-breaking` zone flag:** if a zone has `block-breaking: false`, breaking is normally cancelled — **except** for resource blocks and their mid-progress stages, which remain breakable (`ZoneListener.java:105-115`).
- **Generic mining loot:** the global `LootListener` defers to the Resource System for resource blocks and tracked blocks, so resource nodes drop *only* their configured loot (no double drops).
- **Skills, collections, quests:** a successful resource break still counts as a normal `BLOCK_BREAK` for mining skill XP, collection progress, and `BLOCK_BREAK` quest objectives.

### 3.8 Reload behavior

`/valmora reload` disables and re-enables all modules. On disable, the Resource System **restores every tracked block to its original material immediately** and cancels all pending regeneration timers, so the world is never left half-mined after a reload.

---

## 4. Configuration Reference

All keys live under `resource-blocks:` inside each zone definition in `plugins/Valmora/zones/*.yml`. Defaults are applied automatically if a key is omitted.

```yaml
<zone-id>:
  resource-blocks:
    <MATERIAL>:
      regen-delay: 600
      required-power: 0.0
      stages:
        - drops:
            - item: "COBBLESTONE"
              min: 1
              max: 1
              chance: 1.0
          next: ""
```

### 4.1 `resource-blocks.<MATERIAL>`

- **Type:** map key (uppercase Bukkit material name)
- **Required:** yes
- The block type in the zone that becomes a resource node (e.g. `IRON_ORE`, `DEEPSLATE_IRON_ORE`, `ANCIENT_DEBRIS`, `AMETHYST_CLUSTER`). Unknown materials log a warning and are ignored.

### 4.2 `regen-delay`

- **Type:** integer, in **ticks** (20 ticks = 1 second)
- **Default:** `600` (30 seconds)
- Time after the final stage is mined until the original block regenerates.

### 4.3 `required-power`

- **Type:** number (double)
- **Default:** `0.0`
- Minimum **Breaking Power** stat the player needs. Below it the break is cancelled and the player sees "This ore requires a more powerful tool."

### 4.4 `stages`

- **Type:** list of maps
- **Default:** if absent, the **legacy flat format** is used (top-level `drops:` wrapped as one stage; block becomes air and regenerates).
- Ordered progression of the node. Each element:

#### 4.4.1 `stages[].drops` — list of drop entries

Each drop is rolled independently (`random() < chance`).

| Key | Default | Type | Description |
| --- | --- | --- | --- |
| `item` | `"COBBLESTONE"` | string | Custom Valmora item id (lowercase) or vanilla material name. Unresolvable ids are skipped. |
| `min` | `1` | int | Minimum amount for this drop when rolled. |
| `max` | `1` | int | Maximum amount. `min` is returned when `min >= max`. |
| `chance` | `1.0` | double | Roll probability, `0.0`–`1.0`. |

The rolled amount is then multiplied by Mining Fortune: `amount = max(base, round(base × (1 + fortune/100)))`.

#### 4.4.2 `stages[].next`

- **Type:** string (material name) or empty/null
- **Default:** none (block becomes `AIR`)
- The block material after this stage is mined. On the final stage this is the "depleted" block shown while the regen timer runs (commonly `BEDROCK`, `COBBLESTONE`, or the deepslate variant).

### 4.5 Full worked example (multi-stage, power-gated)

```yaml
ancient_mine:
  display-name: "<light_purple>Ancient Mine"
  world: world
  min: [-200, 0, -200]
  max: [-100, 60, -100]

  resource-blocks:
    DEEPSLATE_IRON_ORE:
      regen-delay: 400
      required-power: 7
      stages:
        - drops:
            - item: raw_ferrite
              min: 2
              max: 4
              chance: 1.0
          next: DEEPSLATE

    AMETHYST_CLUSTER:
      regen-delay: 600
      required-power: 8
      stages:
        - drops:
            - item: raw_lumicite
              min: 1
              max: 3
              chance: 1.0
          next: BUDDING_AMETHYST
```

### 4.6 Quick defaults summary

| Key | Default | Units |
| --- | --- | --- |
| `regen-delay` | `600` | ticks (20 = 1s) |
| `required-power` | `0.0` | stat value |
| `drops[].item` | `"COBBLESTONE"` | item id / material |
| `drops[].min` | `1` | amount |
| `drops[].max` | `1` | amount |
| `drops[].chance` | `1.0` | 0.0–1.0 |
| `stages[].next` | (none → AIR) | material name |
