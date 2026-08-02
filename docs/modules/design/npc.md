# NPC Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `npc` | **Source:** `src/main/java/org/nakii/valmora/module/npc/`

---

## Table of Contents

1. [Overview](#overview)
2. [Code Structure](#code-structure)
3. [Architecture & Key Classes](#architecture--key-classes)
4. [Configuration (YAML)](#configuration-yaml)
5. [Data Model / Persistence](#data-model--persistence)
6. [API Exposed](#api-exposed)
7. [Dependencies & Consumers](#dependencies--consumers)
8. [Unfinished Things / TODOs](#unfinished-things--todos)
9. [Possible Improvements / Changes](#possible-improvements--changes)

---

## Overview

The NPC module is the **custom NPC engine**. An NPC is a YAML definition in `plugins/Valmora/npcs/*.yml` that describes an entity type, a spawn position, a display name, click actions, an optional skin (for `MANNEQUIN`-type NPCs), an optional bound **conversation**, and conditional floating-text **holograms**. Interacting with an NPC fires **script events** (via the shared `open_gui`/`quest_board_assign`/`dialogue` DSL) or opens a full **dialogue tree**.

The module also contains the entire **dialogue system**: a graph of NPC/player nodes rendered as in-chat messages with clickable choices, keyboard navigation, and NPC-to-NPC auto-advance. Rendering and input capture are done through **PacketEvents** packet interception — a client-side fake ArmorStand mount that translates `PLAYER_INPUT`/`STEER_VEHICLE` packets into dialogue navigation (see [3.13](#313-conversation-packet-rendering--conversationpacketmanagerjava)).

Important structural notes:

- **`NpcModule` is the `ReloadableModule`** (`NpcModule.java:13`), ID `"npc"` (`NpcModule.java:82`), display name `"NPC System"` (`NpcModule.java:83`).
- The module is **19 Java files (~2,800 lines)** spread across four sub-packages: `npc/`, `npc/event/`, `npc/dialogue/`, and `npc/dialogue/intercept/`.
- **Conversations are not loaded from `dialogues/*.yml`.** Despite `saveAllResources` listing the `dialogues/` folder (`Valmora.java:473`) and `docs/USER_DOCS.md` §15 documenting a `dialogues/*.yml` schema, **no such loader exists**. Dialogue definitions come exclusively from **quest packages' `conversations:` sections** (`QuestPackageManager.parseConversations`, `QuestPackageManager.java:267-276`), which are registered into the dialogue registry at apply time (`QuestPackageManager.java:582-583`).
- The `NpcType` enum (`NpcType.java:3-5`) is **dead code** — parsed nowhere and used by nothing.

Design decisions:

- **Definitions + live entity maps, not a data-driven runtime.** `NpcManager` keeps the registry of `NpcDefinition`s plus five live maps keyed by NPC ID / entity UUID / hologram name (`NpcManager.java:39-45`).
- **Entity identity via PDC.** Every spawned NPC, its name tag, and its holograms carry `Keys.NPC_ID_KEY` = `valmora:valmora_npc_id` (`Keys.java:59`, written at `NpcManager.java:115`). `cleanupStaleEntities()` removes any pre-existing tagged entity on load — this is what makes reload safe.
- **Skin support only for `MANNEQUIN`.** Player-like NPCs use the `Mannequin` entity + a `ResolvableProfile` built from a Mojang texture value/signature pair (`NpcManager.java:127-165`). `NpcCommand.cmdSkin` rejects skins on any other entity type (`NpcCommand.java:355-357`).
- **Interact → script events.** Right/left click actions are plain script DSL strings executed through a `SimpleExecutionContext` (`NpcManager.java:346-368`). The shared script event system (already registered by other modules) provides `open_gui`, `quest_board_assign`, `sound`, `quest_start`, etc.
- **Packet-based dialogue rendering.** The dialogue screen is a chat-scrollback emulation (clear + re-send lines), not the Paper 1.21.6+ `Dialog` API. See [3.13](#313-conversation-packet-rendering--conversationpacketmanagerjava) for the details and §8 for the mismatch with `docs/DIALOG_API.md`.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/npc/
├── NpcModule.java                  # ReloadableModule lifecycle (id "npc") + wiring
├── NpcManager.java                 # Spawning, despawn, holograms, look/respawn tasks, interact handlers
├── NpcLoader.java                  # YamlLoader wrapper — parses plugins/Valmora/npcs/*.yml
├── NpcDefinition.java              # Immutable NPC definition + copy-with helpers
├── HologramDefinition.java         # Immutable hologram line definition
├── NpcType.java                    # Enum (SHOP/DIALOGUE/QUEST/WARP/BANK/SLAYER) — UNUSED dead code
├── NpcListener.java                # Right/left-click + damage cancellation via PDC tag
├── NpcCommand.java                 # /npc admin command (TabExecutor, wired in Valmora.java)
├── SkinResolver.java               # Mojang + mineskin.org skin fetching (async)
├── SkinFileServer.java             # Minimal HTTP server serving plugins/Valmora/skins/*.png
├── event/
│   ├── NpcInteractEvent.java       # Custom Bukkit event (player + NpcDefinition) fired on right-click
│   ├── GuiOpenEventFactory.java    # Script DSL: "gui open <gui-id>"
│   └── DialogueEventFactory.java   # Script DSL: "dialogue start <dialogue-id>"
├── dialogue/
│   ├── DialogueManager.java        # Session lifecycle, node rendering, chat input, auto-advance
│   ├── DialogueDefinition.java     # Conversation: quester, first options, stop, final actions, nodes
│   ├── DialogueNode.java           # NPC or PLAYER node (text, events, conditions, choices)
│   ├── DialogueChoice.java         # A pointer/edge inside a node
│   └── DialogueSession.java        # Per-player mutable conversation state
└── dialogue/intercept/
    └── ConversationPacketManager.java  # PacketEvents interception: fake mount + input → navigation
```

Tests (`src/test/java/org/nakii/valmora/module/npc/`): **none exist.**

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `NpcModule.java`

| Method | Behavior | Lines |
|---|---|---|
| Constructor | Stores the plugin reference only — all state is initialized in `onEnable()` | `NpcModule.java:23-25` |
| `onEnable()` | Registers the two script event factories, builds `DialogueManager`, `ConversationPacketManager`, `NpcManager`, runs `NpcLoader`, registers listeners, defers spawning to first tick, starts respawn/look tasks, starts the optional skin server | `NpcModule.java:28-69` |
| `onDisable()` | Unregisters the packet listener, stops respawn/look tasks, despawns everything, unregisters both listeners, stops the skin server, clears the NPC registry | `NpcModule.java:72-80` |
| `getId()` | `"npc"` | `NpcModule.java:82` |
| `getName()` | `"NPC System"` | `NpcModule.java:83` |
| Accessors | `getNpcManager()`, `getDialogueManager()`, `getSkinFileServer()`, `getNpcRegistry()` | `NpcModule.java:85-88` |

**`onEnable()` sequence in detail:**

1. Registers `DialogueEventFactory` (DSL `dialogue start`) and `GuiOpenEventFactory` (DSL `gui open`) with the script module (`NpcModule.java:30-31`).
2. Creates the `DialogueManager`, then the `ConversationPacketManager`, registers the packet listener, and hands it to the dialogue manager (`NpcModule.java:32-35`).
3. Creates the `NpcManager` (`NpcModule.java:36`).
4. Runs `new NpcLoader(...).load()` to parse `npcs/*.yml` into the registry (`NpcModule.java:38`).
5. Registers the `NpcListener` (interaction/damage) and the `DialogueManager` (chat/quit) as event listeners (`NpcModule.java:39-41`).
6. **Defers `spawnAll()` to the first server tick.** The comment documents why: calling `world.spawn()` during `onEnable()` (pre-first-tick) silently drops entities on Paper (`NpcModule.java:43-46`).
7. Starts the respawn task (every 1200 ticks) and the look-at-player task (every 5 ticks) (`NpcModule.java:47-48`).
8. Optionally starts the `SkinFileServer` from config: `npc-skin-server.enabled` / `.port` / `.host`; the host auto-resolves to the server IP, the box IP, or `127.0.0.1` when blank/`0.0.0.0` (`NpcModule.java:50-68`). Failure only logs a warning and nulls the server.

Hot-reload safety: everything is torn down in `onDisable()` and rebuilt in `onEnable()`. `NpcManager.despawnAll()` removes every NPC entity, name tag, and hologram and cancels all hologram tasks (`NpcManager.java:65-76`); `cleanupStaleEntities()` double-removes any stragglers by PDC (`NpcManager.java:83-90`).

### 3.2 Definition Model — `NpcDefinition.java`

Immutable value object with one canonical constructor (`NpcDefinition.java:34-55`) and four legacy convenience constructors (`NpcDefinition.java:59-90`). `sourceFile` nulls to `DEFAULT_SOURCE` = `"npcs/from_command.yml"` (`NpcDefinition.java:30`, `:49`); `holograms` is defensively `List.copyOf`'d (`NpcDefinition.java:54`).

| Field | Type | Default | Accessor | Lines |
|---|---|---|---|---|
| `id` | `String` | (ctor arg) | `getId()` | `NpcDefinition.java:8`, `:94` |
| `displayName` | `String` (MiniMessage) | (ctor arg) | `getDisplayName()` | `NpcDefinition.java:9`, `:95` |
| `entityType` | `EntityType` | (ctor arg) | `getEntityType()` | `NpcDefinition.java:10`, `:96` |
| `worldName` | `String` | (ctor arg) | `getWorldName()` | `NpcDefinition.java:11`, `:97` |
| `x`,`y`,`z` | `double` | (ctor arg) | `getX/getY/getZ()` | `NpcDefinition.java:12-13`, `:98-100` |
| `yaw` | `float` | (ctor arg) | `getYaw()` | `NpcDefinition.java:12-13`, `:101` |
| `onRightClick` | `List<String>` | (ctor arg) | `getOnRightClick()` | `NpcDefinition.java:14`, `:102` |
| `onLeftClick` | `List<String>` | (ctor arg) | `getOnLeftClick()` | `NpcDefinition.java:15`, `:103` |
| `boundConversationId` | `String` | `null` | `getBoundConversationId()` | `NpcDefinition.java:16`, `:104` |
| `sourceFile` | `String` | `npcs/from_command.yml` | `getSourceFile()` | `NpcDefinition.java:18`, `:105` |
| `skinTexture` | `String` (base64) | `null` | `getSkinTexture()` | `NpcDefinition.java:20`, `:106` |
| `skinSignature` | `String` | `null` | `getSkinSignature()` | `NpcDefinition.java:22`, `:107` |
| `lookAtPlayer` | `boolean` | `false` | `isLookAtPlayer()` | `NpcDefinition.java:24`, `:108` |
| `showName` | `boolean` | `true` | `isShowName()` | `NpcDefinition.java:26`, `:109` |
| `holograms` | `List<HologramDefinition>` | empty | `getHolograms()` | `NpcDefinition.java:28`, `:110` |

Copy-with helpers (`NpcDefinition.java:114-172`): `withDisplayName`, `withEntityType`, `withPosition`, `withYaw`, `withConversation`, `withSourceFile`, `withSkin`, `withLookAtPlayer`, `withShowName`, `withHolograms`. These are what the `/npc` command uses to build a *new* definition and call `updateAndRespawn`.

### 3.3 Loading — `NpcLoader.java`

```java
npcRegistry.clear();
dialogueRegistry.clear();
new YamlLoader<NpcDefinition>(plugin, "npcs", "NPCs")
        .load(this::parseNpc, def -> npcRegistry.register(def.getId(), def));
```

(`NpcLoader.java:27-32`.) The standard `YamlLoader` flow scans every `*.yml` in `plugins/Valmora/npcs/`, treats each **top-level key as the NPC ID**, parses each section, and registers successes. Note the loader also clears the **dialogue** registry (`NpcLoader.java:29`) — though since conversations come from quest packages (applied later in the load order), this is harmless.

`parseNpc` defaults:

| Key | Default | Line |
|---|---|---|
| `display-name` | `"<white>" + id` | `NpcLoader.java:38` |
| `entity-type` | `VILLAGER` (invalid values silently fall back) | `NpcLoader.java:40-41` |
| `world` | `"world"` | `NpcLoader.java:52` |
| `x`,`y`,`z` | `0`, `64`, `0` | `NpcLoader.java:53` |
| `yaw` | `0` | `NpcLoader.java:54` |
| `on-right-click` / `on-left-click` | empty list | `NpcLoader.java:55-56` |
| `look-at-player` | `false` | `NpcLoader.java:46` |
| `show-name` | `true` | `NpcLoader.java:47` |
| `conversation` | `null` | `NpcLoader.java:42` |
| `skin-texture` / `skin-signature` | `null` | `NpcLoader.java:44-45` |
| `holograms` | empty list | `NpcLoader.java:48` |

**Special case:** a top-level key literally named `npc_conversations` is skipped (`NpcLoader.java:35-36`) — that key now lives in quest-package `quest.yml` files and is handled by `QuestPackageManager` (`QuestPackageManager.java:113-121`).

`parseHolograms` (`NpcLoader.java:71-91`) reads a YAML `getMapList("holograms")`; each entry's `check_interval` defaults to `60` (`NpcLoader.java:85`), missing `vector` coordinates default to `0` (`NpcLoader.java:79-82`), and malformed entries are silently skipped.

### 3.4 Spawning & Rendering — `NpcManager.java`

**Live maps** (`NpcManager.java:39-45`):

| Map | Key → Value |
|---|---|
| `npcEntityMap` | NPC ID → spawned entity UUID |
| `entityNpcMap` | entity UUID → NPC ID |
| `npcTagMap` | NPC ID → name-tag `TextDisplay` UUID |
| `holoEntityMap` | NPC ID → (hologram name → `TextDisplay` UUID) |
| `holoTasks` | `"npcId:hologramName"` → repeating check `BukkitTask` |

**`spawnNpc(def)`** (`NpcManager.java:92-125`): looks up the world (warns + aborts if unloaded), builds the `Location` with pitch `0`. For `MANNEQUIN` entity type it delegates to `spawnMannequin`; otherwise:

- `world.spawnEntity(loc, type)` must yield a `LivingEntity` or the entity is removed (`NpcManager.java:106-109`).
- Flags: `setAI(false)`, `setInvulnerable(true)`, `setSilent(true)`, `setRemoveWhenFarAway(false)`, `setPersistent(false)` (`NpcManager.java:110-114`).
- PDC `NPC_ID_KEY` written with the definition ID (`NpcManager.java:115`).
- If `showName`, a name-tag `TextDisplay` is spawned at `y + entity.getHeight() + 0.3` (`NpcManager.java:120-122`, `spawnNameTag` at `:167-178`).

**`spawnMannequin(def, world, loc)`** (`NpcManager.java:127-165`) uses the Paper spawn-consumer form with an `Immutable`/`invulnerable`/`silent` mannequin, `SkinParts.allParts()`, no custom name, and PDC. When `skinTexture` is present it builds a `ResolvableProfile`:

- profile name = NPC ID truncated to 16 chars (`NpcManager.java:142`);
- deterministic UUID from `UUID.nameUUIDFromBytes(("npc:" + id))` (`NpcManager.java:143`);
- a `ProfileProperty("textures", texture, signature)` (`NpcManager.java:144`) added to a `ResolvableProfile.resolvableProfile()` builder (`NpcManager.java:145-150`).

**Holograms** (`NpcManager.java:180-255`): `HOLO_ORIGIN_Y = 2.0` above the NPC's feet (`NpcManager.java:183`). Each hologram parses its condition list into a `ConditionGroup` once (`NpcManager.java:188`), applies visibility immediately, and schedules a repeating task at `checkInterval` ticks (`NpcManager.java:185-205`). `applyHologramVisibility` evaluates the conditions with `new SimpleExecutionContext(null, npcLoc, null)` (`NpcManager.java:211`) and spawns/removes the `TextDisplay` at `def + (offsetX, HOLO_ORIGIN_Y + offsetY, offsetZ)` with `Billboard.CENTER`, no background, and PDC tag (`NpcManager.java:220-237`).

**Background tasks:**

- `startRespawnTask` — every **1200 ticks** (60 s), `checkRespawn` re-spawns any NPC whose entity is gone (also cleans the tag/holograms first) (`NpcManager.java:298-342`).
- `startLookTask` — every **5 ticks**, for definitions with `lookAtPlayer`, finds the nearest player within `LOOK_RANGE = 10.0` blocks and `npc.lookAt(nearest.getEyeLocation(), LookAnchor.EYES)` (`NpcManager.java:307-324`).

**Public spawn/despawn helpers** (`NpcManager.java:257-294`): `despawnNpc(id)` removes entity + tag + holograms without touching the registry; `removeNpc(id)` also unregisters; `updateAndRespawn(def)` despawns, re-registers, and re-spawns (returns spawn success); `getSpawnedLocation(id)` returns the live entity location or `null`.

**Interact handlers** (`NpcManager.java:344-368`):

- `handleRightClick(player, npcId)`: fires the `NpcInteractEvent` (so quests can listen — see §7), then **if a conversation is bound** starts it and returns; otherwise executes the `onRightClick` script action list via `getEventParser().parseList(...).execute(ctx)` with a `SimpleExecutionContext(player, player.getLocation(), null)` (`NpcManager.java:346-359`).
- `handleLeftClick(player, npcId)`: executes the `onLeftClick` action list only (`NpcManager.java:361-368`).

### 3.5 Interaction & Damage Guard — `NpcListener.java`

- `onRightClick(PlayerInteractEntityEvent)` — **HAND slot only** (avoids the double-fire off-hand bug, `NpcListener.java:24`); reads the PDC tag, cancels the event, and dispatches (`NpcListener.java:22-30`).
- `onLeftClick(EntityDamageByEntityEvent)` — **`LOWEST` priority, `ignoreCancelled=false`**, so the damage is cancelled **before** the combat module ever sees it; only player damagers dispatch `handleLeftClick` (`NpcListener.java:33-41`).
- `onEntityDamage(EntityDamageEvent)` — **`LOWEST`**, cancels **all** non-player damage sources (fire, explosions, fall) to tagged NPCs (`NpcListener.java:44-50`).

### 3.6 Command — `NpcCommand.java`

`TabExecutor` for `/npc`, instantiated in `Valmora.onEnable()` (`Valmora.java:229-231`). Requires `valmora.admin` (`NpcCommand.java:48`, also plugin.yml:55). Subcommand list (`NpcCommand.java:32-36`), dispatched in `onCommand` (`NpcCommand.java:63-81`):

| Subcommand | Behavior | Lines |
|---|---|---|
| `create <id> <entity_type>` | Player-only; validates spawnable `LivingEntity` type; builds def at the player's location (rounded to 2 dp) with default name `<white>` + id (underscores → spaces) and `DEFAULT_SOURCE`; registers + spawns + saves | `NpcCommand.java:87-121` |
| `delete <id>` | `removeNpc` + removes the entry from its source file; warns if the def came from a read-only bundled file | `NpcCommand.java:123-141` |
| `list` | Prints all registered NPCs with live status `●`/`●`, clickable to `/npc info` | `NpcCommand.java:143-160` |
| `info <id>` | Detailed def dump (name, type, world, position, status, conversation, action counts, source file) | `NpcCommand.java:162-190` |
| `tp <id>` | Player-only; `teleportAsync` to the NPC's defined position | `NpcCommand.java:192-211` |
| `move <id>` | Player-only; `withPosition` to the player's location, `updateAndRespawn`, saves | `NpcCommand.java:213-231` |
| `rename <id> <name...>` | `withDisplayName`, `updateAndRespawn`, saves | `NpcCommand.java:233-250` |
| `settype <id> <entity_type>` | `withEntityType`, `updateAndRespawn`, saves | `NpcCommand.java:252-274` |
| `setyaw <id> [yaw]` | `withYaw` (defaults to the sender's yaw when player), `updateAndRespawn`, saves | `NpcCommand.java:276-306` |
| `conversation <id> <dialogue_id>` | `withConversation`, `updateAndRespawn`, saves; tab-completes registered dialogue IDs | `NpcCommand.java:308-325`, tab at `:630-636` |
| `clearconv <id>` | `withConversation(null)`, `updateAndRespawn`, saves | `NpcCommand.java:327-343` |
| `skin <id> player\|url\|file\|reset [value]` | Player-only; **MANNEQUIN-only**; `player` → Mojang fetch, `url` → mineskin.org, `file` → local PNG served by `SkinFileServer`, `reset` → clears skin | `NpcCommand.java:345-436` |
| `near [radius]` | Player-only; lists NPCs within a radius (default 32) using their **defined** coordinates | `NpcCommand.java:438-475` |
| `look <id>` | Toggles `lookAtPlayer` in the registry and saves (**note: does not respawn** — takes effect on next respawn/reload) | `NpcCommand.java:477-493` |
| `showname <id>` | Toggles `showName`, `updateAndRespawn`, saves | `NpcCommand.java:495-512` |
| `reload` | `plugin.getModuleManager().reloadModules()` — reloads **all** modules | `NpcCommand.java:514-518` |

**Persistence helpers** (`NpcCommand.java:520-600`): `saveNpc(def, overwrite)` writes the full definition into `def.getSourceFile()` under the plugin data folder, keyed by NPC ID (creating `npcs/from_command.yml` for command-created NPCs); `overwrite=false` skips if the key already exists. `deleteFromFile(def)` removes the key and returns whether it existed in the file.

**Type validation** (`NpcCommand.java:706-726`): `isSpawnableNpcType` rejects `PLAYER` and anything whose entity class is not a `LivingEntity`. Tab completion (`NpcCommand.java:604-677`) covers subcommands → NPC IDs → entity types / dialogue IDs / skin targets / online players / skin files.

### 3.7 Skin Fetching — `SkinResolver.java`

Static utility using `java.net.http.HttpClient` (10 s connect timeout, `SkinResolver.java:27-29`). All network I/O runs via `runTaskAsynchronously`; callbacks are marshalled back to the main thread via `mainThread()` (`SkinResolver.java:133-135`).

- `fetch(playerName, plugin, callback)` — two Mojang calls: `api.mojang.com/users/profiles/minecraft/<name>` → UUID, then `sessionserver.mojang.com/session/minecraft/profile/<uuid>?unsigned=false` → textures property (value + optional signature) (`SkinResolver.java:42-76`).
- `fetchFromUrl(imageUrl, plugin, callback)` — POST `{"url":...,"variant":"classic"}` to `api.mineskin.org/generate/url` (30 s timeout); handles 429 rate-limit and non-200 responses; extracts `data.texture.value` + `.signature` (`SkinResolver.java:84-116`).
- `get(url)` treats 204/404 as "not found" (`SkinResolver.java:120-131`).
- `Callback` interface: `onSuccess(texture, signature)` / `onFailure(reason)` (`SkinResolver.java:22-25`).

### 3.8 Skin File Server — `SkinFileServer.java`

Minimal `com.sun.net.httpserver.HttpServer` (JDK built-in, no dependency) serving PNGs from `plugins/Valmora/skins/` under `/skins/` (`SkinFileServer.java:25-30`). Security notes:

- Only `GET` is allowed (405 otherwise) (`SkinFileServer.java:37-40`).
- **Path traversal blocked** by canonical-path prefix check (`SkinFileServer.java:45-50`).
- 404 for missing/non-file paths; `image/png` content type; default executor (thread-per-request) (`SkinFileServer.java:51-67`).

Lifecycle is owned by `NpcModule` (`start`/`stop` at `SkinFileServer.java:32-76`). `urlFor(filename)` builds `http://host:port/skins/<file>` (`SkinFileServer.java:83-85`). Enabled via `config.yml` → `npc-skin-server.enabled` (see §4).

### 3.9 Script DSL — `event/` factories

| Factory | DSL | Behavior | Lines |
|---|---|---|---|
| `DialogueEventFactory` | `dialogue start <dialogue-id>` | `plugin.getDialogueManager().startDialogue(player, dialogueId)` (null-guarded) | `DialogueEventFactory.java:24-31` |
| `GuiOpenEventFactory` | `gui open <gui-id>` | `plugin.getGuiModule().openGui(player, guiId, new HashMap<>())` | `GuiOpenEventFactory.java:25-30` |

> **DSL naming trap:** the **npc** module registers `gui open` (name `"gui"`, `GuiOpenEventFactory.java:22`) while the **gui** module registers `open_gui <gui-id> [key=value...]` (name `"open_gui"`, `OpenGuiEventFactory.java:18`, with expression-evaluated props, `OpenGuiEventFactory.java:34-42`). Both exist side by side in the script event registry. The shipped NPC uses `open_gui` with props-free invocation (`shardworks_npcs.yml:17`).

Both factories are registered in `NpcModule.onEnable()` (`NpcModule.java:30-31`), so `dialogue start` and `gui open` are available to **any** script action list plugin-wide.

### 3.10 The `NpcInteractEvent` — `event/NpcInteractEvent.java`

A plain Bukkit `Event` with a static `HandlerList`, carrying `Player` + `NpcDefinition` (`NpcInteractEvent.java:8-23`). Fired by `NpcManager.handleRightClick` (`NpcManager.java:349`). The quest module listens for it to complete `talk_to_npc` objectives (`QuestListener.java:116-120`). No async flag — main-thread only.

### 3.11 Dialogue Data Model — `dialogue/`

**`DialogueDefinition`** (`DialogueDefinition.java:7-45`): `id`, `questerName` (visual NPC name, MiniMessage), `firstOptions` (ordered list of NPC node IDs tried at start — first whose conditions pass wins), `startNodeId` (legacy single-start fallback), `stop` (teleport the player back if they walk away), `finalActions` (fired whenever the conversation ends for any reason), `nodes` (`Map<String, DialogueNode>`). Two constructors: short (`questerName = id`, no first options, `DialogueDefinition.java:21-23`) and full.

**`DialogueNode`** (`DialogueNode.java:5-42`): `NodeType { NPC, PLAYER }` enum (`DialogueNode.java:6`); fields `id`, `text`, `events`, `conditions` (condition DSL strings), `choices` (`List<DialogueChoice>`); three constructors, defaulting to `NodeType.NPC`; `isPlayerNode()` at `DialogueNode.java:41`.

**`DialogueChoice`** (`DialogueChoice.java:5-27`): `text`, `nextNodeId`, `events`, `conditions` (conditions required for the choice to be shown). The parser creates `__ptr__`-texted choices (see below); the runtime re-creates displayed choices from player-node text (`DialogueManager.java:291`).

**`DialogueSession`** (`DialogueSession.java:7-55`): per-player mutable state — `playerUuid`, `dialogue`, `currentNodeId`, `displayedChoices`, `highlightedChoice` (0-based, wraps with `Math.floorMod`, `DialogueSession.java:39-42`), `npcAutoAdvanceTaskId`/`pendingNpcNodeId` for NPC-to-NPC auto-advance.

### 3.12 Conversation Lifecycle — `DialogueManager.java`

Implements `Listener` (chat + quit). Key constants: `TICKS_PER_CHAR = 3`, `MIN_AUTO_ADVANCE_TICKS = 40`, `MAX_AUTO_ADVANCE_TICKS = 200` (`DialogueManager.java:34-36`). State maps: `dialogueRegistry`, `activeSessions` (UUID → session), `stopTasks`, `actionBarTasks` (`DialogueManager.java:39-42`).

| Method | Behavior | Lines |
|---|---|---|
| `startDialogue(player, id)` | Resolves the def (falls back to `"Dialogue not found"`), ends any prior session, creates a session, picks the start node, starts packet interception, starts the stop-teleport task if `stop`, clears chat, renders the first node | `DialogueManager.java:90-110` |
| `onChat(AsyncChatEvent)` | `LOWEST` priority; if the player has a session, cancels the event, parses the message as an integer (1-based), and runs `handleChoice` on the main thread | `DialogueManager.java:71-84` |
| `handleChoice(player, index)` | Executes the choice's events, resolves the target node (incl. cross-conversation), and renders it; empty/`null`/`"null"` target ends the session | `DialogueManager.java:116-140` |
| `skipAutoAdvance(player, session)` | Cancels a pending NPC-to-NPC advance and jumps to the pending node (triggered by Space) | `DialogueManager.java:146-155` |
| `clearSession` | Ends with final events (Player or UUID overloads) | `DialogueManager.java:157-165` |
| `onPlayerQuit` | Ends the session and stops packet interception | `DialogueManager.java:62-69` |
| `getSession(uuid)` | Lookup for the active session (used by `ActionBarUI` to suppress the action bar) | `DialogueManager.java:56` |

**`showNode(player, session)`** (`DialogueManager.java:171-230`) is the renderer:

1. Node missing or node conditions failing → end session (`DialogueManager.java:172-175`).
2. Executes the node's own events (`DialogueManager.java:177-179`).
3. **Player nodes are transparent**: iterates its `__ptr__` choices, executes their events, jumps to the first pointer target whose conditions pass, recursively (`DialogueManager.java:182-196`).
4. **NPC nodes** send the NPC line (`<gold><bold>QUESTER ▶ text`), then split pointers into **player choices** (`player.*` targets → clickable replies, `getPlayerChoices` at `:282-294`) and **NPC pointers** (NPC→NPC continuation, `getNpcPointers` at `:300-312`):
   - With player choices → `setDisplayedChoices`, `renderChoices`, start the choice-hint action-bar task (`DialogueManager.java:208-211`).
   - With NPC pointers only → schedule auto-advance after `calcAutoAdvanceDelay(text)` ticks (`plain.length × TICKS_PER_CHAR`, clamped 40–200), start the skip-hint action-bar task (`DialogueManager.java:212-226`).
   - With neither → end session (`DialogueManager.java:227-229`).

**`renderChoices`** (`DialogueManager.java:263-276`) renders each choice with a `► [n]`/`[n]` prefix and a `ClickEvent.runCommand("/valmora npc-choice <i>")`. That command is handled by `ValmoraCommand` with **no permission required** (`ValmoraCommand.java:28-37`).

**Start-node resolution** — `pickFirstOption` (`DialogueManager.java:314-323`) walks `firstOptions` in order and returns the first node whose conditions pass; falls back to `startNodeId`.

**Cross-conversation jumps** — `resolvePointer` (`DialogueManager.java:332-347`): if the pointer is not a local node and matches `otherDialogue.nodeId` (a `.` that isn't a `player.` prefix), it starts a fresh session on the referenced conversation. `resolveDialogue` also accepts a `pkg>id` prefix and strips it (`DialogueManager.java:349-355`).

**`clearChatDisplay`** (`DialogueManager.java:361-366`) sends 20 empty chat lines via the packet bypass so the conversation reads cleanly top-down.

**`startStopTask`** (`DialogueManager.java:425-437`): while a `stop: true` conversation is active, if the player moves more than 1 block from the conversation origin, they are `teleportAsync`'d back.

**`endSession`** (`DialogueManager.java:443-464`): the player-facing variant fires `finalActions` (when `runFinalEvents`), stops interception, and clears the action bar; the UUID variant cancels any pending auto-advance/stop/action-bar tasks.

### 3.13 Conversation Packet Rendering — `ConversationPacketManager.java`

Extends `PacketEvents`' `PacketListenerAbstract` at `NORMAL` priority (`ConversationPacketManager.java:49`, `:65-68`). Purpose (from the class javadoc, `ConversationPacketManager.java:37-48`):

> **SEND side** — queues outgoing chat packets so other players' messages don't pollute the conversation; cancels other systems' action-bar packets (the `DialogueManager` sends its own).
> **RECEIVE side** — mounts the player on a **client-side-only fake invisible ArmorStand** so the client sends `PLAYER_INPUT` (and `STEER_VEHICLE` on older protocols). These are cancelled and translated into dialogue navigation.

State (`ConversationPacketManager.java:57-63`): `intercepting` (UUID set), `pending` (queued chat components), `history` (100-entry ring buffer per player, `HISTORY_SIZE = 100` at `:51`), `bypass`, `prevInput` (for rising-edge detection), `mountEntityIds` (client-side fake entity ID per player). `MOUNT_Y_OFFSET = -1.375` (`:53`).

| Method | Behavior | Lines |
|---|---|---|
| `register()` / `unregister()` | Registers/unregisters the listener with `PacketEvents`; `unregister` also dismounts everyone still mounted | `ConversationPacketManager.java:74-90` |
| `startInterception(player)` | Adds the UUID, resets input state, `sendMount` | `ConversationPacketManager.java:96-102` |
| `stopInterception(player)` | Dismounts, then **restores** the pre-conversation chat history (replaying the ring buffer as silent system-chat packets) and flushes any queued messages so the conversation scrolls away cleanly | `ConversationPacketManager.java:104-131` |
| `sendMount(player)` | Sends three packets: spawn fake ArmorStand (`WrapperPlayServerSpawnEntity`, entity id from `Bukkit.getUnsafe().nextEntityId()`), mark invisible (metadata byte `0x20`), and `WrapperPlayServerSetPassengers` to seat the player — this is what makes the client emit input packets | `ConversationPacketManager.java:143-165` |
| `sendDismount(player)` | Sends `WrapperPlayServerDestroyEntities` (auto-dismounts client-side) | `ConversationPacketManager.java:167-173` |
| `sendBypass(player, component)` | Pushes a chat component via `sendPacketSilently` (skips `onPacketSend`), so conversation text always appears immediately regardless of intercept state | `ConversationPacketManager.java:183-198` |
| `sendBypassActionBar(player, component)` | Overlay system-chat packet, bypassing the action-bar block | `ConversationPacketManager.java:201-206` |

**`onPacketSend`** (`ConversationPacketManager.java:213-243`): for intercepted players, `SYSTEM_CHAT_MESSAGE` overlay packets (other systems' action bars) are cancelled; normal chat is recorded in the history ring buffer and queued instead of delivered.

**`onPacketReceive`** (`ConversationPacketManager.java:250-269`): `PLAYER_INPUT` (1.21.3+) or `STEER_VEHICLE` (older protocols) packets are cancelled and fed into `handleInput`.

**`handleInput`** (`ConversationPacketManager.java:275-307`) translates **rising-edge** input signals into navigation (rising-edge prevents holding a key from firing 20×/s):
- **Jump** → if awaiting auto-advance, `skipAutoAdvance`; else `handleChoice(highlightedChoice)` (select).
- **Sneak** → `clearSession` (exit).
- **Forward** → highlight choice `-1` (`refreshHighlight`).
- **Backward** → highlight choice `+1`.

All actions are marshalled to the main thread via Paper's **entity scheduler**: `player.getScheduler().run(...)` (`ConversationPacketManager.java:313-318`), which auto-cancels if the player unloads (AGENTS.md §11.13 pattern).

---

## Configuration (YAML)

### `plugins/Valmora/npcs/*.yml` — NPC definitions

Folder auto-copied from the JAR on first run by `Valmora.saveAllResources()` (`Valmora.java:469-484`). Each top-level key is the NPC ID (case-insensitive at registration, `SimpleRegistry.java:20-21`).

| Key | Type | Required | Default | Notes |
|---|---|---|---|---|
| `<npc-id>` | map key | **yes** | — | Lowercased by the registry. |
| `display-name` | String (MiniMessage) | no | `"<white>" + id` | Floating name tag; also a good placeholder for holograms. |
| `entity-type` | String enum | no | `VILLAGER` | Any Bukkit `EntityType`; `MANNEQUIN` enables skins. |
| `world` | String | no | `"world"` | World name; missing world aborts the spawn with a warning. |
| `x`,`y`,`z` | Double | no | `0`,`64`,`0` | Spawn position. |
| `yaw` | Float | no | `0` | Horizontal facing. |
| `conversation` | String | no | *(none)* | Dialogue ID to start on right-click; overrides `on-right-click`. |
| `on-right-click` | String list | no | empty | Script DSL actions executed on right-click (when no conversation bound). |
| `on-left-click` | String list | no | empty | Script DSL actions executed on left-click. |
| `look-at-player` | Boolean | no | `false` | Face the nearest player within 10 blocks (5-tick task). |
| `show-name` | Boolean | no | `true` | Show the floating name-tag `TextDisplay`. |
| `skin-texture` | String (base64) | no | *(none)* | Mojang texture `value`; only used by `MANNEQUIN`. |
| `skin-signature` | String | no | *(none)* | Mojang texture `signature`. |
| `holograms` | List | no | empty | Conditional floating-text lines (see below). |

`holograms` entry schema (`NpcLoader.java:71-91`):

| Key | Type | Default | Notes |
|---|---|---|---|
| `name` | String | (required) | Unique per NPC; used as the task/map key. |
| `text` | String (MiniMessage) | `""` | The hologram line. |
| `vector` | map `{x,y,z}` | `0,0,0` | Offset from the NPC position + 2.0 Y origin. |
| `conditions` | String list | empty | Condition DSL — hologram visible only while all pass. Evaluated with a **null caster** context. |
| `check_interval` | Integer | `60` | Ticks between condition re-checks (min 1). |

**Shipped example** — `src/main/resources/npcs/shardworks_npcs.yml:5-17`:

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
```

### `config.yml` — skin file server

`src/main/resources/config.yml:115-122`:

```yaml
npc-skin-server:
  enabled: false
  port: 2525
  # host: ""   # Leave blank to auto-detect
```

Read at `NpcModule.java:51-53`; host fallback resolution at `NpcModule.java:54-60`.

### Conversations — defined in quest packages, not `dialogues/*.yml`

Conversation YAML lives inside quest-package files under a `conversations:` section (`QUEST_SYSTEM.md` §10 documents the full authoring guide). The parser is `QuestPackageManager.parseConversationSection` (`QuestPackageManager.java:423-488`). Real shipped examples: `src/main/resources/quests/forgotten_mine/conversations.yml` (`thorin_main`, `bjorn_main`) and `quests/blacksmith_hub/blacksmith.yml` (`blacksmith`).

Top-level fields:

| Key | Type | Default | Notes |
|---|---|---|---|
| `quester` | String (MiniMessage) | conversation ID | Name shown above each NPC line. |
| `stop` | Boolean | `false` | Teleport the player back if they walk > 1 block. |
| `first` | String / list | `"start"` | Ordered NPC node IDs; first whose conditions pass opens the conversation. |
| `final_events` | String / list | *(none)* | Fired whenever the conversation ends for any reason. |
| `NPC_options` | map | *(none)* | NPC speech nodes. |
| `player_options` | map | *(none)* | Player reply nodes. |

Node schema (`NPC_options` / `player_options` entries, `QuestPackageManager.java:441-479`):

| Key | Type | Notes |
|---|---|---|
| `text` | String (MiniMessage) | The spoken line / reply label. |
| `conditions` | String / list | **Named conditions only** — inline DSL is rejected with a warning (`resolveConditionRefs`, `QuestPackageManager.java:528-545`). |
| `events` | String / list | Named-event refs or inline DSL, fired when the node shows. |
| `pointers` | String list | Next nodes. NPC→`player_options` (auto-prefixed `player.`), `player.`→NPC. |

Binding to NPCs: `quest.yml` → `npc_conversations:` (`QuestPackageManager.java:113-121`):

```yaml
npc_conversations:
  thorin: thorin_main
  bjorn:  bjorn_main
```

Applied at `QuestPackageManager.java:592-597` by calling `withConversation` on the matching NPC definition — **overriding** any `conversation:` key set on the NPC itself.

---

## Data Model / Persistence

- **No database usage.** The NPC module never touches `DataStore`.
- **Entity PDC tag written by this module:** `Keys.NPC_ID_KEY` → `valmora:valmora_npc_id` (`Keys.java:59`), written on NPC bodies, name tags, and holograms (`NpcManager.java:115`, `:138`, `:175`, `:230`).
- **Definitions live in YAML.** `plugins/Valmora/npcs/*.yml` (bundled + server-editable, auto-copied once by `saveAllResources`) and `npcs/from_command.yml` for command-created NPCs (`NpcDefinition.DEFAULT_SOURCE`, `NpcDefinition.java:30`). `NpcCommand.saveNpc` rewrites the file on every mutating subcommand (`NpcCommand.java:526-581`); `deleteFromFile` removes the entry (`NpcCommand.java:587-600`).
- **Registry lifecycle:** `npcRegistry` + `dialogueRegistry` are populated in `NpcLoader.load()` (`NpcLoader.java:27-32`), cleared in `NpcModule.onDisable()` (`NpcModule.java:79`). Quest-package conversations are registered later, during `QuestPackageManager.applyToManagers()` (`QuestPackageManager.java:582-583`).
- **Spawned NPCs are not persisted.** They despawn on server stop/reload and are re-spawned by `spawnAll()`; the 60-second respawn task heals chunk-unload / entity-loss (`NpcManager.java:330-342`).
- **Dialogue sessions are ephemeral.** `activeSessions` is a pure in-memory map; a quit always ends the session (`DialogueManager.java:62-69`). Unlike `docs/QUEST_MODULE_OUTLINE.md`'s claim of suspend/resume-on-join, there is no resume logic.

---

## API Exposed

`ValmoraAPI.getNpcManager()` and `getDialogueManager()` (`ValmoraAPI.java:55-57`; implemented `Valmora.java:400-407`) are the public surface. `Valmora.getNpcModule()` (`Valmora.java:419`) exposes the module itself for command/quest wiring.

### `NpcManager` (returned by `ValmoraAPI.getNpcManager()`)

| Method | Signature | Purpose |
|---|---|---|
| `getRegistry` | `Registry<NpcDefinition>` | Case-insensitive definition registry. |
| `getDialogueManager` | `DialogueManager` | Access the dialogue subsystem. |
| `registerAndSpawn` | `void registerAndSpawn(NpcDefinition)` | Add a def and spawn it immediately. |
| `despawnNpc` | `void despawnNpc(String id)` | Remove the entity/tag/holograms, keep the definition. |
| `removeNpc` | `void removeNpc(String id)` | Despawn + unregister. |
| `updateAndRespawn` | `boolean updateAndRespawn(NpcDefinition)` | Replace def + respawn; returns spawn success. |
| `getSpawnedLocation` | `Location getSpawnedLocation(String id)` | Live entity location or `null` (used by `NpcRangeObjectiveHandler`). |
| `handleRightClick` / `handleLeftClick` | `void (Player, String)` | Programmatic interaction dispatch. |
| `startRespawnTask` / `startLookTask` / `stop*` | `void` | Background-task control (used by `NpcModule`). |

### `DialogueManager` (returned by `ValmoraAPI.getDialogueManager()`)

| Method | Signature | Purpose |
|---|---|---|
| `getDialogueRegistry` | `Registry<DialogueDefinition>` | Where quest packages register conversations. |
| `getSession` | `DialogueSession getSession(UUID)` | Active-session lookup (action-bar suppression). |
| `startDialogue` | `void startDialogue(Player, String)` | Begin a conversation. |
| `handleChoice` | `void handleChoice(Player, int)` | Select a displayed choice (used by `/valmora npc-choice`). |
| `clearSession` | `void clearSession(Player` / `UUID)` | Force-end with final events. |
| `skipAutoAdvance` | `void skipAutoAdvance(Player, DialogueSession)` | Skip an NPC monologue. |

The `ReloadableModule` ID `"npc"` is registered at `Valmora.java:208` and reloadable in isolation via `ModuleManager.reloadModule("npc")` (though `/npc reload` calls the full `reloadModules()`).

---

## Dependencies & Consumers

### Load order (why it sits where it does)

Registered after `script`, `time`, `stat`, `player`, `economy`, `ui`, `ability`, `items`, `mobs`, `skills`, `combat`, `gui`, `recipe`, `alchemy`, `enchants`, `zone`, `resource`, `fishing` and **before** `warp` and `quest` (`Valmora.java:188-210`; also documented in `MODULE_DEVELOPMENT.md:514-516` as "depends on gui, script"). It depends on modules loaded before it and is consumed by `quest` and `warp` after it.

### Dependencies (loaded before `npc`)

| Dependency | Why |
|---|---|
| `script` (`ScriptModule`) | `registerEvent` for the two DSL factories (`NpcModule.java:30-31`); `getEventParser`/`getConditionParser`/`getExpressionEvaluator` used for actions, conditions, holograms (`NpcManager.java:188`, `:357`, `:366`; `DialogueManager.java:127`, `:179`, `:328`). |
| `gui` (`GuiModule`) | `openGui` used by the `gui open` DSL (`GuiOpenEventFactory.java:29`). |
| `items`/`ability`/`combat` | Indirect — the DSL action system those modules register (e.g. `open_gui`, `quest_board_assign`) is what NPC `on-right-click` lists invoke. |
| `player` (`PlayerManager`) / `profile` | Indirect via quest objective interactions (`QuestListener`). |
| PacketEvents | External dependency (`plugin.yml:6`). Bootstrapped plugin-wide at `Valmora.java:129`; used only by `ConversationPacketManager`. |

### Consumers (loaded after `npc`)

| Consumer | How it uses the NPC module |
|---|---|
| `quest` (`QuestPackageManager`) | Parses `conversations:` from quest packages and registers `DialogueDefinition`s into the dialogue registry (`QuestPackageManager.java:267-276`, `:582-583`); applies `npc_conversations` bindings to NPC definitions (`QuestPackageManager.java:592-597`). **This is the only producer of dialogue definitions.** |
| `quest` (`QuestListener`) | Listens to `NpcInteractEvent` → triggers `talk_to_npc` objectives with the NPC ID (`QuestListener.java:116-120`; objective type at `QuestObjectiveTypes.java:10`). |
| `quest` (`NpcRangeObjectiveHandler`) | Polls `npcManager.getSpawnedLocation(npcId)` every second to complete `npcrange` objectives (`NpcRangeObjectiveHandler.java:52-110`, lookup at `:77`). |
| `quest` (`QuestBoardEventFactory`) | The `quest_board_assign` DSL used by the shipped NPC's `on-right-click` (`QuestBoardEventFactory.java:28-37`). |
| `gui` (`OpenGuiEventFactory`) | The `open_gui` DSL used by the shipped NPC's `on-right-click` (`OpenGuiEventFactory.java:18`, `:41`). |
| `ui` (`ActionBarUI`) | Suppresses the action bar while a dialogue session is active (`ActionBarUI.java:41-42`) — complementary to the packet-level blocking (`ConversationPacketManager.java:222-225`). |
| command layer | `/npc` executor + tab completer (`Valmora.java:229-231`); `/valmora npc-choice <i>` player-facing handler (`ValmoraCommand.java:28-37`). |
| script | The `dialogue start` / `gui open` DSL is available to every script action list plugin-wide (registered by this module). |

---

## Unfinished Things / TODOs

- **`NpcType` enum is dead code.** `NpcType.java:3-5` defines `SHOP, DIALOGUE, QUEST, WARP, BANK, SLAYER` but is never referenced anywhere in `src/main/java`. `docs/USER_DOCS.md:978` documents a `type:` field for it — the loader has no such key.
- **`dialogues/*.yml` is documented but does not exist.** `Valmora.saveAllResources()` lists the folder (`Valmora.java:473`) and `docs/USER_DOCS.md` §15 documents a `start:`/`nodes:`/`actions:`/`choices:` schema, but `NpcLoader` only loads `npcs/`. Conversations are exclusively quest-package `conversations:` sections. The §15 example schema would silently do nothing.
- **`docs/USER_DOCS.md` §14 NPC schema is stale.** It documents `type:`, `gui:`, and `dialogue:` keys with "gui takes priority" semantics (`USER_DOCS.md:969-1015`). The real schema uses `conversation:` (takes priority over `on-right-click`) and has no `type`/`gui` keys.
- **`docs/QUEST_MODULE_OUTLINE.md` documents removed config.** `QUEST_MODULE_OUTLINE.md:340` and `:364` reference `max_conversation_distance` and `npcs.accept_left_click` in `config.yml`; neither key exists in `config.yml`. `stop` teleports at a hardcoded 1-block threshold (`DialogueManager.java:425-437`) and left-click is always enabled (`NpcListener.java:33-41`).
- **`docs/DIALOG_API.md` overstates Paper Dialog API usage.** It claims "Valmora uses dialogs via `NpcModule` for NPC conversation screens" (`DIALOG_API.md:5`). The implementation uses **PacketEvents packet interception** with a client-side fake mount, not Paper's `Dialog`/`showDialog` API. The DIALOG_API doc is a forward-looking reference, not a description of current code.
- **The `look` subcommand does not respawn.** `NpcCommand.cmdLook` updates the registry and saves but skips `updateAndRespawn` (`NpcCommand.java:487-489`), so the toggle only applies on the next respawn/reload.
- **`saveNpc` loses untouched `on-right-click` formatting on rewrite** — it re-serializes lists via YAML's default formatting; harmless but produces noisy diffs.
- **Hologram conditions evaluate with a `null` caster** (`NpcManager.java:211`), so any condition DSL referencing the caster/player silently fails (and the catch-all turns it into "hidden"). Condition authors must use world/stateless conditions only.
- **No tests.** No `src/test/java/org/nakii/valmora/module/npc/` files exist (unlike `mob` which has two unit-test files). The dialogue graph, condition evaluation, and packet input mapping are all untested.
- **No chat/name-channel isolation beyond chat packets.** `onChat` cancels raw chat while in a conversation (`DialogueManager.java:71-84`), but command output, titles, and other plugins' messages that don't route through `SYSTEM_CHAT_MESSAGE` will still appear.
- **No NPC shops / warp NPCs yet.** `NpcType` implies SHOP/WARP/BANK roles, and `docs/todo.md:27,40,44` lists "npc shops" as unimplemented. All current interaction routes go through script DSL actions only.
- **`respawnTask`/`lookTask` never `null`-safe-stopped twice** — `stopRespawnTask`/`stopLookTask` guard correctly (`NpcManager.java:303-305`, `:326-328`); the pattern is fine but worth noting as the only external control points.

---

## Possible Improvements / Changes

- **Adopt the Paper Dialog API (1.21.6+)** for the conversation screen — `docs/DIALOG_API.md` describes the target. That would let `ConversationPacketManager` (and its fake-mount trick) be deleted in favor of `player.showDialog(...)`, eliminating the packet interception complexity entirely.
- **Use the Paper spawn-consumer** in `spawnNpc` for non-mannequin types (`NpcManager.java:106`) to avoid the one-tick half-initialized entity (AGENTS.md §11.7).
- **Wire up `NpcType`** as a real `type:` field (role-based behaviors/shop integration) or delete it and remove the stale `USER_DOCS` references.
- **Add `look` subcommand respawn** for parity with the other toggles.
- **Load real `dialogues/*.yml`** if standalone (non-quest) conversations are desired, or strip the `dialogues/` folder from `saveAllResources` and the USER_DOCS section to avoid the dead-config trap.
- **Unit tests** for: conversation `first:`/condition resolution, `DialogueSession` highlight wrapping, `calcAutoAdvanceDelay` clamping, and hologram visibility evaluation — following the `ExpressionTest`/`MobDefinitionTest` mock pattern (`docs/MODULE_DEVELOPMENT.md` §9).
- **Configurable stop distance** for `stop: true` conversations instead of the hardcoded 1-block threshold (`DialogueManager.java:425-437`), matching the documented-but-removed `max_conversation_distance`.
- **Persist per-player dialogue progress** (optional resume after quit) if long multi-session conversations are planned.
- **Hologram/name-tag updates via `entity.getScheduler()`** where the task interacts with a specific `TextDisplay`, per AGENTS.md §11.13, instead of the global repeating task.
- **Teleport with `teleportAsync` already used** (`NpcCommand.java:209`, `DialogueManager.java:431`) — good; keep that pattern for any future warp NPC integration.
