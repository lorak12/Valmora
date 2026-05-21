# Valmora Manual Testing Guide

Run this checklist after any major update. Use a fresh test profile on the test server. Mark each case **PASS / FAIL / SKIP** and record the build version.

---

## Pre-flight: Automated Tests

Run these first. If any automated test fails, fix it before proceeding to manual tests.

```bash
# Fast smoke — pure units + YAML config validation (~10s)
./gradlew testUnit

# Full automated suite
./gradlew testFull

# Full build sanity check
./gradlew build
```

---

## Setup Commands

```
/valmora reload
/stat set <you> health 100 damage 10 strength 0 defense 0 crit_chance 0
/eco set <you> 10000
```

---

## TC-COM — Combat

| # | Steps | Expected |
|---|-------|----------|
| COM-01 | Set: 10 Damage, 0 Strength, 0 Defense on target. Attack. | Indicator shows `10`. Gold color, floats and fades. |
| COM-02 | Set: 100 Crit Chance, 100 Crit Damage, 10 Damage. Attack. | Indicator shows `20`. Distinct crit color/prefix. |
| COM-03 | Set: 100 Damage on attacker, 300 Defense on target. Attack. | Indicator shows `25` (100 × 100/400). |
| COM-04 | Set: 9999 Defense. Stand in void. | Void damage is NOT reduced. |
| COM-05 | Spawn `test_zombie` level 5 (baseDamage=5). Let it hit player with 0 Defense. | Player takes 9 damage (5 + 4). |
| COM-06 | Take damage; wait out of combat. | Health bar visually regens at expected rate. |

---

## TC-STAT — Stats

| # | Steps | Expected |
|---|-------|----------|
| STAT-01 | Open `/gui stats`. | All stats present with icons, correct colors, correct values. |
| STAT-02 | Run `/stat add <you> crit_chance 200`. Check GUI. | Displayed value capped at 100. |
| STAT-03 | Give `testSword` and equip it. Open stats GUI. | Damage +10, Strength +5, Crit Chance +20, Crit Damage +50 vs. baseline. |
| STAT-04 | Unequip the sword. | All four stats revert immediately. |
| STAT-05 | Equip `fallen_aegis` (if available). | Resistance I effect appears in effects bar; removing chestplate removes it. |

---

## TC-SKILL — Skills

| # | Steps | Expected |
|---|-------|----------|
| SKILL-01 | Break a stone block. Run `/skill info mining`. | XP increased; gain notification shown. |
| SKILL-02 | Stand at 8/10 XP for level 1. Break blocks until 10 XP. | Level-up title/animation fires; `/skill info` shows level 1. |
| SKILL-03 | Reach a milestone level (check skill YAML for defined milestones). | Milestone reward fires once; per-level reward also fires. |
| SKILL-04 | Break `DIAMOND_ORE` vs. `IRON_ORE`. | Each gives the configured XP from skills YAML. |
| SKILL-05 | Open skills list GUI; click into a skill's details. | Progress bar, level, XP, icon, description all correct. |
| SKILL-06 | Reach max level; continue gaining sources. | XP stays at max; no level-up fires. |

---

## TC-ECO — Economy

| # | Steps | Expected |
|---|-------|----------|
| ECO-01 | `/eco set wallet 500 bank 1000`. Open bank GUI. | Shows wallet=500, bank=1000. |
| ECO-02 | Deposit 200 in bank GUI. | Wallet=300, Bank=1200. |
| ECO-03 | Withdraw 500 from bank. | Wallet=800, Bank=700. |
| ECO-04 | Use "deposit all" button. | Wallet=0, Bank=1500. |
| ECO-05 | Withdraw 9999 (bank has 1500). | Error message; no change. |

---

## TC-REC — Recipes

| # | Steps | Expected |
|---|-------|----------|
| REC-01 | Open Forge GUI. Place correct inputs per `forge.yml` EXACT_SLOT recipe. | Output appears; craft consumes inputs. |
| REC-02 | Forge GUI: provide only half the required inputs. | Output slot empty. |
| REC-03 | Open Crafting GUI. Arrange ingredients in exact SHAPED pattern from `crafting_table.yml`. | Output appears; claiming it consumes inputs. |
| REC-04 | SHAPELESS recipe: place ingredients in non-standard order. | Output still appears. |
| REC-05 | Recipe requires 2x item; provide 1x. | No match; output empty. |
| REC-06 | Craft a recipe with an `on-craft` notify or XP grant. | on-craft script fires (visible notification or XP increase). |

---

## TC-ALC — Alchemy

| # | Steps | Expected |
|---|-------|----------|
| ALC-01 | Open Alchemy GUI; place Water Bottle + sugar ingredient. Brew. | Correct Valmora potion appears with name and color. |
| ALC-02 | Drink the brewed Speed I potion. | Speed stat increases by 5; effect appears in Active Effects GUI with timer. |
| ALC-03 | Wait for effect to expire (or `/potion clear`). | Speed reverts; effect removed from Active Effects GUI. |
| ALC-04 | Apply LEVEL modifier to potion. | Upgrades to Speed II; stat value doubles. |
| ALC-05 | Apply DURATION modifier. | Duration increases in Active Effects GUI. |
| ALC-06 | Apply SPLASH modifier; throw at entities. | Nearby entities receive effect; duration is reduced by `durationMultiplier`. |
| ALC-07 | Fill to `max-active-effects` (default 10). Apply one more. | 11th effect not applied; error or silent reject. |
| ALC-08 | Active Effects GUI with 3 active effects. | All 3 shown with correct name, level, countdown timer. |

---

## TC-GUI — GUI System

| # | Steps | Expected |
|---|-------|----------|
| GUI-01 | Open any GUI; press Escape. | Closes cleanly; no ghost items; on-close script fires. |
| GUI-02 | Open a paginated GUI; click next/previous page. | Content updates; buttons disable at boundaries. |
| GUI-03 | Place an item in an input slot; remove it. | Item returns to inventory; on-slot-update fires. |
| GUI-04 | Try to place item in output slot. | Rejected and returned to player. |
| GUI-05 | Stats GUI open. | `$player.health$`, `$player.mana$`, etc. show correct values. |
| GUI-06 | Trigger a craft; close GUI mid-process. | No item duplication or loss; lock resets. |

---

## TC-NPC — NPC & Dialogue

| # | Steps | Expected |
|---|-------|----------|
| NPC-01 | Right-click hub NPC. | Dialogue opens; configured greeting text shown. |
| NPC-02 | Navigate dialogue choices. | Correct node reached per choice. |
| NPC-03 | Select a condition-guarded choice without/with meeting condition. | Blocked without condition; proceeds with condition. |
| NPC-04 | Select a choice with on-select events (give item, start quest). | Item received / quest started. |
| NPC-05 | Approach NPC with hologram. | Floating text name/title visible. |

---

## TC-QUEST — Quest System

| # | Steps | Expected |
|---|-------|----------|
| QUEST-01 | Trigger quest start via NPC or command. | Quest status = IN_PROGRESS; objectives shown in journal. |
| QUEST-02 | Complete an objective (collect items, kill mobs, etc.). | Objective marks complete; quest advances. |
| QUEST-03 | Complete final objective. | Rewards granted; status = COMPLETED. |
| QUEST-04 | Try to start quest that requires a prerequisite you haven't done. | Quest unavailable. |
| QUEST-05 | Open quest journal. | Active quests in one section; completed in another; clicking shows details. |

---

## TC-ZONE — Zones

| # | Steps | Expected |
|---|-------|----------|
| ZONE-01 | Walk into a configured zone boundary. | Entry notification/title fires. |
| ZONE-02 | Walk out of the zone. | Exit notification fires. |
| ZONE-03 | Enter a PvP-disabled zone; try to hit another player. | Damage blocked; message shown. |
| ZONE-04 | Enter a zone with mob spawner; wait a tick. | Configured mobs spawn inside boundaries. |

---

## TC-WARP — Warps

| # | Steps | Expected |
|---|-------|----------|
| WARP-01 | `/warp hub` | Teleport to hub.yml coordinates; arrival message shown. |
| WARP-02 | `/warp hub` without permission. | Permission denied; no teleport. |
| WARP-03 | If warmup configured: start warp, then move. | Countdown cancels; staying still completes teleport. |

---

## TC-FISH — Fishing

| # | Steps | Expected |
|---|-------|----------|
| FISH-01 | Fish in a configured fishing zone. | Bite indicator (particles/sound); reeling gives loot table item. |
| FISH-02 | Catch 50+ items. | Rarer items appear proportionally less often. |
| FISH-03 | Zone has sea-creature-chance > 0. Fish. | Occasionally a custom mob spawns near bobber. |
| FISH-04 | Catch fish. Check `/skill info fishing`. | Fishing XP increases. |

---

## TC-TIME — Time System

| # | Steps | Expected |
|---|-------|----------|
| TIME-01 | `/time` or view a time-variable GUI. | Correct hour, minute, day, season, phase, year displayed. |
| TIME-02 | Advance world 24000 ticks. | Valmora day counter increments; day-change event fires. |
| TIME-03 | Reach a season transition day. | Server-wide announcement; season name updates. |

---

## TC-ENCH — Enchanting

| # | Steps | Expected |
|---|-------|----------|
| ENCH-01 | Open Enchanting GUI; apply enchantment to an item. | Enchantment appears in item lore; affected stats change. |
| ENCH-02 | Remove the enchantment. | Removed from lore; stats revert. |
| ENCH-03 | Sword with Sharpness V — attack enemy. | Damage ~25% higher vs. unenchanted sword (verify against COM-01 baseline). |

---

## TC-INT — Cross-Module Integration

| # | Steps | Expected |
|---|-------|----------|
| INT-01 | Apply Strength potion level 1 (+5 Strength); attack enemy with 0 Defense, 10 base Damage. | Damage = `10 * (1 + 5/100)` = 10.5 → verify indicator. |
| INT-02 | Configure per-level-reward script to `variable add player.var.stat_points 1`; level up skill. | `$player.var.stat_points$` increased by 1. |
| INT-03 | Quest with "reach skill level X" objective; level up skill. | Quest objective auto-completes. |
| INT-04 | NPC dialogue choice uses `gui-open`; select it. | GUI opens correctly after dialogue closes. |
| INT-05 | POISON effect active (hardcoded); wait for tick. | Health decreases on tick; stops on expiry. |
| INT-06 | `/valmora reload` with players online. | All modules reload without exception; GUI sessions, active effects, quests, and skill data remain intact for online players. |
