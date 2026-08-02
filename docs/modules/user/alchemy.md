# Alchemy Module — User Documentation

> The **Alchemy Module** is Valmora's custom potion-brewing system. It replaces vanilla potion brewing with a data-driven table of **effects** (potions), an ingredient → tier progression, level/duration/splash **modifiers**, and a per-player **active effects** system that feeds your stats. All definitions are YAML and fully configurable by the server owner.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

Brewing works on an **Alchemy Table** GUI instead of the vanilla brewing stand. The flow is:

```
Water Bottle + Nether Wart   →   Awkward Potion
Awkward Potion + Ingredient   →   Brewed Potion (level 1+)
Brewed Potion + Modifier      →   Upgraded level / duration / splash
```

Everything about a potion (effect id, level, duration, splash state, whether it was already modified) is stored on the item itself, so potions survive in inventories, chests, and trades like any other item.

Three concepts to understand:

- **Effects** — a potion type (e.g. `speed`, `strength`, `poison`). 19 ship by default. Each has a level scale, per-level duration, and optional per-level stat values.
- **Tiers** — each effect lists ingredients that produce it at a given **base level**. Higher-tier ingredients (usually custom Valmora items) brew stronger base potions directly.
- **Modifiers** — ingredients applied to an already-brewed potion: glowstone raises the level, redstone overrides the duration, gunpowder turns it into a splash potion. Each category applies **once** per potion.

When you drink (or splash) a Valmora potion, the effect becomes an **active effect** on you: it is applied for its real-time duration, and any stat values it grants are folded into your stats while it lasts. Debuffs can be splashed onto enemies; buffs from splash potions only help players.

> **Persistence note:** active effects live in memory only. They survive a `/valmora reload` but are lost when the server restarts. Potion **items** are never lost.

---

## 2. Player Guide

### 2.1 The brewing walkthrough

The Alchemy Table GUI is opened by the server owner (bound to a command, an item, or an NPC). Its layout:

| Slot type | Purpose |
| --- | --- |
| `I` | Ingredient input |
| `P` | Bottle inputs (up to the table's bottle slots) |
| `T` | Status display (brew time countdown, errors) |
| `C` | Close button |

A brew cycle takes **10 seconds** of brewing time. If the ingredients match a recipe, the ingredient is consumed at the start and every non-empty bottle slot is filled with the result when the timer ends.

**Step 1 — make an Awkward Potion.** Put a **Water Bottle** in a bottle slot and **Nether Wart** in the ingredient slot. This is the base every potion needs.

**Step 2 — brew an effect.** Replace the nether wart with the effect's **base ingredient** (see the table below). The result is the potion at that ingredient's tier level. Example: `Awkward Potion + Sugar` → `Potion of Speed I`.

**Step 3 — modify (optional).** Add a modifier ingredient to a brewed potion to upgrade it. Each of the three modifier types can only be applied **once** per potion, and — except for regular glowstone dust — modifiers only work on a potion already brewed at its **highest tier**.

| Modifier | Item | Effect |
| --- | --- | --- |
| Level | Glowstone Dust (+1) | +1 level. **No tier requirement.** |
| Level | Enchanted Glowstone Dust (+2) | +2 levels (requires max base tier) |
| Level | Enchanted Glowstone (+3) | +3 levels (requires max base tier) |
| Duration | Redstone | Set duration to 8 minutes (requires max base tier) |
| Duration | Enchanted Redstone | Set duration to 16 minutes (requires max base tier) |
| Duration | Enchanted Redstone Block | Set duration to 40 minutes (requires max base tier) |
| Splash | Gunpowder | Convert to splash, **−50% duration** (requires max base tier) |
| Splash | Enchanted Gunpowder | Convert to splash, no duration penalty (requires max base tier) |

A level modifier can never push a potion past its `max-level`. Brewing at a higher **Alchemy skill level** gives you **+1% potion duration per level**.

### 2.2 Default effects and their base ingredients

| Effect | Base ingredient (level) | Higher tiers | Type |
| --- | --- | --- | --- |
| Speed | Sugar (I) | Enchanted Sugar (III), Enchanted Sugar Cane (V) | Buff |
| Jump Boost | Rabbit Foot (I) | — | Buff |
| Healing | Spider Eye (I) | — | Buff |
| Poison | Glistering Melon Slice (I) | Enchanted Glistering Melon (III), Enchanted Blistering Melon (clamped to IV) | Debuff |
| Water Breathing | Pufferfish (I) | Enchanted Pufferfish (III) | Buff |
| Fire Resistance | Magma Cream (I) | — | Buff |
| Night Vision | Golden Carrot (I) | — | Buff |
| Strength | Blaze Powder (I) | Enchanted Blaze Powder (III), Enchanted Blaze Rod (V) | Buff |
| Invisibility | Fermented Spider Eye (I) | — | Buff |
| Regeneration | Ghast Tear (I) | Enchanted Ghast Tear (III), Concentrated Ghast Tear (V) | Buff |
| Weakness | Rotten Flesh (I) | Enchanted Rotten Flesh (III) | Debuff |
| Slowness | Turtle Scute (I) | Enchanted Turtle Scute (III) | Debuff |
| Damage | Cactus (I) | Enchanted Cactus (III) | Debuff |
| Haste | Coal (I) | — | Buff |
| Burning | Red Sand (I) | — | Buff |
| Absorption | Gold Ingot (I) | Enchanted Gold Ingot (III), Enchanted Gold Block (V) | Buff |
| Critical Strike | Flint (I) | — | Buff |
| Resistance | Nautilus Shell (I) | Enchanted Nautilus Shell (III), Hardened Nautilus Shell (V) | Buff |
| Mana | Mutton (I) | Enchanted Mutton (III), Enchanted Cooked Mutton (V) | Buff |

### 2.3 Drinking vs splashing

| | Drinking | Splashing |
| --- | --- | --- |
| Buff effects | Apply to you | Apply to **players** in range only |
| Debuff effects | Apply to you | Apply to **every** affected living entity (including mobs) |
| Duration | Full item duration | Multiplied by the splash multiplier (×0.5 for regular gunpowder) |
| Distance | — | Every affected entity gets the full level and duration (no falloff by default) |

### 2.4 Special effect behaviors

Some effects do things stats can't:

- **Healing** — instantly restores health (20 / 50 / 100 / 150 / 200 / 250 / 300 / 350 HP by level). Instant, one-shot.
- **Damage** — instantly deals `5 × level` **true damage** (ignores defense). Best thrown as a splash at enemies.
- **Poison** — deals `10 × level` true damage every tick interval while active (players only). Instant-style application; the damage is dealt over the effect's lifetime.
- **Absorption** — grants bonus absorption health (`20 → 300` HP by level) that disappears when the effect ends.
- **Water Breathing** — a `15% × level` chance to **cancel drowning damage** each time you take it.
- **Burning** — hitting enemies sets them on fire for `2 × level` seconds per hit.
- **Jump Boost / Night Vision / Invisibility / Fire Resistance** — behave like the vanilla potion effects of the same names.

### 2.5 Active effects GUI

`/effects` opens your **Active Effects** list (if the server has the GUI installed). It shows every potion currently on you, its level, remaining time, type (buff/debuff), rarity, and the stat values it grants. The GUI is paginated if you have more effects than fit on one page.

---

## 3. Admin Guide

### 3.1 Commands & permissions

| Command | Permission | Description |
| --- | --- | --- |
| `/potion give <effect_id> <level> [player]` | `valmora.admin` | Gives a drinkable potion. Level is clamped to the effect's `max-level`. |
| `/effects` | (none — any player) | Opens the Active Effects GUI. |

The `/potion` command is intended for admin/test use and builds a plain, unmodified, drinkable potion (no splash).

### 3.2 What lives where

| File | Purpose |
| --- | --- |
| `alchemy/effects.yml` | Effect (potion) definitions — names, colors, tiers, durations, stats |
| `alchemy/modifiers.yml` | Modifier items — glowstone / redstone / gunpowder families |
| `items/alchemy_ingredients.yml` | Custom ingredient item definitions referenced by the above (enchanted tier-2/tier-3 items, enchanted modifier items) |
| `guis/alchemy.yml` | The Alchemy Table GUI layout and brew script |
| `guis/active_effects.yml` | The Active Effects GUI |
| `config.yml` → `alchemy:` | Module settings (see §4.4) |

All files are auto-extracted from the plugin jar the first time it runs (only if missing), so edits are never overwritten by updates.

### 3.3 Adding a new effect

Add a top-level key to `alchemy/effects.yml`:

```yaml
mystrength:
  name: "<dark_red>Potion of My Strength"
  type: BUFF
  rarity: RARE
  color: "#CC0000"
  lore:
    - "<gray>A custom strength potion."
  max-level: 3
  duration: [60, 90, 120]
  stats:
    strength: [10, 20, 30]
  tiers:
    - ingredient: "minecraft:iron_ingot"
      level: 1
    - ingredient: my_custom_ingredient   # a Valmora item id from items/*.yml
      level: 3
```

Rules to remember:

- The effect **id** is the YAML key; it must be unique and is matched case-insensitively.
- At least one tier (or the legacy single `ingredient:` key) is **required** — an effect with no way to brew it is rejected at load.
- `stats` keys must be **registered stat ids**. Unrecognized ids are silently dropped, so double-check spelling.
- `duration` and each `stats` list are **per level** (index 1..`max-level`); a missing index falls back to the last value in the list.
- A tier whose `level` exceeds `max-level` is clamped (the shipped `poison` effect has a tier-5 ingredient but `max-level: 4` — that tier brews at IV).

### 3.4 Adding a modifier item

Add an entry to the matching section of `alchemy/modifiers.yml`:

```yaml
level:
  - item: "minecraft:glowstone_dust"   # or a custom Valmora item id
    bonus: 1
    requires-max-base: false
```

| Section | Required field | Meaning |
| --- | --- | --- |
| `level` | `bonus` | Levels added (clamped to `max-level`) |
| `duration` | `seconds` | New absolute duration in seconds |
| `splash` | `duration-multiplier` | Duration multiplier on splash conversion (0.5 = −50%) |

- `requires-max-base: true` means the modifier only applies to a potion already at its **highest brewable tier** (no level modifier used). This prevents skipping recipe tiers.
- `item` can be `minecraft:<material>` (vanilla) or a custom Valmora item id matched by PDC.

### 3.5 Known limitations

- The Alchemy Table GUI (`guis/alchemy.yml`) has **no bound command** — it must be opened by the server via GUI/script hooks.
- Poison's tier-5 ingredient brews at level IV (tier level clamped to `max-level`).
- Splash radius is a **fixed** area-of-effect (config `splash-radius` is not read); every affected entity gets the full level/duration, and splash debuffs hit all entities while buffs only hit players.
- Drinking only consumes the potion when it's in the **main hand**.
- Lingering potions are not supported.
- The `alchemy` **skill grants no XP** from brewing by default (`skills/alchemy.yml` has no `sources`), even though it grants the +1%/level duration bonus.
- Modifier/potion **static recipes** (`recipes/alchemy.yml`) are a separate legacy path that outputs plain vanilla potions and does not feed the dynamic system.

---

## 4. Configuration Reference

### 4.1 Effect schema — `alchemy/effects.yml`

Each top-level key is the effect ID.

| Field | Type | Default | Required | Meaning |
| --- | --- | --- | --- | --- |
| *(top-level key)* | string | — | yes | Effect id — registry key, PDC value, command argument. Stored lowercase. |
| `name` | MiniMessage string | the id | no | Item display name; rendered as `name + roman numeral` (e.g. "Potion of Speed III"). |
| `type` | `BUFF` / `DEBUFF` | `BUFF` | no | Buff vs debuff — drives lore coloring, GUI dye, and splash targeting. |
| `rarity` | string | `COMMON` | no | Displayed on the bottom lore line (`UNCOMMON`→green, `RARE`→blue, `EPIC`→dark purple, `LEGENDARY`→gold, else gray). |
| `color` | hex string | purple | no | Potion liquid color (`#RRGGBB`). |
| `lore` | list of MiniMessage strings | `[]` | no | Extra lore lines above the stat block. |
| `max-level` | int | `1` | no | Absolute level ceiling — clamps tier levels, `/potion` levels, and level modifiers. |
| `duration` | list of ints | `[60]` | no | Base duration in **seconds**, per level `[1..max-level]`. |
| `stats` | map | `{}` | no | Per-level stat values; keys must be registered stat ids (unknown ones are silently dropped). |
| `tiers` | section \| list \| — | — | yes* | Ingredient → base level mapping (see below). Either `tiers` or a legacy `ingredient:` key is required. |
| `tiers[].ingredient` | string | — | yes | `minecraft:<material>`, a vanilla material name, or a custom Valmora item id. |
| `tiers[].level` | int | `1` | no | Base level this ingredient produces. |
| `ingredient` | string | — | no | Legacy single-ingredient form; becomes a level-1 tier. |

`tiers` accepts three formats (in preference order): a section of sub-keys, a list of maps, or a single legacy `ingredient:` key.

### 4.2 Modifier schema — `alchemy/modifiers.yml`

Three list sections; each entry applies its category **once** per potion.

| Field | Type | Default | Required | Meaning |
| --- | --- | --- | --- | --- |
| `item` | string | — | yes | Ingredient key: `minecraft:<material>` or a custom Valmora item id. |
| `bonus` | int | — | for `level` | Levels added; result clamped to the effect's `max-level`. |
| `seconds` | int | — | for `duration` | New absolute duration in seconds (×0.5 if the potion is already splash). |
| `duration-multiplier` | double | — | for `splash` | Duration multiplier on splash conversion. |
| `requires-max-base` | bool | `false` | no | If `true`, only applies to a potion at its max **base** (non-modifier) level. |

Shipped entries:

| Section | Item | Value | requires-max-base |
| --- | --- | --- | --- |
| level | `minecraft:glowstone_dust` | +1 level | false |
| level | `enchanted_glowstone_dust` | +2 levels | true |
| level | `enchanted_glowstone` | +3 levels | true |
| duration | `minecraft:redstone` | 480 s (8 m) | true |
| duration | `enchanted_redstone` | 960 s (16 m) | true |
| duration | `enchanted_redstone_block` | 2400 s (40 m) | true |
| splash | `minecraft:gunpowder` | ×0.5 (−50% duration) | true |
| splash | `enchanted_gunpowder` | ×1.0 (no penalty) | true |

### 4.3 Related item/skill/recipe config (owned by other modules)

- **`items/alchemy_ingredients.yml`** — defines the custom tier-2/tier-3 ingredients and `enchanted_*` modifier items referenced by `effects.yml` / `modifiers.yml` (enchanted sugar, blaze powder, glistering melon, gunpowder, redstone, glowstone, etc.).
- **`skills/alchemy.yml`** — the Alchemy skill (`max-level: 60`, milestone rewards at levels 10 and 30). Grants the +1% duration-per-level brew bonus; **no XP sources** ship by default.
- **`guis/alchemy.yml`** — the Alchemy Table GUI (`machine: alchemy`, 6 rows, 10-second brew cycle). No command binding ships.
- **`guis/active_effects.yml`** — the Active Effects GUI opened by `/effects`.
- **`recipes/alchemy.yml`** — legacy static shapeless recipes that output plain vanilla potions and grant `alchemy_xp` on craft (separate from the dynamic pipeline).

### 4.4 Module settings — `config.yml`

```yaml
alchemy:
  splash-radius: 4.0       # documented as splash AoE radius (not currently read by the code)
  tick-interval: 20        # ticks between active-effect expiry checks (20 = 1 s)
  max-active-effects: 10   # max concurrent active effects per player
```

| Key | Default | Meaning |
| --- | --- | --- |
| `alchemy.splash-radius` | `4.0` | Reserved: splash potion area-of-effect radius. **Not currently used** — splash effects apply to every entity the event reports in range. |
| `alchemy.tick-interval` | `20` | How often active effects are checked for expiry and tick mechanics (poison) run. Lower = more precise, slightly more CPU. |
| `alchemy.max-active-effects` | `10` | Maximum active effects a player can hold. Once full, **new** effects are rejected (re-applying an effect you already have still works). Note this value is read at startup, so a `/valmora reload` won't pick up changes until a restart. |

---

> For implementation details, class structure, and developer APIs, see `docs/modules/design/alchemy.md`. For intended potion values, see `docs/POTION_LIST.md`.
