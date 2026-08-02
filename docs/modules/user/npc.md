# NPC Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `npc` | **Config folder:** `plugins/Valmora/npcs/`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The NPC module is the **custom NPC engine**. An NPC is a YAML definition in `plugins/Valmora/npcs/*.yml` describing an entity type, a spawn position, a display name, click actions, an optional skin (for `MANNEQUIN` NPCs), an optional bound **conversation**, and conditional floating-text **holograms**. NPCs are **world entities** — they spawn automatically at their defined location when the plugin loads and are restored on reload, chunk loss, or server restart.

Key facts:

- NPCs are defined in `plugins/Valmora/npcs/*.yml`. Each top-level key is the NPC's ID.
- NPCs **auto-spawn** at their configured `x/y/z` when the server starts (and re-spawn on reload). A background task re-spawns any NPC whose entity disappears (e.g. chunk unload) every 60 seconds.
- **Right-click** an NPC to interact: if a conversation is bound it starts; otherwise the `on-right-click` script actions fire. **Left-click** fires the `on-left-click` actions (both always trigger the plugin's quest `talk_to_npc` objective check).
- Conversations are the plugin's **dialogue system**: an NPC/player dialogue tree rendered as in-chat messages with clickable choices and keyboard navigation. Definitions live in **quest packages** (`conversations:` sections), not in a `dialogues/` folder.
- Interactions are driven by the **shared script DSL** (`open_gui`, `quest_board_assign`, `dialogue start`, …), so NPCs can open GUIs, assign quests, play sounds, and more without any new code.
- Name tags and holograms use lightweight `TextDisplay` entities (AGENTS.md §11.17).

---

## Player Guide

### Interacting with NPCs

NPCs are ordinary-looking entities with a floating name tag:

- **Right-click** an NPC to talk to it. If the NPC has a conversation bound, the dialogue screen opens; otherwise the NPC performs its configured right-click actions (opens a GUI, assigns a quest, etc.).
- **Left-click** an NPC to trigger its left-click actions (often nothing, or a "not in that way" line). NPCs are invulnerable — you cannot damage them.

### Conversations

When you talk to an NPC, the dialogue is rendered directly in your chat, one line per message. The NPC's name is shown above each of its lines, e.g.:

```
<gold><bold>Elder Thorin ▶ <yellow>The mine has gone silent. Will you help us?
  ► [1] I will investigate the mine
  ► [2] I am not ready for that yet.
```

You have several ways to pick a reply:

- **Click** a numbered choice (`► [n]`).
- Type the **number** of the choice in chat and press Enter.
- Use the conversation **controls** (see below) and confirm with a jump.

**Keyboard controls** (while a conversation is open):

| Input | Effect |
|---|---|
| **Jump / Space** | Confirm the highlighted choice — or, while the NPC is still talking, skip ahead. |
| **Sneak / Shift** | Leave the conversation. |
| **Move forward** | Move the highlight up to the previous choice. |
| **Move backward** | Move the highlight down to the next choice. |

Some NPCs **auto-advance** from one line to the next without asking you anything (a monologue). The chat will show a hint to press Space to skip. If the conversation has `stop: true`, walking more than one block away teleports you straight back to the NPC until you finish or sneak out.

> While you are in a conversation, other players' chat is hidden and the action bar is suppressed so nothing distracts from the dialogue. Leaving the conversation restores the chat history that was on screen before it started.

### What NPCs can do

Anything the script engine supports — depending on how the server configured the NPC this can include:

- **Quest givers** — start or advance quests (fires the quest `talk_to_npc` objective when you interact).
- **GUIs / shops** — open custom GUIs, quest boards, etc. (via `open_gui`).
- **Info / lore** — plain chat lines or simple scripted responses.

---

## Admin Guide

### Creating an NPC

The quickest way to create an NPC is in-game:

```
/npc create <id> <entity_type>
```

This spawns the NPC at your feet, registers it, and saves it to `plugins/Valmora/npcs/from_command.yml`. From there, `/npc move`, `/npc rename`, `/npc setyaw`, `/npc conversation`, and `/npc skin` let you shape it without touching YAML. To move an existing NPC to your location: `/npc move <id>`.

Alternatively, author the YAML directly (see [Configuration Reference](#configuration-reference)).

### `/npc` command

Requires the `valmora.admin` permission (OP by default, `plugin.yml:53-56`). All subcommands support tab completion.

| Subcommand | Usage | Description |
|---|---|---|
| `create` | `/npc create <id> <entity_type>` | Create an NPC at your location. Entity type must be a living entity (e.g. `VILLAGER`, `MANNEQUIN`, `ZOMBIE`). |
| `delete` | `/npc delete <id>` | Remove the NPC from the world and from its source file. |
| `list` | `/npc list` | List every registered NPC; click an entry for details. |
| `info` | `/npc info <id>` | Show the full definition (name, type, world, position, conversation, action counts, source file). |
| `tp` | `/npc tp <id>` | Teleport yourself to the NPC's position. |
| `move` | `/npc move <id>` | Move the NPC to your current position and save. |
| `rename` | `/npc rename <id> <name...>` | Change the display name (MiniMessage) and save. |
| `settype` | `/npc settype <id> <entity_type>` | Change the entity type and save. |
| `setyaw` | `/npc setyaw <id> [yaw]` | Rotate the NPC (defaults to your facing) and save. |
| `conversation` | `/npc conversation <id> <dialogue_id>` | Bind a conversation (tab-completes registered dialogue IDs) and save. |
| `clearconv` | `/npc clearconv <id>` | Remove the bound conversation and save. |
| `skin` | `/npc skin <id> player\|url\|file\|reset [value]` | Set a player skin — **MANNEQUIN NPCs only** (see [Skins](#skins)). |
| `near` | `/npc near [radius]` | List NPCs within a radius of you (default 32 blocks). |
| `look` | `/npc look <id>` | Toggle "face the nearest player" and save. ⚠ Takes effect on the next respawn/reload, not instantly. |
| `showname` | `/npc showname <id>` | Toggle the floating name tag and save. |
| `reload` | `/npc reload` | Reloads **all** modules (equivalent to `/valmora reload`). |

> **Note:** `/npc reload` reloads every module, not just NPCs. There is no NPC-only reload — iterate on `npcs/*.yml` with `/valmora reload` or the server.

### Conversations

Conversations are authored in **quest packages**, not in `npcs/*.yml`:

1. Inside a quest package (e.g. `plugins/Valmora/quests/forgotten_mine/`), add a `conversations:` section — either in the quest's main file or a sibling `conversations.yml` (see [Configuration Reference](#configuration-reference)).
2. Bind the conversation to an NPC with the quest file's `npc_conversations:` block:

```yaml
# quests/forgotten_mine/quest.yml
npc_conversations:
  thorin: thorin_main
  bjorn:  bjorn_main
```

The binding **overrides** any `conversation:` key on the NPC itself. Shipped examples: `quests/forgotten_mine/conversations.yml` (`thorin_main`, `bjorn_main`) and `quests/blacksmith_hub/blacksmith.yml` (`blacksmith`).

### Skins

Player-looking NPCs use the `MANNEQUIN` entity type. Skins are only applied to `MANNEQUIN` NPCs — other types reject the command.

| Source | Command | Notes |
|---|---|---|
| **Player name** | `/npc skin <id> player <playername>` | Fetches the player's current skin from Mojang. |
| **Image URL** | `/npc skin <id> url <image_url>` | Renders the image as a skin via mineskin.org. |
| **Local file** | `/npc skin <id> file <filename>` | Uses a PNG from `plugins/Valmora/skins/` served by the built-in skin server (see [config.yml — skin file server](#configyml--skin-file-server)). |
| **Reset** | `/npc skin <id> reset` | Clears the skin. |

### Reloading

When you edit `npcs/*.yml` or conversation files, run `/valmora reload` (or `/npc reload`) on the server. The plugin despawns every NPC (entity, name tag, hologram), re-reads the YAML, and re-spawns them. Parse errors are logged with the file path and NPC ID.

> **Stale documentation:** older docs (`docs/USER_DOCS.md` §14–§15) describe a `type:`, `gui:`, and `dialogue:` NPC schema and a `dialogues/*.yml` conversation format. **These do not exist in the current code.** The real schema uses `conversation:` / `on-right-click` and conversations defined in quest packages. Ignore the old sections.

### Permissions

| Permission | Default | Grants |
|---|---|---|
| `valmora.admin` | OP | `/npc` (all subcommands) and `/valmora reload`. |

The player-facing dialogue click handler (`/valmora npc-choice <n>`) has **no** permission requirement.

---

## Configuration Reference

### `plugins/Valmora/npcs/*.yml` — NPC definitions

Every `*.yml` in `plugins/Valmora/npcs/` is scanned at startup; each **top-level key becomes the NPC ID** (stored lowercase, case-insensitive when referenced elsewhere). The folder is auto-created with a sample file on first run.

Minimal NPC:

```yaml
# plugins/Valmora/npcs/mynpcs.yml
village_elder:
  display-name: "<yellow>Elder Thorin"
  entity-type: VILLAGER
  world: world
  x: 10
  y: 64
  z: 10
  yaw: 0
```

| Key | Type | Required | Default | Explanation |
|---|---|---|---|---|
| `<npc-id>` | map key | **yes** | — | The NPC's ID, used by `/npc`, quest bindings, and objectives. |
| `display-name` | String (MiniMessage) | no | `"<white>" + id` | Floating name tag above the NPC. |
| `entity-type` | String enum | no | `VILLAGER` | Any living Bukkit `EntityType`. `MANNEQUIN` enables skins. |
| `world` | String | no | `world` | World name; a missing/unknown world aborts the spawn with a warning. |
| `x`,`y`,`z` | Double | no | `0`, `64`, `0` | Spawn position. |
| `yaw` | Float | no | `0` | Horizontal facing. |
| `conversation` | String | no | *(none)* | Dialogue ID to start on right-click. **Overrides `on-right-click`** (but a quest-package `npc_conversations:` binding overrides this). |
| `on-right-click` | String list | no | empty | Script DSL actions run on right-click (only when no conversation is bound). |
| `on-left-click` | String list | no | empty | Script DSL actions run on left-click. |
| `look-at-player` | Boolean | no | `false` | Face the nearest player within 10 blocks. |
| `show-name` | Boolean | no | `true` | Show the floating name tag. |
| `skin-texture` | String (base64) | no | *(none)* | Mojang texture `value`; `MANNEQUIN` only. |
| `skin-signature` | String | no | *(none)* | Mojang texture `signature`; `MANNEQUIN` only. |
| `holograms` | List | no | empty | Conditional floating-text lines (see below). |

A full example with actions and holograms:

```yaml
shardworks_prospector:
  display-name: "<yellow>Prospector Kade"
  entity-type: VILLAGER
  world: world
  x: -100
  y: 20
  z: -100
  yaw: 0
  look-at-player: true
  show-name: true
  on-right-click:
    - "quest_board_assign shardworks"
    - "open_gui shardworks_quest_board"
  holograms:
    - name: title
      text: "<gold>Quest Board"
      vector: { x: 0, y: 0.5, z: 0 }
```

This is the shipped `shardworks_npcs.yml` example extended with a hologram.

### `holograms:` entry

| Key | Type | Default | Explanation |
|---|---|---|---|
| `name` | String | (required) | Unique per NPC; used as the task/map key. |
| `text` | String (MiniMessage) | `""` | The hologram line. |
| `vector` | map `{x,y,z}` | `0,0,0` | Offset from the NPC position + 2.0 Y above its feet. |
| `conditions` | String list | empty | Condition DSL — the line is visible only while all conditions pass. ⚠ Evaluated with **no player context**, so use world/stateless conditions only (e.g. time-of-day, weather). |
| `check_interval` | Integer | `60` | Ticks between condition re-checks (min 1). |

### Conversations — quest-package `conversations:` section

Conversations are defined inside quest package YAML. The parser also reads a top-level `conditions:` and `events:` block in the same file and allows **named** references from conversation nodes. Shipped example: `quests/forgotten_mine/conversations.yml`.

```yaml
conditions:
  mine_not_started: "!quest forgotten_mine in_progress"
  mine_completed:   "quest forgotten_mine completed"

events:
  start_forgotten_mine: "quest_start forgotten_mine"

conversations:
  thorin_main:
    quester: "<gold><bold>Elder Thorin"
    stop: true
    first:
      - greeting_new
      - greeting_progress
      - greeting_done
    final_events:
      - "sound player entity.villager.no"
    NPC_options:
      greeting_new:
        text: "<yellow>The mine has gone silent. Will you help us?"
        conditions: "mine_not_started, mine_not_completed"
        pointers:
          - accept_quest
          - decline_quest
    player_options:
      accept_quest:
        text: "<white>I will investigate the mine."
        events: "start_forgotten_mine"
        pointers:
          - quest_accepted
```

Conversation fields:

| Key | Type | Default | Explanation |
|---|---|---|---|
| `quester` | String (MiniMessage) | conversation ID | Name shown above each NPC line. |
| `stop` | Boolean | `false` | Teleport the player back if they walk more than 1 block away. |
| `first` | String / list | `"start"` | Ordered NPC node IDs; the first whose conditions pass opens the conversation. |
| `final_events` | String / list | *(none)* | Events fired whenever the conversation ends for any reason. |
| `NPC_options` | map | *(none)* | NPC speech nodes. |
| `player_options` | map | *(none)* | Player reply nodes. |

Node fields (`NPC_options` / `player_options` entries):

| Key | Type | Notes |
|---|---|---|
| `text` | String (MiniMessage) | The spoken line / reply label. |
| `conditions` | String / list | **Named conditions only** (references the file's `conditions:` block); inline condition DSL is rejected with a warning. |
| `events` | String / list | Named-event refs or inline DSL, fired when the node shows. |
| `pointers` | String list | Next nodes. NPC nodes point to `player_options` keys (auto-prefixed `player.`); player nodes point to `NPC_options` keys. An empty/no pointer ends the conversation. |

### `quest.yml` — `npc_conversations:` binding

```yaml
npc_conversations:
  thorin: thorin_main   # bind NPC 'thorin' to conversation 'thorin_main'
```

Applied when the quest package loads — it calls `withConversation` on the NPC definition, **overriding** any `conversation:` key set on the NPC itself.

### `config.yml` — skin file server

Used only for the `file` skin source (`/npc skin <id> file <filename>`):

```yaml
npc-skin-server:
  enabled: false
  port: 2525
  # host: ""   # Leave blank to auto-detect
```

When enabled, the plugin starts a small HTTP server (JDK built-in, port 2525 by default) that serves PNGs from `plugins/Valmora/skins/` under `/skins/`. Blank host auto-detects the server IP. Place your PNG files in `plugins/Valmora/skins/` and reference them by filename.

### Quick reference of defaults

| Field | Default |
|---|---|
| `display-name` | `<white>` + NPC ID |
| `entity-type` | `VILLAGER` |
| `world` | `world` |
| `x` / `y` / `z` | `0` / `64` / `0` |
| `yaw` | `0` |
| `on-right-click` / `on-left-click` | empty |
| `conversation` | none |
| `look-at-player` | `false` |
| `show-name` | `true` |
| `skin-texture` / `skin-signature` | none |
| `holograms` | empty |
| hologram `vector` / `check_interval` | `0,0,0` / `60` |
| conversation `quester` / `stop` / `first` | ID / `false` / `"start"` |
