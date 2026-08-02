# Core Engine — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Plugin:** Valmora | **Main command:** `/valmora` | **Config:** `config.yml`, `ui.yml`
> **Scope:** Everything the server admin needs about the engine itself — setup, the full command and permission surface, root configuration, and the day-to-day admin workflow. Feature modules have their own guides under `docs/modules/user/<module>.md`, linked from the command table below.

---

## Table of Contents

1. [Overview](#overview)
2. [Plugin Setup](#2-plugin-setup)
3. [Commands Reference](#3-commands-reference)
4. [Permissions Reference](#4-permissions-reference)
5. [Configuration Reference](#5-configuration-reference)
6. [Admin Workflow](#6-admin-workflow)

---

## Overview

Valmora is a modular MMORPG engine for Paper 1.21.x. It is one plugin that boots a collection of subsystems (items, mobs, skills, combat, quests, economy, and more) through a shared module system. From the admin's point of view:

- **Everything reloads together** with one command: `/valmora reload` (requires `valmora.admin`). It runs every module's `onDisable()` in reverse order, then every `onEnable()` in forward order.
- **All content is file-based.** Custom items, mobs, skills, GUIs, zones, NPCs, etc. live in YAML folders under `plugins/Valmora/`. On first run the plugin copies its bundled demo content into those folders and never overwrites your edits afterwards.
- **Player data is stored in a database** — SQLite by default (`plugins/Valmora/database.db`), MySQL optional for multi-server networks.
- **`ui.yml` controls the scoreboard, action bar, and tab list.** Every text field there supports MiniMessage formatting and `$variable$` tokens.

Two permissions gate the whole engine: `valmora.admin` (all admin commands) and `valmora.admin.gui` (GUI debugging). There is no top-level `permissions:` block in `plugin.yml` — permission plugins that need descriptions should be pointed at the command list in §4.

---

## 2. Plugin Setup

### Requirements

| Requirement | Value |
|---|---|
| Server | **Paper 1.21.x** (API 1.21) — not Spigot, not CraftBukkit |
| Java | **21** |
| Plugin dependency | **PacketEvents** (must be installed first — `plugin.yml` declares `depend: [packetevents]`; Valmora will not load without it) |

### Install steps

1. Drop the Valmora jar into `plugins/`.
2. Drop the PacketEvents jar into `plugins/` (Valmora hard-depends on it).
3. Start the server. Valmora creates `plugins/Valmora/` with `config.yml`, `ui.yml`, `database.db`, and the bundled content folders (`items/`, `mobs/`, `skills/`, `guis/`, …).
4. (Optional) Set up MySQL — see §5.
5. Give your admins `valmora.admin` (and `valmora.admin.gui` if they test GUIs).

### Database

| Choice | What it means |
|---|---|
| **SQLite** (default) | Zero setup. Everything in `plugins/Valmora/database.db`. Recommended for single servers. |
| **MySQL** | For networks syncing player data across multiple servers. Set `database.type: mysql` and uncomment/fill the `database.mysql.*` block in `config.yml:17-28`. |

The database is managed automatically — tables are created and migrated on startup (current schema version 2). If the stored schema is *newer* than the plugin, Valmora logs a warning and runs read-only rather than risk data loss.

### First run

The bundled demo content is only extracted **if the target file does not already exist** — server edits to any content folder are safe and survive reloads/restarts. `plugin.yml` and `config.yml` are never overwritten from the jar.

---

## 3. Commands Reference

All commands are registered by the engine after all modules load. Admin commands require `valmora.admin` (marked **Admin**); player commands have no permission gate.

### 3.1 Engine commands

| Command | Permission | Description |
|---|---|---|
| `/valmora reload` | Admin | Reload all modules (disable reverse, enable forward). Sends `<aqua>Reloading Valmora Engine...` then `<green>Valmora Engine reloaded successfully!`. |
| `/valmora variable get <path>` | Admin | Print the value of a script variable (path is auto-wrapped in `$…$`). Tab-completes registered variable keys. |
| `/valmora npc-choice <index>` | none (players) | Internal route used by NPC dialogue GUIs to select a dialogue option. You should never type this manually. |
| `/valmora` | Admin | Prints the command help. |

### 3.2 All registered commands (with feature-module docs)

| Command | Usage summary | Permission | Details |
|---|---|---|---|
| `/profile` | `gui` · `create <name>` · `delete <name>` · `switch <name>` · `list` · `info` | none | Per-player profiles. `info` shows health/mana/in-combat. See `docs/modules/user/profile.md`. |
| `/stat` | `list` · `add <statId> <value>` · `remove <statId> <value>` | none (add/remove need Admin) | `list` shows all stats on your active profile. See `docs/modules/user/stat.md`. |
| `/item` | `give <id> [amount] [player]` · `info [id]` · `list` · `reload` · `enchant <id> <level>` · `enchantbook <id> <level>` | Admin | `reload` only reloads the `items` module. `enchant` applies to the held item. See `docs/modules/user/item.md` (if present). |
| `/mob` | `spawn <mob> [player]` · `list` · `reload` · `info` | Admin | `info` inspects the mob you are looking at (max 10 blocks). `reload` reloads only `mobs`. See `docs/modules/user/mob.md`. |
| `/skill` | `list` · `get <player> <skill>` · `give <player> <skill> <xp>` · `set <player> <skill> <xp\|level> <value>` | none (give/set need Admin) | `set … level` converts the level to the matching XP via the skill's XP curve. See `docs/modules/user/skill.md`. |
| `/gui` | `open <player> <id>` | `valmora.admin.gui` | Debug command — opens any registered GUI for a player. See `docs/modules/user/gui.md` (if present). |
| `/time` | `info` · `reset` | none (reset needs Admin) | Shows season/phase/day/year; `reset` returns the RPG calendar to its stored start. See `docs/modules/user/time.md`. |
| `/eco` | `get \| set \| add \| remove <player> [purse\|bank] [amount]` | Admin | Amounts accept `k/m/b` and arithmetic (`2.5k`, `1k+500`). Target must be **online**. See `docs/modules/user/economy.md`. |
| `/potion` | `give <effect_id> <level> [player]` | Admin | Gives a Valmora alchemy potion (level clamped to the effect's max). See `docs/modules/user/alchemy.md`. |
| `/effects` | (no args) | none | Opens the `active_effects` GUI showing your active alchemy effects. See `docs/modules/user/alchemy.md`. |
| `/warp` | `[id]` | none | No argument opens the `fast_travel` GUI; an ID teleports you straight to that warp. See `docs/modules/user/warp.md`. |
| `/zone` | `wand` · `pos1`/`pos2` · `clear` · `create <id> [name]` · `delete <id>` · `info <id>` · `list` · `flag <id> <flag> <true\|false>` · `spawner <add\|remove\|list>` · `visualize` | Admin | Region flags: `pvp`, `natural-mob-spawning`, `block-breaking`, `block-placing`, `hunger`, `entry`, `teleportation`, `leaf-decay`. See `docs/modules/user/zone.md` (if present). |
| `/quest` | `journal` (default) | none | Opens the quest journal GUI. See `docs/modules/user/quest.md` (if present). |
| `/npc` | `create <id> <entity_type>` · `delete <id>` · `list` · `info <id>` · `tp <id>` · `move <id>` · `rename <id> <name>` · `settype <id> <type>` · `setyaw <id> [yaw]` · `conversation <id> <dialogue_id>` · `clearconv <id>` · `skin <id> <player\|url\|file\|reset> [value]` · `near [radius]` · `look <id>` · `showname <id>` · `reload` | Admin | Skins apply only to `MANNEQUIN` NPCs. `skin file` needs `npc-skin-server.enabled: true`. Note: `/npc reload` reloads **all** modules, not just NPCs. See `docs/modules/user/npc.md` (if present). |
| `/collections` | (no args) | none | Opens the collections menu. See `docs/modules/user/collection.md`. |
| `/accessories` | (no args) | none | Opens your accessory bag. See `docs/modules/user/accessory.md`. |
| `/quiver` | (no args) | none | Opens your 27-slot quiver (arrows only). See `docs/modules/user/quiver.md`. |

---

## 4. Permissions Reference

Valmora declares permissions **inline on each command** in `plugin.yml` (there is no central `permissions:` block). These are the only permission nodes in the plugin:

| Permission | Commands it gates |
|---|---|
| `valmora.admin` | `/valmora`, `/item`, `/mob`, `/potion`, `/eco`, `/zone`, `/npc`, plus the admin subcommands of `/stat` (`add`/`remove`) and `/skill` (`give`/`set`), and `/time reset`. |
| `valmora.admin.gui` | `/gui`. |

Additionally, `ValmoraCommand` re-checks `valmora.admin` in code before any `/valmora` subcommand other than `npc-choice`, so granting the permission in a permissions plugin is sufficient — no per-command nodes exist beyond the two above.

Player-facing commands (`/profile`, `/stat list`, `/skill list/get`, `/effects`, `/warp`, `/zone` — commands are player-only, `/quest`, `/collections`, `/accessories`, `/quiver`) require no permission.

---

## 5. Configuration Reference

### 5.1 `config.yml` — root configuration (`plugins/Valmora/config.yml`)

| Key | Type | Default | Description |
|---|---|---|---|
| `database.type` | `sqlite` \| `mysql` | `sqlite` | Storage backend. SQLite = local `database.db`; MySQL = multi-server sync. |
| `database.mysql.host` | string | `localhost` | MySQL host (only when `type: mysql`). |
| `database.mysql.port` | number | `3306` | MySQL port. |
| `database.mysql.database` | string | `valmora` | MySQL database name. |
| `database.mysql.username` | string | `root` | MySQL user. |
| `database.mysql.password` | string | *(empty)* | MySQL password. |
| `database.mysql.use-ssl` | boolean | `false` | Enable SSL/TLS for the MySQL connection. |
| `economy.autosave-interval-seconds` | number | `60` | How often dirty coin balances are flushed to the DB in one batched save. Balances are always safe in memory; this only bounds crash-loss. Logouts and clean restarts always save immediately. |
| `profiles.max-profiles` | number | `4` | Maximum profiles per player (UI shows 4). |
| `profiles.default-name` | string | `Earth` | Name of the first/default profile created for new players. |
| `profiles.planet-names` | string list | Mars…Europa | Pool of random names for new profiles (unused only). |
| `time.world` | string | `world` | World whose clock drives the RPG calendar. |
| `time.start-year` | number | `1` | Starting calendar year (first launch; stored in `time.yml` after). |
| `time.start-season` | `SPRING` \| `SUMMER` \| `AUTUMN` \| `WINTER` | `SPRING` | Starting season. |
| `time.start-phase` | `EARLY` \| `MID` \| `LATE` | `EARLY` | Starting phase. |
| `time.start-day` | number | `1` | Starting day (1–30). |
| `time.season-names` | string list | `[Spring, Summer, Autumn, Winter]` | Display names used by scoreboard/variables. |
| `time.phase-names` | string list | `[Early, Mid, Late]` | Display names for phases. |
| `time.scoreboard-enabled` | boolean | `true` | Show the time lines on the sidebar scoreboard. |
| `combat.health-stat` … `combat.luck-stat` | stat IDs | see §5.1a | Maps engine roles to `stats/*.yml` stat IDs. |
| `mining.mining-fortune-stat` etc. | stat IDs | `mining_fortune`, `mining_speed`, `breaking_power`, `mining_spread` | Mining stat role mappings. |
| `npc-skin-server.enabled` | boolean | `false` | Enable the tiny built-in HTTP server that serves PNG skins from `plugins/Valmora/skins/` for `/npc skin <id> file <file.png>`. |
| `npc-skin-server.port` | number | `2525` | HTTP port for the skin server. |
| `npc-skin-server.host` | string | *(blank)* | Leave blank to auto-detect; set to a public IP if clients connect from outside the LAN. |
| `alchemy.splash-radius` | number | `4.0` | Block radius for splash potion area of effect. |
| `alchemy.tick-interval` | number | `20` | Ticks between active-effect expiry checks. |
| `alchemy.max-active-effects` | number | `10` | Max concurrent active effects per player. |

**§5.1a — combat stat-ID defaults** (`config.yml:90-101`): `health-stat: health`, `mana-stat: mana`, `damage-stat: damage`, `strength-stat: strength`, `defense-stat: defense`, `crit-chance-stat: crit_chance`, `crit-damage-stat: crit_damage`, `speed-stat: speed`, `health-regen-stat: health_regen`, `mana-regen-stat: mana_regen`, `luck-stat: luck`. If you rename a core stat in `stats/*.yml`, update these mappings or combat breaks.

### 5.2 `ui.yml` — scoreboard / action bar / tab (`plugins/Valmora/ui.yml`)

All text supports MiniMessage formatting and `$variable$` tokens. Available variables (per the header comment `ui.yml:4-6`): `$player.*$`, `$time.*$`, `$zone.*$`, `$economy.*$`, `$server.*$`, `$world.*$`, `$stat.*$`.

| Key | Default |
|---|---|
| `scoreboard.title` | `<gold><bold>VALMORA RPG` |
| `scoreboard.lines` | 9 entries — a literal `"$dynamic$"` line inserts the current dynamic section (combat lock, dialogue, etc.); empty strings are blank lines. Default includes server name, time (`$time.formatted_time$ $time.color$$time.emote$ $time.phase$ $time.season$`), day/year, profile, zone, purse. |
| `action-bar.default` | `<red>❤ $player.hp$/$player.max_hp$ <dark_gray>\| <green>❈ $player.stat.defense$ Defense <dark_gray>\| <aqua>⛨ $player.mana$/$player.max_mana$ Mana` |
| `tab.header` | `<gold><bold>VALMORA</bold></gold>` |
| `tab.footer` | `<gray>Players online: <white>$server.online$` |

### 5.3 `plugin.yml`

| Key | Value |
|---|---|
| `name` | `Valmora` |
| `version` | `${version}` (set by the build, currently `1.0.0-beta1`) |
| `main` | `org.nakii.valmora.Valmora` |
| `api-version` | `1.21` |
| `authors` | `[nakii]` |
| `depend` | `[packetevents]` |

---

## 6. Admin Workflow

### Reloading the engine

```
/valmora reload
```

Requires `valmora.admin`. Disables every module in reverse registration order, then enables in forward order. This is the standard way to pick up YAML content changes. The economy flushes all balances before reload, so no coins are lost.

**Single-module reloads** also exist for content folders:

- `/item reload` — reloads only the `items` module.
- `/mob reload` — reloads only the `mobs` module.
- `/npc reload` — reloads **all** modules (not just NPCs).

### Day-to-day content workflow

1. **Edit YAML** in `plugins/Valmora/` (items, mobs, skills, guis, zones, npcs, …). Content folders are created from bundled defaults only if missing — your edits are never overwritten.
2. **Reload** with `/valmora reload` (or the single-module reloads above).
3. **Verify** with the info commands — `/item info <id>`, `/mob info` (look at a mob), `/npc info <id>`, `/zone info <id>`.
4. **Distribute** items with `/item give <id> [amount] [player]`, mobs with `/mob spawn <id> [player]`, potions with `/potion give <effect_id> <level> [player]`.

### Setting up zones

1. `/zone wand` — gives the golden-axe selection tool (left-click = Pos1, right-click = Pos2).
2. `/zone pos1` / `/zone pos2` — or set corners at your feet.
3. `/zone create <id> [display-name]` — creates the zone from the selection.
4. `/zone flag <id> <flag> <true|false>` — toggle `pvp`, `natural-mob-spawning`, `block-breaking`, `block-placing`, `hunger`, `entry`, `teleportation`, `leaf-decay`.
5. `/zone spawner add <zoneId> <mobId> [spawnRadius] [maxAlive] [interval]` — spawn mobs inside the zone.
6. `/zone visualize` — toggle the border particle preview.

### Setting up NPCs

1. `/npc create <id> <entity_type>` — spawns at your location.
2. `/npc rename <id> <name>` — set a MiniMessage display name.
3. `/npc conversation <id> <dialogue_id>` — bind a dialogue (requires a matching `dialogues/` file).
4. `/npc settype <id> MANNEQUIN`, then `/npc skin <id> player <name>` / `url <url>` / `file <file.png>` — for custom-skinned humanoid NPCs. `file` needs `npc-skin-server.enabled: true` in `config.yml`.

### Managing the economy

- `/eco get <player>` — see purse + bank (or `purse`/`bank` separately).
- `/eco add <player> purse 1k` / `/eco set <player> bank 5000` / `/eco remove <player> purse 250` — amounts support `k/m/b` and arithmetic like `2.5k`, `1k+500`.
- The target must be **online**.
- Players lose half their purse on death (hardcoded, not configurable); banked coins are safe.

### Switching database backend

1. Stop the server.
2. In `config.yml`: set `database.type: mysql` and fill in the `database.mysql.*` block (host, port, database, username, password, `use-ssl`).
3. Start the server — tables are created/migrated automatically on startup.
4. Back up `plugins/Valmora/database.db` before migrating if you need to preserve existing data.

### Troubleshooting

- **"Failed to enable module: <id>"** appears in the console — that module's `onEnable()` threw. The engine continues with the remaining modules (`ModuleManager.java:35-44`). Fix the referenced YAML, then `/valmora reload`.
- **Plugin disables itself at startup** with *"Database initialization failed — disabling Valmora to avoid data loss."* — schema init failed. Check the database config and file permissions, then restart.
- **Bundled content is missing after an update** — `saveAllResources` never overwrites; if a newer default ships, delete the old file from `plugins/Valmora/` (or edit it manually) and reload.
- **Skin files won't apply** — `MANNEQUIN` NPCs only, and `file` needs the skin server enabled (`config.yml` → `npc-skin-server.enabled: true`).
