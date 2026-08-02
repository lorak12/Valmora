# Cross-Module Integration (User Guide)

> **Purpose:** Understand how Valmora's systems work together. This guide explains the relationships between modules, what data flows where, and how features combine to create the player experience.
>
> Related: [VALMORA_DOCUMENTATION.md](../../VALMORA_DOCUMENTATION.md), [MODULE_DEVELOPMENT.md](../../MODULE_DEVELOPMENT.md)

---

## 1. How Modules Connect

Valmora is built from **35+ interconnected modules**. Each module handles one area of the game, and they communicate through shared systems (profiles, stats, scripts, items, and events).

### 1.1 Module Families

Modules group into logical families:

| Family            | Modules                                  |
|-------------------|------------------------------------------|
| **Core Engine**   | script, stat, profile, combat            |
| **Items**         | item, enchant, reforge, accessory, backpack, quiver |
| **Entities**      | mob, npc, quest                          |
| **Skills**        | skill, alchemy, slayer                   |
| **World**         | zone, resource, fishing, time, calendar  |
| **Progression**   | collection, progression                  |
| **User Interface**| gui, ui, hud, notify                     |
| **Economy**       | economy                                  |

### 1.2 Dependency Flow

Modules load in a strict order — earlier modules set up systems that later modules build upon. For example:

- **script** loads first (provides parameter resolution for everything)
- **stat** loads next (provides the stat system)
- **profile** builds on stat (adds player profiles)
- **combat** uses profile + stat + mob (handles all combat logic)
- **item** uses stat + profile + script (custom items with stats)
- **skill** uses stat + profile + item + mob (active abilities)
- ...and so on, with later modules like **slayer**, **quest**, and **progression** using the full stack.

---

## 2. Key Shared Systems

These are the "glue" that connects modules:

### 2.1 Player Profiles

Every player has a **profile** — an in-memory data container that tracks their stats, currency, inventory, quest progress, and more. Think of it as the player's persistent character sheet.

- **Created/loaded** when a player joins
- **Updated** by stats, items, combat, quests, skills, etc.
- **Saved** when a player leaves or the server reloads

### 2.2 Stat System

**Stats** are the numerical attributes of entities — health, damage, defense, speed, etc. They come from:

- Base player stats (in `config.yml`)
- Equipped items (`item` module)
- Enchants (`enchant` module)
- Reforges (`reforge` module)
- Accessories (`accessory` module)
- Skills (`skill` module)
- Zone effects (`zone` module)
- Potions/consumables (`alchemy` module)

All stat sources combine to produce a player's **final stats**, used in combat and displayed on the HUD.

### 2.3 Scripting Engine

The **script** module provides a lightweight scripting system used by nearly every other module. Scripts control:

- Item effects (what happens when you use an item)
- Skill behavior (damage calculations, targeting, cooldowns)
- Mob AI (custom behaviors, attack patterns)
- Quest logic (objectives, conditions, branching)
- Zone triggers (entering a zone, time-based events)
- UI interactions (GUI button actions)

### 2.4 Custom Items

The **item** module defines all custom items — weapons, armor, consumables, materials, and quest items. Each item can have:

- Base stats (damage, defense, health)
- Scripted effects (on-use, on-hit, passive)
- Enchant slots (`enchant` module)
- Reforge slots (`reforge` module)
- Special NBT data (tier, rarity, custom model data)

### 2.5 Zones

**Zones** define areas of the world with special rules — changed stats, weather, mob spawns, event triggers, and loot tables. Multiple systems interact with zones:

- **Time** — zone-local day/night cycles
- **Resource/Fishing** — zone-specific nodes and catches
- **Slayer** — zone-specific boss mobs
- **Mob** — zone-specific spawns
- **Hud** — zone name display

---

## 3. Feature Combination Examples

### 3.1 Combat Resolution

When a player attacks a mob, many systems collaborate:

```
Player swings weapon
  ↓
Combat module calculates hit chance (from stat + item)
  ↓
If hit → Combat module calculates base damage
  ↓
Stat module applies final modifiers:
  • Weapon damage (item)
  • Player attack power (stat/profile)
  • Enchants on weapon (enchant)
  • Active skill bonuses (skill)
  • Zone modifiers (zone)
  • Accessory bonuses (accessory)
  ↓
Slayer module checks if target is a slayer mob → grants slayer XP
  ↓
Notify module displays floating damage text
  ↓
Quest module checks if this mob counts toward any objectives
  ↓
Progression module checks for combat-related achievements
  ↓
Profile module updates the mob's health/damage tracking
```

### 3.2 Using a Custom Item

When a player right-clicks with a custom item:

```
PlayerInteractEvent fires
  ↓
Item module identifies the custom item via Persistent Data Container
  ↓
Script module runs the item's "on-use" script
  ↓
Profile module updates player stats/state if applicable
  ↓
Stat module recalculates player stats (if the item grants bonuses)
  ↓
Skill module checks for any skill triggers (e.g., "on potion use, cast Fireball")
  ↓
Notify module shows feedback (e.g., "Restored 50 HP!")
  ↓
Progression module checks for usage-based objectives
```

### 3.3 Completing a Quest Objective

```
Player performs an action (kill mob, collect item, talk to NPC)
  ↓
Quest module detects the action via event listeners
  ↓
Profile module provides the player's current quest progress
  ↓
Quest module checks if the objective condition is met
  ↓
If complete:
  • Item module grants item rewards
  • Economy module grants currency
  • Stat module grants stat/permanent bonuses
  • Skill module grants skill points
  • Progression module tracks achievement
  • Notify module sends completion message
  • Profile module saves updated progress
```

### 3.4 Boss Slayer Task

```
Slayer module assigns a boss kill task
  ↓
Mob module spawns the boss in a designated zone
  ↓
Player fights the boss:
  • Combat module handles damage
  • Skill module handles boss abilities
  • Stat module provides player stats/bonuses
  • Notify module shows boss health bar
  • Hud module displays active task info
  ↓
Boss defeated:
  • Slayer module grants XP + task completion reward
  • Zone module may spawn reward chest
  • Quest module checks related objectives
  • Progression module tracks slayer milestones
  • Profile module saves rewards
```

---

## 4. Reload Behavior (Player Perspective)

When an admin runs `/valmora reload`:

### What happens:
1. **All modules shut down** — event listeners are removed, custom mobs despawn, GUI data is cleared, active combat effects end.
2. **All configurations reload** — YAML files are re-read, item definitions refresh, mob stats recalculate, zone rules reset.
3. **Player profiles reload** — saved data is re-read; active potions/effects may reset depending on persistence settings.

### What players experience:
- **Brief freeze** (~1-3 seconds) as everything reloads.
- **Active cooldowns** on skills/items may reset.
- **Open GUIs** close; players must reopen them.
- **Summoned pets** (if not persistent) may despawn.
- **Quest progress** is NOT lost — it's saved to the player profile.
- **Inventory contents** are preserved (items are persistent).

---

## 5. System Overview Table

| System       | What It Does                        | Key Modules                |
|--------------|-------------------------------------|----------------------------|
| **Profiles** | Player character data (stats, inventory, progress) | profile, stat, item, quest, skill, progression |
| **Combat**   | Damage calculation, hit/miss, crits | combat, stat, skill, slayer |
| **Items**    | Custom weapons, armor, consumables   | item, enchant, reforge, accessory |
| **Mobs**     | Custom enemy/NPC entities            | mob, npc, combat, quest     |
| **Skills**   | Active and passive abilities         | skill, alchemy, combat      |
| **World**    | Zones, events, environment           | zone, resource, time, fishing |
| **Quests**   | Mission objectives and rewards       | quest, npc, progression     |
| **Slayer**   | Boss hunting system                  | slayer, mob, combat, zone   |
| **UI**       | Menus, HUD, notifications            | gui, ui, hud, notify        |
| **Economy**  | Currency and trading                 | economy, npc, quest         |

---

## 6. Common Cross-Module Features

### 6.1 Gear Progression

A typical gear upgrade path involves:

1. **Fishing/Resource** — gather raw materials
2. **Item** — craft/upgrade base gear
3. **Enchant** — add enchantments (requires skill level + materials)
4. **Reforge** — apply reforges for stat bonuses (requires currency + special anvil)
5. **Accessory** — equip accessories in accessory slots
6. **Stat** — all gear contributes to final stats
7. **Combat** — improved stats = stronger in combat
8. **Slayer** — stronger gear unlocks tougher boss fights
9. **Progression** — defeating bosses unlocks new recipes/areas

### 6.2 Skill Build Planning

Skills interact with multiple systems:

- **Stat** — skills require minimum stat thresholds
- **Item** — some skills require specific weapons equipped
- **Alc**emy — potion brewing enables certain skill effects
- **Enchant** — enchants can boost or modify skill damage
- **Slayer** — slayer tasks grant skill points
- **Combat** — skills are the primary combat mechanic
- **Progression** — skill milestones unlock new abilities

### 6.3 Quest → Reward Chain

Quest rewards cascade through the system:

1. **Quest** — completes and triggers reward script
2. **Item** — grants custom reward items
3. **Economy** — grants coins/pouches
4. **Stat** — grants permanent stat boosts
5. **Skill** — grants skill levels or unlock points
6. **Progression** — advances overarching achievement track
7. **Profile** — all changes saved to player data
