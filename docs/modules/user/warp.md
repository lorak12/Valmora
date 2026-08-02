# Warp Module — User Documentation

> **Applies to:** Valmora 0.1 — Paper 1.21.x
> **Configuration folder:** `plugins/Valmora/warps/`
> **Command:** `/warp [id]`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

Warps are **named teleport destinations**. The server admin defines a warp in a YAML file (name, destination coordinates, optional unlock condition, and the floor "pad" blocks that trigger it). Players then reach a warp in one of two ways:

- **Walk onto a warp pad** — stepping onto one of the configured pad blocks instantly teleports you to the destination.
- **Use `/warp <id>`** — teleports directly to the warp's destination.

All warps in this module are **free and instant** (no coins, no cooldown, no warm-up) and there are **no per-warp permissions** — access is controlled purely by the warp's `unlock-condition`. There is currently **no sign-warp mechanic** (walk onto a pad or use the command instead).

---

## Player Guide

### Using `/warp`

| Command | What it does |
|---|---|
| `/warp` | Opens the "fast travel" menu. **Note:** as shipped, this menu is not yet configured — nothing happens. Use `/warp <id>` instead. |
| `/warp <id>` | Teleports you to the named warp, e.g. `/warp hub_spawn`. |
| `/warp <id> <extra args>` | Extra arguments are ignored; only the first argument is used. |

- Warp ids are **case-insensitive** (`/warp HUB_SPAWN` works the same as `/warp hub_spawn`).
- Teleportation is asynchronous (Paper `teleportAsync`) — you arrive safely even if the destination chunk is unloaded.
- On success you see: `Teleported to <display-name>`.

### Possible messages

| Message | Meaning |
|---|---|
| `Player only.` | The command was run from the console (only players can warp). |
| `<red>Warp system not loaded.` | The module is disabled. |
| `<red>This warp is locked! Condition: <condition>` | The warp's `unlock-condition` is not met (see below). |
| `<red>World not loaded.` | The destination world isn't loaded. |
| `<red>Unknown warp: <id>` | No warp with that id exists. |

### Unlock conditions

A warp may be locked until you meet a requirement set by the admin:

| Condition | What you need |
|---|---|
| `always` | Nothing — always available. |
| `tag:<tag>` | The active profile must carry the tag, e.g. `tag:tier2_combat_unlocked`. |
| `skill:<skillId>:<level>` | Your level in the skill must be at least `<level>`, e.g. `skill:mining:10` = Mining level 10+. |

Conditions are checked against your **active profile** — switching profiles (`/profile switch`) changes which warps you can use. Unknown or malformed conditions count as locked.

### Warp pads

- Pads are **block positions** configured by the admin. You must **walk into** the pad block to trigger the warp.
- A pad area is usually a small cluster of blocks (the shipped demo uses 2×2 pads). Any block in the list triggers the warp.
- Pads are not physical pressure plates — there is no visual indicator unless the admin builds one. A common trick is to place end-portal frames or carpets on the pad blocks.

> **Not implemented (yet):** warp fees, warm-up/countdown, per-player cooldowns, per-warp permissions, and sign-activated warps. `docs/TESTING_GUIDE.md` mentions a warmup test that does not exist in the current build.

---

## Admin Guide

### Where warps are defined

Create `.yml` files in **`plugins/Valmora/warps/`**. On first boot the plugin copies the bundled `hub.yml` example there (existing files are never overwritten — see `Valmora.java:481-483`).

- **One file may hold many warps.** Every top-level key in the file is a warp id.
- All `.yml` files in the folder are loaded; every top-level key across all files becomes a warp.
- After editing, reload with `/valmora reload` (requires `valmora.admin`). Alternatively restart the server.
- If a warp fails to parse, the console prints `- [warps/<file>.yml] Error parsing warp '<id>': <reason>` and the warp is skipped; the rest still load.

### Minimal warp

```yaml
hub_spawn:
  display-name: "<gold>Hub Spawn"
  world: world
  x: 0.5
  y: 65.0
  z: 0.5
  yaw: 0
  pitch: 0
  unlock-condition: always
  pad-locations:
    - {x: 10, y: 64, z: 10}
    - {x: 11, y: 64, z: 10}
    - {x: 10, y: 64, z: 11}
    - {x: 11, y: 64, z: 11}
```

This defines a warp `hub_spawn`: destination `(0.5, 65.0, 0.5)` facing yaw 0 / pitch 0 in world `world`, always unlocked, triggered by a 2×2 pad of blocks at x/z 10–11 on y 64.

### A locked warp example

```yaml
deep_cavern_warp:
  display-name: "<dark_purple>Deep Caverns"
  world: world
  x: -500.5
  y: 40.0
  z: 320.5
  yaw: 0
  pitch: 0
  unlock-condition: skill:mining:15
  pad-locations:
    - {x: -60, y: 64, z: 60}
    - {x: -61, y: 64, z: 60}
```

Players below Mining level 15 see `This warp is locked! Condition: skill:mining:15` and are not teleported.

### Display names

`display-name` accepts **MiniMessage** tags: `<gold>`, `<gray>`, `<green>`, `<aqua>`, `<dark_gray>`, `<dark_purple>`, `<bold>`, etc. The name appears in the arrival message. (Do **not** use legacy `§` codes — see AGENTS.md §7.5.)

### Tips

- **Setting pads:** the easiest way is to stand on the pad block and copy its coordinates; e.g. a 2×2 pad at block `(10, 64, 10)` is `{x: 10, y: 64, z: 10}`, `{x: 11, y: 64, z: 10}`, `{x: 10, y: 64, z: 11}`, `{x: 11, y: 64, z: 11}`.
- **Pads are not required** — a warp with no `pad-locations` is still reachable via `/warp <id>` (and script events like `warp_to`).
- **Permissions:** `/warp` has **no permission node** — every player can use every warp. There is no `valmora.warp.<id>` permission today.
- **Zones:** the zone flag `teleportation: false` only blocks the script `teleport` event — it does **not** block warp pads, `/warp`, or the `warp_to` script event.

### Script integration (for quests / GUIs / abilities)

- **Event:** `warp_to <id>` teleports the acting player to the warp, e.g. `warp_to coal_mine_warp`.
- **Variables:**
  - `$warp.<id>.name$` — the warp's display name.
  - `$warp.<id>.unlocked$` — whether the acting player currently has it unlocked (true/false).
- **Quest command objectives:** `/warp` commands count toward quest `command` objectives, e.g. an objective requiring `/warp <player> farms`.

---

## Configuration Reference

All values live under each warp id in `plugins/Valmora/warps/*.yml`. Defaults shown are applied when the key is missing.

### Per-warp keys

| Key | Type | Default | Description |
|---|---|---|---|
| `display-name` | string (MiniMessage) | the warp id | Name shown in the arrival message (`Teleported to <name>`) and in lock messages. MiniMessage tags are honored. |
| `world` | string | `world` | Destination world name. Must be a loaded world or the teleport fails with "World not loaded." |
| `x` | double | `0` | Destination X coordinate. |
| `y` | double | `64` | Destination Y coordinate. |
| `z` | double | `0` | Destination Z coordinate. |
| `yaw` | double | `0` | Destination horizontal facing (degrees). |
| `pitch` | double | `0` | Destination vertical facing (degrees). |
| `unlock-condition` | string | `always` | Gate expression. See the table below. |
| `pad-locations` | list of maps | empty | Floor blocks that trigger the warp. Each entry: `{x: <int>, y: <int>, z: <int>}`. Missing `x/y/z` in an entry defaults to `0`. |

### `unlock-condition` reference

| Value | Meaning |
|---|---|
| `always` | Unlocked for everyone (also the default when the key is omitted). |
| `tag:<tag>` | Unlocked for players whose active profile has the tag `<tag>`. |
| `skill:<skillId>:<level>` | Unlocked for players whose `<skillId>` level is at least `<level>` (e.g. `skill:mining:10`). |
| anything else | Always locked. |

### `pad-locations` reference

```
pad-locations:
  - {x: 10, y: 64, z: 10}
  - {x: 11, y: 64, z: 10}
```

- Each entry is an exact block coordinate (integers). Walking into one of these blocks triggers the warp.
- A list of several entries forms a pad area (the demo uses 2×2 pads).
- A warp with no pads is reachable only via `/warp <id>` and script events.

### Shipped example — `warps/hub.yml`

The bundled demo file defines five warps (all `always` unlocked, `world`, yaw/pitch 0, 2×2 pads):

| Warp id | display-name | Destination |
|---|---|---|
| `hub_spawn` | `<gold>Hub Spawn` | (0.5, 65.0, 0.5) |
| `coal_mine_warp` | `<gray>Coal Mine` | (-55.5, 65.0, 90.5) |
| `forest_warp` | `<green>Whispering Forest` | (90.5, 65.0, -90.5) |
| `fishing_village_warp` | `<aqua>Fishing Village` | (150.5, 65.0, 150.5) |
| `graveyard_warp` | `<dark_gray>Graveyard` | (-115.5, 65.0, -105.5) |
