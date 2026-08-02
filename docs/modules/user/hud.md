# HUD Item Module — User Documentation

> **Module ID:** `hud` | **Module Name:** "HUD Items"
> **Config folder:** `plugins/Valmora/hud-items/*.yml`
> **Hot reload:** `/valmora reload` (requires `valmora.admin`)

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The HUD module adds **HUD buttons** to players' hotbars. These are special inventory items that look like RPG-style menu buttons (e.g. a glowing gold "✦ Menu" star or an aqua "⚔ Profile" head) and react to being clicked. Unlike normal items they **cannot be moved, dropped, lost, or removed** — they always snap back to their configured slot.

Every HUD button is defined in a YAML file under `plugins/Valmora/hud-items/`. Each definition specifies:

- **where** the button sits (inventory slot),
- **what** it looks like (material, name, lore, glint),
- **what happens** on right-click and left-click (a list of scripted actions written in the Valmora event DSL, e.g. opening a menu, teleporting, giving items, or sending a message).

The bundled example ships two placeholder buttons: `menu_button` (hotbar slot 8) and `profile_button` (hotbar slot 7). Both currently just broadcast *"Menu coming soon!"* / *"Profile coming soon!"* — they are placeholders for real menus.

---

## Player Guide

**How HUD buttons appear**

- A HUD button appears in your hotbar automatically when you join the server.
- It is restored after you die and respawn, and after any plugin reload.
- It always sits in the same slot — you cannot move it around your inventory, and you cannot put other items into its slot by dragging over it.

**Behaviors**

| Action | Result |
|---|---|
| **Right-click** the button | Runs the button's right-click action (e.g. opens a menu or sends a message). |
| **Left-click** the button | Runs the button's left-click action (if defined; otherwise nothing happens). |
| Try to **move** it | The move is cancelled; the button stays put. |
| Try to **drop** it (Q / clicking it out of the inventory) | The drop is cancelled and the button is instantly returned to its slot. |
| **Die** | The button never drops on the ground. You get it back automatically on respawn. |
| Click it while in a **chest/other GUI** | The move is blocked; no click action fires (actions only fire from your own inventory). |

> Note: pressing a number key (1–9) over the button, middle-clicking, or double-clicking is treated as a **left** click, because only right-clicks are special-cased.

---

## Admin Guide

### Where configs live

- Runtime folder: `plugins/Valmora/hud-items/`
- Example file (`default.yml`) is copied there automatically on first run if it doesn't already exist — server edits are never overwritten.
- Format: one YAML file with **any number of definitions** per file (top-level keys are the definition IDs), or many files. All `*.yml` files in the folder are loaded.

### Workflow

1. Create or edit a file in `plugins/Valmora/hud-items/` (e.g. `buttons.yml`).
2. Run `/valmora reload` (permission `valmora.admin`) to apply changes.
3. Check the server console for a load summary: `Successfully loaded N HUD Item.` — or a list of warnings with exact file paths for any definition that failed to parse. A bad definition is skipped; the rest still load.

### Permissions

- **None** — HUD buttons work for every player. The only relevant permission is `valmora.admin`, needed for `/valmora reload` (see `plugin.yml`).
- There is no per-player toggle and no way to hide individual buttons.

### Example: a menu button that opens a teleport menu

```yaml
teleport_button:
  slot: 8
  glow: true
  item:
    material: ENDER_PEARL
    name: "<light_purple><bold>✦ Teleport Menu"
    lore:
      - "<gray>Right-click to open the fast travel menu"
  on-right-click:
    - "notify io:title title:<light_purple>Teleport Menu subtitle:<gray>Choose a destination"
```

### Example: a button with both click actions

```yaml
shop_button:
  slot: 6
  prevent-move: true
  item:
    material: EMERALD
    name: "<green><bold>Shop"
  on-right-click:
    - "notify io:actionbar <green>Opening shop..."
  on-left-click:
    - "notify io:actionbar <gray>Left-click does nothing yet"
```

### Click action DSL

The `on-right-click` / `on-left-click` lists use the **Valmora event DSL** (see the Script DSL reference in `docs/VALMORA_DOCUMENTATION.md` §33). Each list item is one event. Common options per event: `notify`, `delay:<ticks>`, `conditions:<...>`. Built-in events include `give`, `teleport`, `spawnmob`, `statmodify`, `tag`, `foreach`, `runscript`, `notify` (to the clicking player) and `notifyall` (to every online player).

Example with a condition and delay:

```yaml
on-right-click:
  - "conditions:$player.permission.admin$ notify io:chat <red>You are admin!"
  - "give DIAMOND:5 notify delay:20"
```

### Reload caveats

- After `/valmora reload`, buttons are re-issued to all online players from the new definitions.
- **Removing** a definition from YAML does **not** remove the old button item from already-online players' hotbars until they rejoin — so if you delete a button, ask players to relog, or set the slot to an unused one. (Known limitation — see the design doc's *Unfinished Things*.)

---

## Configuration Reference

### Top-level

| Key | Type | Default | Required | Explanation |
|---|---|---|---|---|
| `<id>` | string | — | yes | Unique definition ID for the button. It becomes the internal identity marker stored on the item. |
| `<id>.slot` | int | `8` | no | Inventory slot to render the button in. `0`–`8` are hotbar slots, `9`–`35` are the main inventory rows. |
| `<id>.prevent-move` | bool | `true` | no | *Documented config intent:* whether the button may be moved/dropped. **Currently every HUD item is locked regardless of this value** (the option is parsed but not yet enforced). |
| `<id>.glow` | bool | `false` | no | `true` gives the item an enchanted glint (with the enchantment line hidden). |
| `<id>.item` | section | — | **yes** | The visual definition. Required — the button fails to load without it. |
| `<id>.on-right-click` | string list | *(none)* | no | DSL events run when the button is right-clicked (or shift-right-clicked). |
| `<id>.on-left-click` | string list | *(none)* | no | DSL events run on any other click (left, shift-left, number-key swap, etc.). |

### `item` section

| Key | Type | Default | Required | Explanation |
|---|---|---|---|---|
| `<id>.item.material` | string | `STONE` | no | Bukkit material name, e.g. `NETHER_STAR`, `PLAYER_HEAD`, `EMERALD`. Invalid names are rejected at load time. |
| `<id>.item.name` | string | *(material name)* | no | Display name in **MiniMessage** format, e.g. `"<gold><bold>✦ Menu"`. Italics are automatically removed. |
| `<id>.item.lore` | string list | *(none)* | no | Lore lines in MiniMessage format. |
| `<id>.item.custom-model-data` | int | `0` | no | `CustomModelData` for custom resource-pack textures. Only applied when `> 0`. |

### Defaults recap

- `slot: 8`, `prevent-move: true`, `glow: false`, `material: STONE`, `custom-model-data: 0`.
- Missing `item` section, or an invalid `material`, fails that definition with a clear console error.
- Missing `on-right-click` / `on-left-click` simply means "do nothing" for that click type.

### Bundled default file (`default.yml`)

| Definition | Slot | Looks like | Right-click | Left-click |
|---|---|---|---|---|
| `menu_button` | 8 | Glowing gold "✦ Menu" nether star | `notifyall io:actionbar <yellow>Menu coming soon!` | Same as right-click |
| `profile_button` | 7 | Aqua "⚔ Profile" player head | `notifyall io:actionbar <aqua>Profile coming soon!` | *(none)* |

---

_Last updated: see git history. Example config: `src/main/resources/hud-items/default.yml`. See `docs/modules/design/hud.md` for the full code-side design._
