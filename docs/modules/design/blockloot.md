# Block Loot Overrides Module — Design & Code

> **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `block_loot` | **Source:** `src/main/java/org/nakii/valmora/module/blockloot/`

---

## Table of Contents

1. [Overview](#overview)
2. [Code Structure](#code-structure)
3. [Architecture & Key Classes](#architecture--key-classes)
4. [Configuration (YAML)](#configuration-yaml)
5. [Precedence & Interaction with Other Modules](#precedence--interaction-with-other-modules)
6. [Dependencies & Consumers](#dependencies--consumers)
7. [Unfinished Things / Possible Improvements](#unfinished-things--possible-improvements)

---

## Overview

`block_loot` is a **global, zone-independent** per-block-type loot override: "every `STONE` block
anywhere drops this table instead of vanilla cobblestone." It exists to close
`VANILLA_CONTROL_AUDIT.md` §1/§4's "generic `BlockDropItemEvent`/`BlockExpEvent` control for
arbitrary blocks" gap — a user-directed content-authoring pass, not itself an audit line item.

It is deliberately **not** the same feature as the `resource` module's zone-scoped
`resource-blocks:` map (`module/zone/ZoneResourceConfig.java`):

| | `block_loot` (this module) | `resource-blocks:` (`resource`/`zone` modules) |
|---|---|---|
| Scope | Global — every block of this material, everywhere | One specific zone only |
| Progression | None — the block breaks once, like vanilla | Multi-stage, with a `next:` material per stage |
| Regeneration | None | Timer-based (`regen-delay`), restores the original block |
| Gating | None | `required-power` (Breaking Power) |
| Config location | `plugins/Valmora/block_loot/*.yml` | Inside a zone's own YAML, under `resource-blocks:` |

Both systems can theoretically target the same `Material`; when they do, the zone-scoped
`resource-blocks:` config **always wins** for any block actually inside that zone (see
[Precedence](#precedence--interaction-with-other-modules)) — `block_loot` only ever applies where
nothing more specific claims the block.

It is a small module: a `ReloadableModule` wrapping a registry + loader + roll/give manager + one
`BlockBreakEvent` listener, closely mirroring the `resource` module's own
`ResourceManager`/`ResourceListener` shape (deliberately — this is the closest existing analog in
the codebase). There is no persistence, no commands, and configuration is purely
YAML-plus-`/valmora reload`, the same as items/mobs/recipes/machines.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/blockloot/
├── BlockLootModule.java      # ReloadableModule — lifecycle (enable/disable/getId)
├── BlockLootRegistry.java    # Material -> BlockLootConfig lookup (EnumMap, not the generic Registry<T>)
├── BlockLootLoader.java      # YAML loader/parser via YamlLoader<BlockLootConfig>
├── BlockLootConfig.java      # Immutable record: material + drop list
├── BlockLootDrop.java        # Immutable record: one drop entry (item/min/max/chance) + rollAmount()
├── BlockLootManager.java     # Silk Touch / Mining Fortune roll-and-give logic
└── BlockLootListener.java    # BlockBreakEvent handler (EventPriority.LOWEST)

src/main/resources/block_loot/
└── example.yml                # Ships fully commented — zero real entries by default
```

Test coverage: `src/test/java/org/nakii/valmora/module/blockloot/` (`BlockLootDropTest`,
`BlockLootConfigTest`, `BlockLootRegistryTest`, `BlockLootLoaderTest`).

---

## Architecture & Key Classes

### `BlockLootRegistry`

A plain `Map<Material, BlockLootConfig>` (`EnumMap`), **not** the generic string-keyed
`Registry<T>` used elsewhere in the codebase — the lookup key here is a `Material` enum, matching
the existing convention set by `ZoneDefinition.getResourceBlocks()` (also a plain
`Map<Material, ZoneResourceConfig>`). `register(Material, BlockLootConfig)` keeps the **first**
config registered for a given material and logs a warning on a collision, rather than silently
letting a later file/id overwrite an earlier one.

### `BlockLootLoader` — YAML shape and the namespacing fix

Uses the standard `YamlLoader<T>(plugin, "block_loot", "Block Loot Overrides").load(...)` pattern
(see `infrastructure/config/YamlLoader.java` and `module/fishing/FishingLoader.java` for the
template this follows). One important deviation from the "obvious" design:

**The YAML top-level key is an arbitrary id — never the material name.** `YamlLoader.load()` runs
every top-level key through the content-pack namespacer
(`YamlLoader.setIdQualifier`/`module/pack/PackNamespacer.qualify`) before the parser ever sees it.
If the top-level key *were* the material (e.g. `STONE:`), a pack-owned `block_loot/*.yml` file
would have its key rewritten to something like `"somepack:STONE"`, and
`Material.matchMaterial("somepack:STONE")` would silently return `null` — breaking every
pack-authored override with no clear error. Instead, the material is a required field **inside**
the section:

```yaml
stone_override:        # arbitrary id — namespaced transparently for pack content, otherwise unused
  material: STONE       # required; Material.matchMaterial, case-insensitive
  drops: [...]
```

A missing or unresolvable `material:` key is a load failure (`LoadResult.failure`), same
convention as every other loader in the codebase (unknown-material warnings, etc.).

### `BlockLootManager.handleBlockBreak(Player, Block) -> boolean`

Mirrors `module/resource/ResourceManager.handleBlockBreak`'s Silk Touch / Mining Fortune logic,
simplified (no stages/regen/required-power/pipeline hooks — this is a one-shot override, not a
mineable node):

1. Look up `registry.get(block.getType())`. `null` → return `false` (not configured; caller
   applies vanilla handling).
2. Silk Touch check: `block-loot.silk-touch.enabled` (config, default `true`) **and** the main-hand
   tool has `Enchantment.SILK_TOUCH`. If true, give exactly 1 of the block's own material —
   vanilla Silk Touch semantics (loot table and Mining Fortune are both ignored, matching
   `resource.silk-touch`'s existing behavior).
3. Otherwise, roll each `BlockLootDrop` **independently** (`Math.random() < chance`), scaling the
   rolled amount by the player's Mining Fortune stat via the shared
   `org.nakii.valmora.util.MiningFortune` utility (extracted from `ResourceManager` in this same
   pass — see below).
4. Each resolved item goes through `ItemManager.createItemStack(itemId)` (custom Valmora item or
   vanilla-material-plus-translate, same public resolve API `ResourceManager` itself should use —
   see the [improvements](#unfinished-things--possible-improvements) note) then
   `ItemManager.giveOrPrivateDrop(player, item, location)`.
5. Returns `true` — the block was configured and fully handled.

### `MiningFortune` utility (`org.nakii.valmora.util.MiningFortune`)

`getPlayerMiningFortune(Player)`/`applyFortune(int, double)` were previously private methods
duplicated nowhere else in the codebase (verified during design) — this module needed the
identical formula, so rather than create a second copy, both methods were extracted into this
small shared utility and `ResourceManager` now delegates to it (its own private methods of the
same name are now one-line delegations, preserving its existing call sites and behavior exactly).

### `ItemManager.giveOrPrivateDrop(Player, ItemStack, Location)`

Also extracted in this pass, from `module/item/LootListener`'s private `processLoot`/
`handleFullInventory` — "add to inventory, else spawn a private glowing pickup with an INVENTORY
FULL title" is now a public, reusable method on `ItemManager` rather than something only
`LootListener` could do. `LootListener.processLoot` now just translates, then delegates to it.
This lets `BlockLootManager` give items safely (no overflow loss) without duplicating ~35 lines —
notably an improvement over `ResourceManager`'s own existing give-path, which still calls a bare
`player.getInventory().addItem(item)` and ignores the leftovers (a pre-existing minor gap, left
untouched in this pass; see [improvements](#unfinished-things--possible-improvements)).

### `BlockLootListener`

```java
@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent event) {
    if (manager.handleBlockBreak(event.getPlayer(), event.getBlock())) {
        event.setDropItems(false);
    }
}
```

Registered at `LOWEST`, exactly mirroring `ResourceListener` — this must run and suppress vanilla
drops *before* `item.LootListener`'s own `HIGH`-priority handler computes `block.getDrops(...)`,
otherwise a configured block would double-drop (the custom item plus the real vanilla one).

### `BlockLootModule`

`onEnable()` reads `block-loot.enabled` (config, default `true`) **once**. If disabled, it skips
both `loader.load()` and listener registration entirely — no YAML parsed, no listener installed,
rather than a per-event branch. `onDisable()` unregisters the listener (if any) and clears the
registry, following the standard `ReloadableModule` contract.

---

## Configuration (YAML)

See `docs/modules/user/blockloot.md` for the full user-facing schema reference. Summary:

```yaml
<arbitrary-id>:
  material: STONE          # required — Material.matchMaterial, case-insensitive
  drops:
    - item: iron_dust      # Valmora item id OR vanilla material name
      min: 1
      max: 1
      chance: 1.0
```

`config.yml`:
```yaml
block-loot:
  enabled: true
  silk-touch:
    enabled: true
```

---

## Precedence & Interaction with Other Modules

`module/item/LootListener.onBlockBreak` (`HIGH` priority) already deferred to the `resource`
module for two cases before this pass (a zone's `resource-blocks:` map, and an in-progress tracked
resource block). This pass adds a third, matching defer check:

```java
BlockLootModule blm = plugin.getBlockLootModule();
if (blm != null && blm.getRegistry() != null && blm.getRegistry().get(block.getType()) != null) return;
```

Net effect — for any given block break, exactly one of three outcomes happens, in this order of
precedence:

1. **Zone `resource-blocks:`** (if the block's location is inside a zone configuring that
   material) — handled entirely by `ResourceListener`/`ResourceManager` at `LOWEST`.
2. **`block_loot`** (if no zone resource-block applies, but the material has a global override) —
   handled entirely by `BlockLootListener`/`BlockLootManager`, also at `LOWEST`.
3. **Vanilla** (neither applies) — handled by `item.LootListener` at `HIGH`: real
   `block.getDrops(tool, player)`, translated to Valmora format, given to the player.

Both `LOWEST`-priority listeners run before `LootListener`'s `HIGH` handler ever executes, and
each is responsible for its own `event.setDropItems(false)` — `LootListener`'s defer checks exist
purely as a second, explicit guard against double-processing (matching the precedent
`ResourceModule` already set), not as the actual precedence mechanism.

---

## Dependencies & Consumers

- **`item` module** — `ItemManager.createItemStack`/`giveOrPrivateDrop` (runtime access only, via
  `plugin.getItemManager()`; no `onEnable()`-time dependency in either direction).
- **`resource`/`zone` modules** — no direct code dependency, but see precedence above: `resource`
  effectively takes priority for any block a zone also configures.
- Registered in `Valmora.java` right after `resourceModule`/`fishingModule` (grouped with the
  other loot-source modules) and before `npcModule`. See CLAUDE.md §5 for the full module order.

---

## Unfinished Things / Possible Improvements

- `ResourceManager`'s own give-path (`player.getInventory().addItem(item)`, ignoring the leftovers
  map) still silently loses overflow items on a full inventory — `block_loot` deliberately doesn't
  repeat this (it uses the new `ItemManager.giveOrPrivateDrop`), but the pre-existing gap in
  `ResourceManager` itself was left untouched as out of scope for this pass.
- `ResourceManager`'s private `createItem` duplicates `ItemManager.createItemStack`'s resolution
  logic rather than calling it directly; `block_loot` calls the public API instead. Worth
  revisiting `ResourceManager` to do the same in a future pass.
- No per-block Silk Touch override — `block-loot.silk-touch.enabled` is one global toggle for
  every configured block, matching `resource.silk-touch.enabled`'s existing simplicity. A future
  pass could add a per-entry override if a concrete need shows up (nullable `Boolean` field,
  same convention as `ZoneFlags.keepInventoryOnDeath`).
- No general `BlockDropItemEvent`/`BlockExpEvent` hook for arbitrary blocks beyond this
  material-keyed override system — XP orb drops from ore-like blocks are untouched by this module
  (same known limitation `resource`'s resource-blocks already have).
