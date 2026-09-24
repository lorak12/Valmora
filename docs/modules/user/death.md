# Death Module — User Documentation

> **Module ID:** `death` | **Module Name:** "Death & Respawn"
> **Config:** `death:` section in `plugins/Valmora/config.yml`
> **Related:** `/zone flag` for per-zone overrides — see `docs/modules/user/zone.md`
> **Hot reload:** `/valmora reload` (requires `valmora.admin`)

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The Death module gives full control over what happens when a player dies and respawns:

- A **custom death message** broadcast to the server (or off entirely), styled per damage type.
- Whether **items and XP are kept** on death — server-wide, with an optional override per zone.
- **Totem of Undying** actually works (it previously did nothing on this server — see the Admin
  Guide's technical note if you're curious why).
- An optional **custom respawn point** for a zone.
- Sleeping can be **disabled in specific zones** (e.g. a boss arena).
- **Phantoms** (the insomnia mob) can be disabled server-wide or per zone.

---

## Player Guide

- When you die, a message is broadcast (unless the server disabled it) describing how — e.g. *"Steve
  was slain by Alex"*, *"Steve hit the ground too hard"*, *"Steve fell into the void"*.
- Whether your items and XP survive death depends on the server's settings and which zone you died
  in — check with your server admin, or just watch what happens the first time.
- **Totem of Undying** works normally: hold one in either hand, and it saves you from a fatal hit,
  consuming the totem and leaving you at a sliver of health with the usual Regeneration/Absorption/
  Fire Resistance effects. (The totem's pop-up animation doesn't play visually on this server — a
  known cosmetic limitation — but the save itself works.)
- Some zones may not let you sleep in a bed (a message will tell you if so).

---

## Admin Guide

### Permissions

No dedicated permission — this module has no commands of its own. Zone-level overrides go through
`/zone flag`, gated by the existing `valmora.admin` (or your configured `permissions.zone` node — see
`docs/modules/user/zone.md`).

### Setting the server-wide death policy

Edit `death.keep-inventory-default`/`death.keep-experience-default` in `config.yml` (both default
`false`, matching vanilla), then `/valmora reload`.

### Overriding death policy for one zone

```
/zone flag <id> keep-inventory-on-death true
/zone flag <id> keep-experience-on-death true
/zone flag <id> keep-inventory-on-death default   # clear the override, inherit the server default again
```

### Writing custom death messages

Edit `death.messages.<DAMAGE_TYPE_ID>` in `config.yml` — the type ids come from `damage_types/*.yml`
(built-ins: `MELEE`, `PROJECTILE`, `FALL`, `VOID`, `FIRE`, `LAVA`, `DROWNING`, `STARVATION`,
`LIGHTNING`, `EXPLOSION`, `MAGIC`, `WITHER`, `DRAGON_BREATH`, `SONIC_BOOM`, `CONTACT`,
`OUTSIDE_BORDER`, `FREEZE`, `SUICIDE`, plus any custom type your pack defines). Any type without its
own entry falls back to `death.messages.default`. Placeholders:

| Placeholder | Meaning |
|---|---|
| `%victim%` | The player who died. |
| `%attacker%` | The killer's name (blank if there wasn't one — fall damage, void, etc.). |
| `%weapon%` | The attacker's held item's display name (blank if none/not applicable). |
| `%cause%` | The raw damage-type id. |

Set `death.broadcast-enabled: false` to silence death messages entirely.

### Disabling sleep in a zone

```
/zone flag <id> sleeping false
```

### Disabling phantoms

Globally: set `death.phantoms-enabled: false` in `config.yml`. Per zone: a zone with
`natural-mob-spawning: false` (`/zone flag <id> natural-mob-spawning false`) also blocks phantoms
there, since a phantom spawn is itself a natural spawn.

### Custom respawn point for a zone

```yaml
death:
  zone-respawn-overrides:
    dungeon_arena: "world,100.5,64,200.5,180"   # world,x,y,z[,yaw]
```

A player who dies inside that zone respawns there instead of at their bed/anchor/world spawn.
Leave a zone out of this map to keep vanilla's own respawn-location resolution (bed → respawn
anchor → world spawn) — that's the default for every zone.

### Technical note: why the totem fix mattered

This server tracks player health separately from vanilla ("virtual health"), which means vanilla's
own Totem of Undying protection never actually ran — the totem sat in your inventory doing nothing.
This was fixed as part of this module's rollout; see `docs/modules/design/death.md` §3.5 if you want
the full technical explanation.

---

## Configuration Reference

```yaml
death:
  keep-inventory-default: false
  keep-experience-default: false
  broadcast-enabled: true
  phantoms-enabled: true
  messages:
    default: "<gray>%victim% died."
    MELEE: "<gray>%victim% was slain by %attacker%."
    PROJECTILE: "<gray>%victim% was shot by %attacker%."
    FALL: "<gray>%victim% hit the ground too hard."
    VOID: "<gray>%victim% fell into the void."
    FIRE: "<gray>%victim% went up in flames."
    LAVA: "<gray>%victim% tried to swim in lava."
    DROWNING: "<gray>%victim% drowned."
    STARVATION: "<gray>%victim% starved to death."
    LIGHTNING: "<gray>%victim% was struck by lightning."
    EXPLOSION: "<gray>%victim% blew up."
  zone-respawn-overrides: {}
```

| Key | Default | Meaning |
|---|---|---|
| `keep-inventory-default` | `false` | Whether items are kept on death, absent a zone override. |
| `keep-experience-default` | `false` | Whether XP is kept on death, absent a zone override. |
| `broadcast-enabled` | `true` | Whether the death message is broadcast server-wide. |
| `phantoms-enabled` | `true` | Set `false` to disable phantom spawning entirely. |
| `messages.<TYPE>` / `messages.default` | *(see above)* | MiniMessage death-message templates. |
| `zone-respawn-overrides.<zone-id>` | *(none)* | `"world,x,y,z[,yaw]"` — custom respawn point for deaths in that zone. |

**Related zone flags** (`/zone flag <id> <flag> <value>`, see `docs/modules/user/zone.md`):

| Flag | Default | Meaning |
|---|---|---|
| `sleeping` | `true` | `false` blocks bed entry in this zone. |
| `keep-inventory-on-death` | *(unset)* | `true`/`false`/`default` — overrides the server-wide setting for this zone. |
| `keep-experience-on-death` | *(unset)* | Same, for kept XP. |
