# Death Module — Design & Code

> **Module ID:** `death` | **Module Name:** "Death & Respawn" | **Package:** `org.nakii.valmora.module.death`
> **Files:** `DeathModule.java`, `DeathListener.java`, `DeathMessageService.java`, `DeathPolicyResolver.java`,
> `BedListener.java`, `RespawnAnchorListener.java`, `PhantomInsomniaListener.java`, `DeathPipelineLoader.java`
> **Related:** `TotemProtectionService.java`/`DeathContextCache.java` (live in `module/combat/`, not here — see §3.5)
> **Audit source:** `docs/VANILLA_CONTROL_AUDIT.md` §9 (Death, Respawn & Persistence)

---

## Table of Contents

1. [Overview](#overview)
2. [Code Structure](#code-structure)
3. [Architecture & Key Classes](#architecture--key-classes)
4. [Configuration (YAML)](#configuration-yaml)
5. [Dependencies & Consumers](#dependencies--consumers)
6. [Unfinished Things / Deliberately Out of Scope](#unfinished-things--deliberately-out-of-scope)

---

## Overview

Before this module existed, player death was **100% vanilla-triggered but through a virtualized-health
side door**, and nothing owned `PlayerDeathEvent`/`PlayerRespawnEvent` holistically:
`combat`'s `DamageApplier` reduces `PlayerState.currentHealth` (a plain double — real vanilla damage
is always zeroed by `CombatListener` before this), then `PlayerManager.syncVisualHealth` does
`if (current <= 0) player.setHealth(0)` to force a real vanilla death. Only three narrow, unrelated
listeners touched `PlayerDeathEvent`: `EconomyListener` (purse % loss), `HudItemListener` (strips HUD
items from drops), `QuestListener` (fires the `DIE` objective trigger). No death message,
keepInventory/keepExperience, respawn-location, or totem-of-undying control existed anywhere.

This module now owns that gap end to end:

1. **Custom death messages** — built entirely from Valmora's own `DamageType`/attacker/weapon
   resolution (`DeathMessageService` + `combat`'s `DeathContextCache`), **not** Paper's
   `DamageSource`/`DamageType` builder API (unused anywhere else in this codebase, and fighting the
   virtual-health architecture — see §3.5 for why).
2. **keepInventory/keepExperience** — a global config default with an optional per-zone override
   (`DeathPolicyResolver`, `ZoneFlags.keepInventoryOnDeath`/`keepExperienceOnDeath`).
3. **Totem of Undying** — actually fixed to interrupt Valmora's virtual-health death (it was silently
   non-functional before this — see §3.5). Lives in `module/combat/` since it must run *inside*
   `DamageApplier`, before `combat` even reaches this module's own logic.
4. **Respawn location** — an optional zone-declared override, layered on top of vanilla's own
   already-correct bed/anchor/world-spawn resolution (left untouched).
5. **Sleep/bed gating** (`ZoneFlags.sleeping`), **respawn-anchor/bed wrong-dimension explosion** zone
   protection, and **phantom insomnia** gating (global toggle + reused `naturalMobSpawning` flag).
6. Two new generic item-ability triggers, `ON_DEATH`/`ON_RESPAWN` (`module/item/AbilityTrigger.java`),
   so any modifier-framework ability — not just the built-in totem — can react to a wielder's death or
   respawn.

**Deliberately out of scope** (see §6): a vanilla-`DamageSource`-based message system, a void-damage
rescue/anti-void platform, and the audit's separate, much larger §4 general explosion/block-destruction
control system.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/death/
├── DeathModule.java             # ReloadableModule — registers/unregisters the four listeners + pipeline loader
├── DeathListener.java           # Owns PlayerDeathEvent (message/keepInventory/keepExperience) + PlayerRespawnEvent
├── DeathMessageService.java     # Template resolution + MiniMessage formatting for death messages
├── DeathPolicyResolver.java     # keepInventory/keepExperience precedence + zone-respawn-override parsing
├── BedListener.java             # PlayerBedEnterEvent gated by ZoneFlags.sleeping
├── RespawnAnchorListener.java   # BlockExplodeEvent (respawn anchor / bed) zone block-protection filtering
├── PhantomInsomniaListener.java # PhantomPreSpawnEvent gated by config + ZoneFlags.naturalMobSpawning
└── DeathPipelineLoader.java     # Loads death_pipeline.yml onto the shared HookBus ("player:*" points)

src/main/java/org/nakii/valmora/module/combat/           (existing module — new files added here)
├── TotemProtectionService.java  # The actual totem fix — must run inside DamageApplier (see §3.5)
└── DeathContextCache.java       # Per-player last-damage-context cache, feeds DeathMessageService
```

### Registering the module (`Valmora.java`)

- Field: `private org.nakii.valmora.module.death.DeathModule deathModule;`
- Instantiation: right after `zoneModule`.
- Registration: `moduleManager.registerModule(deathModule);` — **immediately after `zoneModule`**,
  before `resourceModule`. Needs `stat`, `economy`, `combat`, `zone` already enabled (all earlier);
  nothing later depends on it.
- API accessor: `ValmoraAPI.getDeathModule()` (both `Valmora.java` and `ValmoraAPIImpl`).

---

## Architecture & Key Classes

### 3.1 `DeathListener` — owns `PlayerDeathEvent`/`PlayerRespawnEvent`

`onDeath` (priority `NORMAL`) runs **before** `AbilityTriggerListener`'s `ON_DEATH` dispatch
(priority `HIGH`) and `EconomyListener`'s purse-loss penalty (priority `MONITOR`), so both observe
the already-resolved keepInventory/keepExperience policy:

1. Builds and sets the death message via `DeathMessageService.build(player)` (or `null` if
   `death.broadcast-enabled` is `false` — the cached context is still consumed either way, so it can't
   leak into the next unrelated hit's message).
2. Resolves `DeathPolicyResolver.Policy` and applies it: `event.setKeepInventory(...)`,
   `event.setKeepLevel(...)`. When `keepInventory` is true, `event.getDrops().clear()` is also called —
   `setKeepInventory(true)` alone still populates `getDrops()`, the exact pitfall the audit calls out.
   When `keepExperience` is true, `event.setDroppedExp(0)`.
3. Fires the `player:on_death` HookBus point if anything is registered there (§3.4).

`onRespawn` (priority `NORMAL`):

1. Resolves the zone the player died in (`DeathPolicyResolver.deathZone`, reads
   `player.getLocation()` — still the pre-teleport death location at this point in Bukkit's event
   flow) and, if that zone declares a `death.zone-respawn-overrides` entry, calls
   `event.setRespawnLocation(...)`. If unset, vanilla's own bed/anchor/world-spawn resolution is left
   completely untouched.
2. Fires `player:on_respawn` with `player:is_bed_spawn`/`player:is_anchor_spawn` context.

Note: the existing `module/stat/PlayerListener.onPlayerRespawn` (heal-to-full + stat recalculation)
was **not** moved here — that's `stat`'s job (resetting `PlayerState`), kept where it already lived
rather than centralizing everything into `death` for its own sake.

### 3.2 `DeathMessageService` + `DeathContextCache`

Death messages are built **entirely from Valmora's own state**, never from vanilla `DamageSource`:

- `DeathContextCache` (in `module/combat/`) is a `Map<UUID, Context>` (`type`, `attacker`, `weapon`)
  updated by `CombatListener` on every damage application to a player (both the entity-vs-entity and
  environmental paths), not just fatal ones — mirrors how vanilla's own `lastDamageCause` behaves.
  This exists because `player.getLastDamageCause()` can't be trusted: health is virtualized and death
  is forced via a direct `setHealth(0)` call outside the normal event flow, so relying on vanilla's own
  bookkeeping for "what actually killed this player" is fragile by construction.
- `DeathMessageService.build(player)` consumes (removes) that player's cached context, resolves a
  MiniMessage template from `death.messages.<DamageType id>` (falling back to `death.messages.default`),
  and substitutes `%victim%`/`%attacker%`/`%weapon%`/`%cause%`.

### 3.3 `DeathPolicyResolver`

Pure resolution logic, no listener of its own:

- `resolve(player)` → `Policy(keepInventory, keepExperience)`: the dying zone's
  `ZoneFlags.keepInventoryOnDeath`/`keepExperienceOnDeath` (nullable `Boolean`, `null` = inherit) if
  set, else `death.keep-inventory-default`/`death.keep-experience-default` from config (both ship
  `false` — matching vanilla's own default, not a game-design decision made for the server operator).
- `deathZone(player)` — the `ZoneDefinition` at the player's current location, or `null`.
- `resolveRespawnOverride(zoneId)` — parses `death.zone-respawn-overrides.<zoneId>: "world,x,y,z[,yaw]"`
  into a `Location`, logging a warning and returning `null` on any malformed/unknown-world entry.

### 3.4 `DeathPipelineLoader`

Mirrors `combat`'s `CombatPipelineLoader`/`PipelineYamlLoader` pattern: loads `death_pipeline.yml`
onto the shared `HookBus` under `player:on_death`/`player:on_respawn`. Kept **separate** from
`combat:on_death` (which `MobVariableProvider`'s docs tie specifically to mob deaths with a
`mob:level` context) — this is the player-death/respawn equivalent, not a reuse of that point.

### 3.5 Totem of Undying — `TotemProtectionService` (lives in `module/combat/`, not `death`)

**This was the single most non-obvious fix in the whole audit item.** Vanilla's totem-death-protection
check lives inside NMS's damage-application path (roughly `LivingEntity.actuallyHurt`/`die`) — but
that code path **never runs** in this plugin, because:

1. `CombatListener` always zeroes the real `EntityDamageEvent` damage (`event.setDamage(0)`).
2. Damage is instead applied to a virtual `PlayerState.currentHealth` double.
3. When that hits `<= 0`, `PlayerManager.syncVisualHealth` calls `player.setHealth(0)` **directly** —
   a raw health mutation, not a real damage event — which bypasses vanilla's totem check entirely.

So before this fix, holding a Totem of Undying did **nothing** against Valmora's own damage pipeline
— a totem in your inventory was purely decorative. `TotemProtectionService.tryProtect(player, state,
stats, incomingDamage)` is called from `DamageApplier.applyDamage()` **before** the health-reduction
step, and:

1. If the hit would be fatal (`state.getCurrentHealth() - incomingDamage <= 0`) and the player holds a
   totem in either hand, constructs and calls a real `EntityResurrectEvent(player, slot)` via
   `Bukkit.getPluginManager().callEvent(...)` — so any other plugin observing that event still sees it
   and can cancel it, exactly like a normal totem save.
2. If not cancelled: consumes one totem (preferring main hand), sets virtual health to the
   vanilla-equivalent "1 HP out of a 20-HP bar" fraction of the player's actual max-health stat,
   applies vanilla's totem potion-effect table (Regeneration II / Absorption I / Fire Resistance, 900/
   800/800 ticks), plays the totem-use sound, and calls `syncVisualHealth` itself.
3. `DamageApplier` skips its normal reduce/sync steps entirely when this returns `true`.

**Known limitation:** since the real vanilla resurrect code path never runs, the client's totem
pop-up animation (driven server-side by that same NMS path) does not play — only the sound and the
resulting effects/health are reproduced. Fixing that would need raw packet injection (PacketEvents is
already a hard dependency for NPC dialogue — see CLAUDE.md §14.1); judged not worth it for a one-off
visual flourish.

### 3.6 `BedListener` / `RespawnAnchorListener` / `PhantomInsomniaListener`

- **`BedListener.onBedEnter`** — cancels `PlayerBedEnterEvent` with a message if the player's zone has
  `ZoneFlags.sleeping() == false`.
- **`RespawnAnchorListener.onBlockExplode`** — this Paper/Bukkit version (1.21.11) has **no dedicated
  `RespawnAnchorExplodeEvent`/`BedExplodeEvent`** — both fire as a plain `BlockExplodeEvent` whose
  source block is `Material.RESPAWN_ANCHOR` or a bed (`Tag.BEDS`). Filters the destroyed-block list
  down to zones that allow block breaking (reusing `ZoneFlags.blockBreaking`, the same flag
  `ZoneListener` already enforces for normal mining) — deliberately **not** re-implementing the
  audit's much larger §4 general explosion-control system, and deliberately **not** touching player
  damage from the explosion, since `EntityDamageEvent` cause `BLOCK_EXPLOSION` already flows through
  `CombatListener.onEntityDamage` → `mapCauseToType` → `DamageType.EXPLOSION` unchanged.
- **`PhantomInsomniaListener.onPhantomPreSpawn`** — cancels the Paper-specific `PhantomPreSpawnEvent`
  when `death.phantoms-enabled` is `false` (global), or the target player's zone has
  `naturalMobSpawning() == false` (reused, not a redundant new flag). Does not touch
  `Statistic.TIME_SINCE_REST` — vanilla's own countdown/reset-on-sleep behavior is left untouched.

### 3.7 `ON_DEATH`/`ON_RESPAWN` ability triggers

`AbilityTrigger.ON_DEATH`/`ON_RESPAWN` added alongside the totem fix (a general framework extension,
not group-specific Java — consistent with the modifier framework's hard rule). Dispatched from
`AbilityTriggerListener`:

- `onDeath` (priority `HIGH`, after `death`'s own `NORMAL`-priority resolution) fires `ON_DEATH` for
  the player's held item + armor.
- `onRespawn` fires `ON_RESPAWN` a tick after respawn (mirrors `stat`'s own respawn-heal delay —
  equipment/inventory isn't necessarily settled at event-fire time).

---

## Configuration (YAML)

Lives under `death:` in `config.yml` (not a dedicated resource file — small enough to match how
`economy`/`combat` do it). Every toggle ships matching vanilla's own default:

```yaml
death:
  keep-inventory-default: false
  keep-experience-default: false
  broadcast-enabled: true
  phantoms-enabled: true
  messages:
    default: "<gray>%victim% died."
    MELEE: "<gray>%victim% was slain by %attacker%."
    # ... one entry per DamageType id (damage_types/*.yml), falls back to `default`
  zone-respawn-overrides: {}   # zone-id: "world,x,y,z[,yaw]"
```

See `src/main/resources/config.yml`'s `death:` block for the full shipped default set and inline
comments (including the note that this Paper version has no `PlayerDeathScreenEvent`, so the
audit's "death-screen duration" gap has no API hook to control it on and is left pure vanilla).

`ZoneFlags` (`module/zone/ZoneFlags.java`) gained three fields consumed by this module —
`keepInventoryOnDeath`/`keepExperienceOnDeath` (nullable `Boolean`) and `sleeping` (`boolean`,
default `true`) — see `docs/modules/design/zone.md` §3.7 for the full flag reference and
`/zone flag <id> <flag> <true|false|default>` for setting them in-game.

---

## Dependencies & Consumers

### Dependencies (runtime)

| Dependency | Why |
|---|---|
| `zone` (`ZoneManager`) | `DeathPolicyResolver`/`BedListener`/`RespawnAnchorListener`/`PhantomInsomniaListener` all resolve the player's/block's zone for flag overrides. |
| `combat` (`DeathContextCache`, `TotemProtectionService`) | Death-message attacker/weapon/type resolution; the totem fix lives in `combat` itself, called from `DamageApplier`. |
| `stat` | `PlayerListener.onPlayerRespawn` still owns the heal-to-full/stat-recalculation step — untouched by this module. |
| `economy`, `hud`, `quest` | Existing narrow `PlayerDeathEvent` listeners (purse loss, HUD-item strip, DIE trigger) — left as-is, ordered to run after this module's `NORMAL`-priority policy resolution. |
| `script` (`HookBus`) | `player:on_death`/`player:on_respawn` insertion points. |

### Consumers

None yet beyond the above — `death` is a leaf module in the registration order (registered right
after `zone`, before `resource`).

---

## Unfinished Things / Deliberately Out of Scope

- **Vanilla `DamageSource`-based death messages** — considered and explicitly rejected (user decision,
  see `docs/VANILLA_CONTROL_AUDIT_PROGRESS.md`): fighting the virtual-health architecture for no
  practical benefit over building messages from Valmora's own already-computed damage context.
- **Void-damage rescue/anti-void platform** — explicitly declined (user decision). Void damage kills
  exactly like vanilla; it only gets a correct custom death message (`death.messages.VOID`).
- **General explosion/block-destruction control** (audit §4) — respawn-anchor/bed explosions only get
  zone block-protection filtering, not the full system (TNT, creepers, etc. untouched).
- **`PlayerDeathScreenEvent`** — doesn't exist in this Paper API version (1.21.11); the audit's
  "death-screen duration" gap has no hook to control it on.
- **Cross-world/portal events**, **full vanilla XP-curve abstraction** — separate, larger audit items
  (§10, §11 item 21), not part of "death."
