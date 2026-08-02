# Profile Module — User Documentation

> **Version:** 0.1 | **Server:** Paper 1.21.x | **Command:** `/profile`

---

## Overview

Profiles are Valmora's **character save slots**. Every player gets a set of profiles, and each
profile is a completely independent character: its own stats, skill levels, collection progress,
persistent flags (tags), custom variables, health/mana, and even its own copy of your inventory.

- A **Default profile** is created automatically the first time you join (named `Earth` by default).
- Only **one profile is active at a time** — the profile you log in with is the one that gets played.
- You can **create, switch, and delete** profiles at any time, both from chat commands and from a
  graphical menu.
- Everything inside a profile is saved to the database, so your progress survives restarts.

Profiles are the backbone of almost every other Valmora feature: stats, skills, quests, collections,
and more all read from your **active profile**.

---

## Player Guide

### What lives inside a profile

Each profile independently stores:

| Data | Notes |
|---|---|
| **Stats** | Base + effective stat values (health, mana, damage, defense, luck, …). |
| **Skills** | Skill XP and levels (combat, mining, fishing, …). |
| **Health & Mana** | Your current HP/Mana pool for that profile. |
| **Tags** | Persistent per-profile flags used by quests and scripts. |
| **Custom variables** | `player.var.<name>` values used by scripts, quests, and the economy's legacy coins. |
| **Collections** | Collection progress. |
| **Inventory snapshot** | Storage, armor, and offhand slots are saved per profile and swapped when you switch. |
| **Accessory bag & quiver** | The 45-slot accessory bag and 27-slot quiver are per-profile. |

Things that are **shared across all of your profiles**: your UUID-based identity, and your **coins**
(the economy purse/bank balance, `docs/modules/user/economy.md`).

### Your first join

When you join for the first time, Valmora automatically creates your **Default profile**
(`profiles.default-name` in `config.yml`, default `Earth`) and sets your health to your max health
(`PlayerManager.java:63-74`). From then on, you are in control.

### The `/profile` command

Run from chat, no permission required (`plugin.yml:10-11`):

| Command | What it does |
|---|---|
| `/profile` | Shows usage: `create`, `delete`, `switch`, `list`, `info`, `gui`. |
| `/profile gui` | Opens the graphical **Profile Manager** menu (see below). |
| `/profile create <name>` | Creates a new empty profile with the given name (`ProfileCommand.java:41-49`). |
| `/profile delete <name>` | Deletes the named profile — **permanently** (`ProfileCommand.java:50-58`). |
| `/profile switch <name>` | Switches your active profile to the named one (`ProfileCommand.java:59-67`). |
| `/profile list` | Lists all your profiles; the active one is marked `[ACTIVE]` in green (`ProfileCommand.java:68-77`). |
| `/profile info` | Shows the active profile's ID, name, health/mana, and combat status (`ProfileCommand.java:79-99`). |

> **Tip:** `/profile delete` accepts profile *names*; if you have two profiles with the same name,
> the first match is used. `/profile gui` is the safest way to manage profiles because it refuses to
> delete the profile you are currently playing.

### Profile switching and your inventory

When you switch profiles, Valmora saves your current profile's **inventory** (storage, armor,
offhand) and loads the new profile's inventory in its place (`PlayerManager.java:139-163`). Your
inventory therefore follows your profile — no need to stash your gear manually. The same swap happens
when you join: you log in with the inventory snapshot of whichever profile is active.

> **Note:** the accessory bag contents and quiver are also per-profile, so switching profiles swaps
> those too.

### The Profile Manager GUI (`/profile gui`)

A 36-slot menu titled **Profiles** (`ProfileGui.java:38-43`):

- **Profile cards** show health/mana, total skill level, your coins, and last-used time
  (`ProfileGui.java:171-221`).
- **Left-click a profile** to switch to it (blocked if it is already active).
- **Shift-click a profile** to delete it — this opens a confirmation dialog first, and is refused for
  your **active** profile or your **only** profile (`ProfileGui.java:334-360`).
- The **Create Profile** button (lime dye) adds a profile with a random name from the configured
  `planet-names` pool (`ProfileGui.java:310-323`).
- The **Close** button (barrier) closes the menu.

### Maximum number of profiles

Players are limited to `profiles.max-profiles` profiles (default **4**, `config.yml:43-45`). The
GUI displays exactly 4 profile slots, so profiles created while over the visual cap are only
reachable via `/profile switch <name>`.

### What resets if you delete a profile

Everything **inside** the deleted profile is gone for good — its stats, skills, tags, variables,
collections, inventory snapshot, accessory bag, and quiver. Your **coins** are shared per player and
are **not** deleted with a profile.

---

## Admin Guide

### Permissions

| Permission | Effect |
|---|---|
| *(none)* | `/profile` and `/profile gui` require **no** permission — every player manages their own profiles (`plugin.yml:10-11`). |
| `valmora.admin` | Required for `/stat add` and `/stat remove` (which edit the active profile's stats, `StatCommand.java:61`, `:84`) and `/valmora reload`. |
| `valmora.admin` | Required for `/item`, `/mob`, `/eco`, `/zone`, `/npc`, `/valmora` — the admin tools that *indirectly* manipulate profiles. |

### Database considerations

Profile data is stored in the plugin database (`plugins/Valmora/database.db` by default, or your
configured MySQL server):

- `valmora_players` — one row per player, holding the currently **active** profile UUID
  (`SQLDataStore.java:124-129`).
- `valmora_profiles` — one row per profile, holding stats, skills, health/mana state, tags,
  variables, collections, inventory, quiver, creation time, and last-used time
  (`SQLDataStore.java:131-144`, v2 quiver column `:117-120`).
- The schema is versioned (`valmora_schema_version`) and migrates automatically on startup;
  upgrading Valmora from an older version migrates existing data in place (`SQLDataStore.java:50-72`,
  `:104-115`).

Save behaviour:

- **On quit**, the profile is saved asynchronously (`PlayerManager.java:127-137`).
- **On `/valmora reload` and on a clean server shutdown**, every online session is flushed to the
  database before reload/close (`PlayerManager.java:116-118`, `Valmora.java:270-274`).
- **MySQL:** set `database.type: mysql` and the `database.mysql.*` keys in `config.yml`
  (`config.yml:5-28`). Use it when syncing profiles across a network of servers.
- **SQLite WAL mode** is enabled automatically for better concurrency (`DatabaseFactory.java:39-41`).

### Reload behaviour

`/valmora reload` (requires `valmora.admin`, `Valmora.java:232`) runs the module disable/enable
cycle. Online players are re-loaded **synchronously** from the database to avoid a gap where no
session exists (`PlayerManager.java:50-53`). Player progress is preserved across reloads because all
profile data is database-backed.

### Known limitations to be aware of

- **Deleting your active profile via `/profile delete <name>` is not blocked** by the command (only
  the GUI blocks it). Deleting the profile you are currently playing can leave you with no active
  profile until you rejoin. Prefer `/profile gui` for deletions.
- **Cooldowns, combat timer, and current-zone** are in-memory only and reset on reload
  (`PlayerState.java:10-11`, `ValmoraProfile.java:23`).
- **`max-profiles` above 4:** the GUI only shows 4 profile slots; extra profiles must be switched
  to by name.

---

## Configuration Reference

The Profile module is configured in `config.yml` under the `profiles:` section
(`src/main/resources/config.yml:40-63`):

| Key | Default | Type | Description |
|---|---|---|---|
| `profiles.max-profiles` | `4` | int | Maximum number of profiles a player can have. The GUI displays 4 slots regardless. |
| `profiles.default-name` | `Earth` | string | Name of the profile created automatically for a brand-new player. |
| `profiles.planet-names` | `Mars, Venus, Jupiter, Saturn, Mercury, Neptune, Uranus, Pluto, Kepler-22b, Proxima b, Titan, Europa` | string list | Names randomly picked (only unused ones) when a player clicks **Create Profile** in the GUI. Falls back to `Profile N` if the pool runs out. |

Example:

```yaml
profiles:
  max-profiles: 4
  default-name: Earth
  planet-names:
    - Mars
    - Venus
    - Jupiter
    - Saturn
```

There is **no** per-profile YAML folder — profiles are created and stored entirely in the database.
The only other config that affects this module is the database block (`database.type`,
`database.mysql.*`), which controls where profile data lives.
