# Reforge Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Config folder:** `plugins/Valmora/reforges/` | **Module ID:** `reforge`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

The Reforge Module adds a **reforging system** that permanently boosts custom items by applying a named modifier (a *reforge*) whose stat bonus scales with the item's **rarity**. A Diamond-tier sword reforged with "Fierce" gets a stronger bonus than the same reforge on a Common sword.

Reforging happens at **two GUI machines**:

- **Reforge Anvil** — put your item in the left slot and a **Reforge Stone** in the right slot. The reforge on the stone is applied to your item **exactly** as written on the stone.
- **Random Forge** — put only your item in. A **random** valid reforge (never the item's current one) is applied.

Both machines charge a **coin cost based on your item's rarity**. When a reforge is applied, the item's stats are recalculated from scratch (base stats + the reforge's bonus), so reforges **never stack** — applying a new reforge always replaces the previous one.

Reforge definitions live in YAML files under `plugins/Valmora/reforges/`. The plugin ships with eight combat reforges out of the box (Fierce, Sharp, Fabled, Heroic, Rapid, Fortified, Reinforced, Titanic). There are no dedicated reforge commands — machines are opened as GUIs and stone/definition changes are picked up with `/valmora reload`.

---

## 2. Player Guide

### 2.1 What reforges do

A reforge adds a fixed set of stat bonuses to an item. Which stats, and how much, depend on the **reforge** and the **rarity tier** of the item. For example, the shipped `fierce` reforge grants `strength` and `crit_damage`; the shipped `titanic` reforge grants `health` and `defense`. Higher-rarity items receive bigger bonuses:

| Item rarity | `fierce` bonus example |
|---|---|
| Common | +5 Strength, +3 Crit Damage |
| Uncommon | +12 Strength, +6 Crit Damage |
| Rare | +20 Strength, +10 Crit Damage |
| Epic | +32 Strength, +15 Crit Damage |
| Legendary | +48 Strength, +22 Crit Damage |
| Mythic | +65 Strength, +30 Crit Damage |
| Divine | +85 Strength, +40 Crit Damage |

If a reforge has no entry for your item's rarity, it uses the next **lower** tier that is defined.

### 2.2 Getting a Reforge Stone

Reforge Stones are `AMETHYST_SHARD` items with purple `REFORGE STONE` lore. You get them from server admins — there is no player-facing way to craft or buy them (yet). Admins grant them with:

```
/item give fierce_reforge_stone
/item give titanic_reforge_stone
```

The stone's lore lists which item types it works on and exactly what stat bonus it gives per rarity tier, including the coin cost per rarity.

### 2.3 Using the Reforge Anvil (deterministic)

1. Open the Reforge Anvil GUI.
2. Place your item in the **left slot** (`base_item`).
3. Place a Reforge Stone in the **right slot** (`reforge_stone`).
4. Click the green **Apply Reforge!** anvil button.

Result: the exact reforge written on the stone is applied to your item. **Both the item and the stone are consumed**, and the reforged item appears in the output slot. The coin cost is deducted from your balance.

> **Note:** The output slot stays empty until you click **Apply Reforge!** — there is no live preview of the result before paying.

### 2.4 Using the Random Forge (random)

1. Open the Reforge GUI.
2. Place your item in the input slot.
3. Click the yellow **Reforge Item** button.

Result: a random reforge that your item type supports is applied (the current reforge, if any, is excluded). **The item is replaced in place** by the reforged result, and the coin cost is deducted. If no reforge applies to your item type, nothing happens.

### 2.5 Cost table

Cost is charged for **both** machines and depends on the item's rarity:

| Item rarity | Cost |
|---|---|
| Common | 250 Coins |
| Uncommon | 500 Coins |
| Rare | 1,000 Coins |
| Epic | 2,500 Coins |
| Legendary | 5,000 Coins |
| Mythic | 10,000 Coins |
| Divine | 15,000 Coins |

If you do not have enough coins, the machine does nothing and you see a chat warning: *"You need \<amount> Coins to use the forge."*

### 2.6 Rules to remember

- **Reforges do not stack.** Reforging always wipes the previous reforge and computes fresh base stats + the new bonus.
- A reforged item's **name** is prefixed with the reforge name (e.g. `Fierce Demon Slayer Sword`), and its stat lore is updated to the new totals.
- **Custom items** keep their original base stats; reforges are added on top. **Vanilla (non-custom) items** lose their built-in stats when reforged and end up with only the reforge's bonuses.
- Reforging preserves the item's material, enchantments, abilities, and other metadata — only the stats are rerolled.

---

## 3. Admin Guide

### 3.1 Where reforges live

Each reforge is one top-level key in a `.yml` file inside `plugins/Valmora/reforges/` (any number of files, any number of reforges per file). Files are read **only on load** — after editing, run:

```
/valmora reload
```
(requires the `valmora.admin` permission)

On reload you will see a console report like:
- `Successfully loaded N Reforge.`
- `Failed to load some Reforge. ...` followed by one warning per broken definition (a broken reforge is skipped; the rest still load).

### 3.2 Minimal example

```yaml
fierce:
  name: "Fierce"
  applicable-types:
    - SWORD
    - AXE
  generate-stone: true
  stat-bonuses-by-rarity:
    COMMON:
      strength: 5
      crit_damage: 3
    UNCOMMON:
      strength: 12
      crit_damage: 6
    RARE:
      strength: 20
      crit_damage: 10
```

This defines the `fierce` reforge for swords and axes, with `generate-stone: true` so `/item give fierce_reforge_stone` works. Missing tiers (e.g. no `EPIC`) fall back to the nearest lower tier automatically — but you can define all seven for full control:

```yaml
stat-bonuses-by-rarity:
  COMMON:    { strength: 5 }
  UNCOMMON:  { strength: 12 }
  RARE:      { strength: 20 }
  EPIC:      { strength: 32 }
  LEGENDARY: { strength: 48 }
  MYTHIC:    { strength: 65 }
  DIVINE:    { strength: 85 }
```

### 3.3 Anatomy of a reforge

| Key | Required? | Purpose |
|---|---|---|
| `name:` | No (defaults to the id) | Display name shown on the reforge stone and prefixed onto reforged items. |
| `applicable-types:` | No (default: everything) | List of `ItemType` values the reforge can be applied to. See valid values below. |
| `generate-stone:` | No (default `false`) | If `true`, `/item give <reforge-id>_reforge_stone` is enabled. |
| `stat-bonuses-by-rarity:` | Yes (empty map does nothing) | Rarity tier → stat-id → bonus value. |

### 3.4 Valid `applicable-types` values

```
SWORD  AXE  PICKAXE  SHOVEL  HOE  TRIDENT  BOW  CROSSBOW
FISHING_ROD  SHEARS  SHIELD  ELYTRA
HELMET  CHESTPLATE  LEGGINGS  BOOTS  HORSE_ARMOR
PET  ACCESSORY  BACKPACK
ALL  NONE
```

- `ALL` matches any item type; `NONE` matches items with no type assigned.
- An **empty or missing** list means the reforge applies to **everything**.
- Values are case-insensitive. Unknown values are silently ignored at load.

### 3.5 Valid stat ids

The `stat-bonuses-by-rarity` stat keys must match ids from your `stats/*.yml` definitions. The shipped reforges use: `strength`, `crit_damage`, `damage`, `crit_chance`, `ferocity`, `bonus_attack_speed`, `defense`, `health`, `true_defense`. Stat keys are case-insensitive and stored lowercased. **Unknown stat ids are not validated** — they are written to the item but silently dropped from lore, so double-check your spelling.

### 3.6 Wiring the machines into your server

The two machines are GUI definitions:

| GUI file | GUI id | Machine id | Slots |
|---|---|---|---|
| `plugins/Valmora/guis/reforge_anvil.yml` | `reforge_anvil` | `reforge_anvil` | item (left) + stone (right) → output |
| `plugins/Valmora/guis/reforge.yml` | `reforge` | `forge_random` | item (center) → in-place reforged item |

Neither shipped GUI defines a `command:` key, so no player command is auto-registered to open them. You can open them for testing with:

```
/gui open <player> reforge_anvil
/gui open <player> reforge
```
(requires the `valmora.admin.gui` permission)

To let players open them in-game you should add a `command:` key to the GUI files (auto-registers an open command, see the GUI module docs) or trigger them through NPC / shop / script actions.

### 3.7 Item-side configuration you can use

Items can carry their own reforge pool (acts like a built-in multi-id stone):

```yaml
some_sword:
  material: DIAMOND_SWORD
  item-type: SWORD
  rarity: RARE
  reforge-pool:
    - fierce
    - fabled
```

> **Known limitation:** the Anvil machine currently reads only the **first** id of a reforge pool, so extra entries are ignored.

### 3.8 Permissions

| Permission | Effect |
|---|---|
| `valmora.admin` | Required for `/valmora reload` (picks up reforge file changes) and `/item give <id>_reforge_stone`. |
| `valmora.admin.gui` | Required for `/gui open <player> <gui>` used to open the machines for testing. |

There is **no** player-facing reforge permission and **no** dedicated `/reforge` command. Coin costs are charged through the economy module.

### 3.9 Reloading and editing workflow

1. Edit or add files under `plugins/Valmora/reforges/`.
2. Run `/valmora reload`.
3. Check the console for the load report (see §3.1).
4. Regrant stones with `/item give <reforge-id>_reforge_stone` — existing reforged items are unaffected by config changes until reforged again.

---

## 4. Configuration Reference

Folder: `plugins/Valmora/reforges/` — auto-created from the plugin jar on first run (existing files are never overwritten). All keys and defaults below are taken directly from the parser (`ReforgeModule.java:307-340`).

### 4.1 Per-reforge keys

| Key | Type | Default | Explanation |
|---|---|---|---|
| `<reforge-id>` | — | *(required)* | The YAML top-level key. Identifies the reforge; used in `/item give <id>_reforge_stone`, load-error messages, and the item's `reforge_id` tag. Use lowercase ids. |
| `name` | string | the reforge id | Display name shown on the reforge stone and prefixed onto reforged items' names. |
| `applicable-types` | list of strings | *(empty list = applies to all)* | Item types this reforge can be applied to. Case-insensitive `ItemType` values; unknown entries are skipped. |
| `generate-stone` | boolean | `false` | When `true`, `/item give <reforge-id>_reforge_stone` creates an `AMETHYST_SHARD` Reforge Stone for this reforge. |
| `stat-bonuses-by-rarity` | section | *(empty)* | Map of rarity tier → stat-id → bonus value (numbers). Tiers: `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`, `MYTHIC`, `DIVINE` (case-insensitive; unknown tiers skipped). Missing tiers fall back to the nearest lower tier. |

### 4.2 Coin costs (engine hard-coded, not editable in YAML)

| Rarity | Cost to Reforge |
|---|---|
| `COMMON` | 250 |
| `UNCOMMON` | 500 |
| `RARE` | 1,000 |
| `EPIC` | 2,500 |
| `LEGENDARY` | 5,000 |
| `MYTHIC` | 10,000 |
| `DIVINE` | 15,000 |

### 4.3 Shipped defaults — `reforges/combat.yml`

The plugin ships with eight reforges, all with `generate-stone: true` and all seven rarity tiers defined.

**Weapon reforges:**

| Reforge | Applies to | Stats (COMMON → UNCOMMON → RARE → EPIC → LEGENDARY → MYTHIC → DIVINE) |
|---|---|---|
| `fierce` | SWORD, AXE | `strength` 5/12/20/32/48/65/85 · `crit_damage` 3/6/10/15/22/30/40 |
| `sharp` | SWORD, AXE, BOW | `damage` 5/10/18/28/42/58/75 · `crit_chance` 2/3/5/7/10/14/18 |
| `fabled` | SWORD, AXE | `strength` 3/7/12/18/28/40/55 · `damage` 4/8/14/22/33/46/62 · `ferocity` 1/2/4/6/9/13/18 |
| `heroic` | SWORD, AXE, BOW, CROSSBOW | `crit_chance` 3/5/8/11/15/20/26 · `crit_damage` 6/12/20/30/45/62/82 |
| `rapid` | BOW, CROSSBOW | `bonus_attack_speed` 8/16/26/38/55/75/100 · `damage` 4/8/14/22/33/46/62 |

**Armor reforges (all apply to HELMET, CHESTPLATE, LEGGINGS, BOOTS):**

| Reforge | Stats (COMMON → DIVINE) |
|---|---|
| `fortified` | `defense` 8/18/30/45/65/90/120 · `health` 10/20/35/55/80/110/150 |
| `reinforced` | `true_defense` 3/6/10/15/22/30/40 · `defense` 6/14/24/37/54/74/100 |
| `titanic` | `health` 20/40/70/110/160/220/300 · `defense` 4/8/14/20/30/42/56 |

### 4.4 Related settings (elsewhere in the plugin)

| Setting | Where | Notes |
|---|---|---|
| `reforge-pool` | item definitions (`items/*.yml`, `ItemDefinitionParser.java:69-72`) | Optional list of reforge ids a custom item can act as. Only the first id is currently used by the Anvil machine. |
| `item-type` / `rarity` | item definitions | Determine which reforges apply and the coin cost; written to the item at create time (`ItemFactory.java:33-39`). |
| Economy balance | economy module | `hasCoins`/`removeCoins` gate the craft (`EconomyService.java:9`, `:7`). If the economy is unavailable, reforges cost nothing. |
| Machine GUIs | `plugins/Valmora/guis/reforge_anvil.yml`, `guis/reforge.yml` | Defines the GUI layout, slots, and cost lore text; references the machine ids `reforge_anvil` and `forge_random`. |
