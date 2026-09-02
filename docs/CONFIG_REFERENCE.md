# config.yml — Field-by-Field Reference

> **File:** `plugins/Valmora/config.yml` (ships as `src/main/resources/config.yml`)
> **Reload:** `/valmora reload` (requires the `reload` permission node, see [Permissions](#permissions)) applies every field below without a server restart, unless a field's row says otherwise.
>
> This page documents every key that exists in the shipped `config.yml`, in the same top-to-bottom
> order as the file itself. Each section starts with the YAML path prefix; every row below it is
> relative to that prefix. Where a field carries an `HC-xxx` tag in the file, that's a cross-reference
> to `docs/HARDCODED_VALUES_AUDIT.md` / `docs/HARDCODED_VALUES_DECISIONS.md` — the audit that decided
> the value was worth exposing as a config key; it's not something you need to look up to use the field.
>
> For the module-specific *behavior* these numbers feed into (not just what the field does in
> isolation), see the matching `docs/modules/user/<module>.md` / `docs/modules/design/<module>.md`
> page linked at the end of each section.

---

## Table of Contents

1. [database](#database)
2. [permissions](#permissions)
3. [economy](#economy)
4. [profiles](#profiles)
5. [time](#time)
6. [progression](#progression)
7. [combat](#combat)
8. [skills](#skills)
9. [mobs](#mobs)
10. [mining](#mining)
11. [resource](#resource)
12. [quests](#quests)
13. [scripting](#scripting)
14. [npc-skin-server](#npc-skin-server)
15. [anvil](#anvil)
16. [recipes](#recipes)
17. [pack](#pack)
18. [items](#items)
19. [mechanics](#mechanics)
20. [enchants](#enchants)
21. [pets](#pets)
22. [modifiers](#modifiers)
23. [alchemy](#alchemy)
24. [fishing](#fishing)
25. [notify](#notify)
26. [zones](#zones)
27. [warps](#warps)
28. [gui](#gui)
29. [ui](#ui)
30. [hud](#hud)
31. [npc / dialogue](#npc--dialogue)

---

## `database`

Backs the plugin's HikariCP-pooled data layer (`org.nakii.valmora.database`, see
[CLAUDE.md §11](../CLAUDE.md#11-database-layer)).

| Field | Default | Meaning |
|---|---|---|
| `type` | `sqlite` | Engine to use: `sqlite` (zero-setup, local file, recommended for most servers) or `mysql` (recommended for networks syncing multiple servers). |
| `pool.maximum-pool-size` | `10` | Max HikariCP connections. Raise on large/high-concurrency networks; lower on tiny single-world servers to save RAM/handles. |
| `worker-threads` | `4` | Size of the dedicated async executor database calls run on (see [CLAUDE.md §7.4](../CLAUDE.md#74-async-operations) — never touch Bukkit API from these threads). |
| `shutdown-timeout-seconds` | `10` | How long the plugin waits for in-flight DB work to finish during shutdown/reload before giving up. |
| `mysql.prep-cache-size` | `250` | MySQL JDBC driver prepared-statement cache size. Only used when `type: mysql`. |
| `mysql.prep-cache-sql-limit` | `2048` | Max SQL string length MySQL's prepared-statement cache will cache. Only used when `type: mysql`. |
| `mysql.host` / `port` / `database` / `username` / `password` | *commented out* | MySQL connection details. Uncomment and fill in only when `type: mysql`. |
| `mysql.use-ssl` | `false` (commented) | Whether the MySQL connection requires/serves over SSL/TLS. |

---

## `permissions`

One permission node per admin command, each falling back to `admin` and then the literal
`valmora.admin` if unset — lets you hand a staff role one admin command without granting all of
them. Leaving this section untouched keeps the original single-permission behavior exactly as it
was (every command gated on `valmora.admin`).

| Field | Default | Gates |
|---|---|---|
| `admin` | `valmora.admin` | Fallback used by every command below that isn't individually overridden. |
| `reload` | `valmora.admin` | `/valmora reload` |
| `pack` | `valmora.admin` | `/valmora pack ...` |
| `eco` | `valmora.admin` | `/eco` admin subcommands |
| `calendar` | `valmora.admin` | `/calendar` |
| `collection` | `valmora.admin` | `/collection` admin subcommands |
| `alchemy` | `valmora.admin` | `/potion` (alchemy) admin subcommands |
| `modifier` | `valmora.admin` | `/modifier` |
| `zone` | `valmora.admin` | `/zone` |
| `npc` | `valmora.admin` | `/npc` |
| `warp` | `valmora.admin` | `/warp` admin subcommands |
| `pet` | `valmora.admin` | `/pet` admin subcommands |
| `stat` | `valmora.admin` | `/stat` admin subcommands |
| `quest` | `valmora.admin` | `/quest` admin subcommands |
| `time` | `valmora.admin` | `/time` (Valmora calendar clock, not vanilla) |
| `skill` | `valmora.admin` | `/skill` admin subcommands |

Resolution is handled by `util/PermissionResolver.java`; set any one key to a distinct permission
node (e.g. `permissions.eco: "valmora.staff.eco"`) to split it out from the general admin node.

---

## `economy`

| Field | Default | Meaning |
|---|---|---|
| `autosave-interval-seconds` | `60` | How often dirty balances are flushed to the database in one batched transaction. Balances always live in memory and are safe to use between flushes; this only bounds how much could be lost on an *unclean* crash — a clean shutdown/reload always flushes everything immediately regardless. |
| `death-loss-percent` | `50.0` | Percentage (0–100) of a player's **purse** lost on death. Bank balances are never touched. |
| `bank-interest-percent` | `0.5` | Flat-rate interest applied to every online/cached player's **bank** balance each interval below. `0` disables interest entirely (no task is even scheduled). |
| `bank-interest-interval-seconds` | `60` | How often bank interest is applied. |
| `ledger-retention-per-player` | `10` | How many ledger rows are actually **kept** in the database per player (GDPR/verbosity control). Distinct from `ledger-display-limit` below, which only controls the GUI's display window. |
| `ledger-display-limit` | `5` | How many recent transactions the bank GUI's "Recent Transactions" list shows. |
| `format.thousand` | `"%.1fk"` | `String.format` pattern for compact-suffix display in the 1,000–999,999 range. |
| `format.million` | `"%.2fm"` | Same, for the millions range. |
| `format.billion` | `"%.2fb"` | Same, for the billions range. |
| `format.coin-symbol` | `"🪙 "` | Prefix/branding symbol shown before formatted coin amounts. |
| `format.thousands-separator` | `"."` | Separator used in the exact/dot-separated display form (e.g. `10.000`). |
| `messages.no-transactions` | `"<gray>There are no recent transactions!"` | Shown in the bank GUI ledger list when a player has no transactions yet. |
| `messages.deposit-verb` | `"Deposited"` | Verb used in deposit confirmation text. |
| `messages.withdraw-verb` | `"Withdrew"` | Verb used in withdraw confirmation text. |
| `bank-messages.prefix` | `"<dark_gray>[<gold>Bank<dark_gray>] "` | Chat prefix on bank-related messages. |
| `deposit-halving` | `floor` | Rounding mode (`floor` \| `ceil` \| `round`) used when a player deposits/withdraws "half" their balance. |
| `tab-complete-amounts` | `["1000","1k","10k","100k","1m"]` | Suggestions offered when tab-completing an amount argument on `/eco`-family commands. |
| `coin-suffixes` | `{k: 1000, m: 1000000, b: 1000000000}` | Suffix → multiplier map used when parsing a typed coin expression like `2.5k`. Add e.g. `t: 1000000000000` for trillions. |

See `docs/modules/user/economy.md`.

---

## `profiles`

| Field | Default | Meaning |
|---|---|---|
| `max-profiles` | `4` | Max number of profiles (separate save-slots) a single player account can have. |
| `default-name` | `Earth` | Name given to the very first profile auto-created for a brand-new player. |
| `planet-names` | 12 names (`Mars`, `Venus`, `Jupiter`, `Saturn`, `Mercury`, `Neptune`, `Uranus`, `Pluto`, `Kepler-22b`, `Proxima b`, `Titan`, `Europa`) | Pool randomly drawn from (unused names only) when naming a player's 2nd+ profile. |

---

## `time`

Drives the RPG in-game calendar (`module/time/TimeManager.java`), independent of vanilla
day/night.

| Field | Default | Meaning |
|---|---|---|
| `world` | `world` | Which world's `world.getTime()` clock the hour/minute display is read from. |
| `start-year` | `1` | Calendar year applied on the very first server launch. Ignored after that — actual progress is persisted in `plugins/Valmora/time.yml`. |
| `start-season` | `SPRING` | One of `SPRING` \| `SUMMER` \| `AUTUMN` \| `WINTER`. |
| `start-phase` | `EARLY` | One of `EARLY` \| `MID` \| `LATE` — the "month" within a season. |
| `start-day` | `1` | Day within the phase, 1–30 (or 1–`calendar.days-per-phase`, see below). |
| `season-names` | `[Spring, Summer, Autumn, Winter]` | Display names used in the scoreboard and `$time.season$`-style variables. |
| `phase-names` | `[Early, Mid, Late]` | Display names for the phase. |
| `scoreboard-enabled` | `true` | Whether time lines are shown on the sidebar scoreboard. |
| `calendar.days-per-phase` | `30` | Days per phase — safe to retune (e.g. `28` for a "28-day month" calendar). `phases-per-season` (3) and `seasons-per-year` (4) are **not** independently configurable; they're fixed by the `Phase`/`Season` enums and `days-per-year` derives automatically from this one value. |

---

## `progression`

| Field | Default | Meaning |
|---|---|---|
| `refund-percent` | `100.0` | Percentage of every point ever spent on a progression tree that's refunded on `/progression reset`. |
| `daily-bonus.window-hours` | `24` | Cadence (hours) of the daily progression-node bonus window. |
| `daily-bonus.check-interval-minutes` | `5` | How often the daily-bonus task polls to see if the window has rolled over. |

---

## `combat`

### Stat-role mapping

Maps the engine's internal combat roles onto stat IDs defined in `stats/*.yml` — change these only
if you rename or replace a core stat.

| Field | Default |
|---|---|
| `health-stat` | `health` |
| `mana-stat` | `mana` |
| `damage-stat` | `damage` |
| `strength-stat` | `strength` |
| `defense-stat` | `defense` |
| `crit-chance-stat` | `crit_chance` |
| `crit-damage-stat` | `crit_damage` |
| `speed-stat` | `speed` |
| `health-regen-stat` | `health_regen` |
| `mana-regen-stat` | `mana_regen` |
| `luck-stat` | `luck` |

### Damage / regen tuning

| Field | Default | Meaning |
|---|---|---|
| `environment-damage-multiplier` | `5.0` | Multiplier applied to raw vanilla environmental damage (fall/fire/lava/drowning/etc.) before defense mitigation, so it hits meaningfully hard against the RPG stat curve. |
| `damage-indicator-rate-limit-ms` | `400` | Minimum time between floating damage-indicator spawns per victim entity — protects against spam from rapid DoT ticks. |
| `damage-indicator-lifetime-ticks` | `20` | How long a floating damage indicator stays before despawning. |
| `post-hit-no-damage-ticks` | `20` | Vanilla invulnerability ticks applied after a hit lands, preventing overlapping DoT triggers from double-counting the same tick. |
| `combat-window-ms` | `3000` | How long a player is considered "in combat" after dealing or taking damage. |
| `visual-health-hearts` | `10` | Number of vanilla hearts the visual health bar is scaled to (independent of the player's actual `MAX_HEALTH` stat, which can be much higher). |
| `regen-interval-ticks` | `20` | How often the passive health/mana regen tick runs. `20` = once per second. |
| `regen.health-in-combat` | `false` | Whether health regen is blocked while "in combat" (see `combat-window-ms`). |
| `regen.mana-in-combat` | `true` | Whether mana regen is blocked while in combat. |
| `iframe-threshold-factor` | `0.5` | Fraction of a victim's max no-damage-ticks under which a second hit is rejected as an i-frame double-hit (anti multi-hit/DoT-stacking guard). `0.5` = half of vanilla invulnerability. |
| `environment.fallback-damage-type` | `MELEE` | Damage-type id used for an environmental damage cause with no explicit mapping. |
| `fallback-base-damage` | `1.0` | Base damage used for an attacker that's neither a player nor a registered `MobDefinition` (plain vanilla mobs). |
| `cause-mapping` | `{}` | `Bukkit.DamageCause` → Valmora damage-type-id overrides, checked before the built-in mapping in `CombatListener`. Example: `NECRO_ATTACK: poison`. |
| `damage-indicator.offset` | `0.5` | Vertical spawn offset (blocks) for floating damage indicators. |
| `damage-indicator.crit-format` | `"<gold>✧ {color}<b>{damage}<gold> ✧"` | MiniMessage format for a critical-hit indicator. Placeholders: `{color}`, `{damage}`. |
| `damage-indicator.normal-format` | `"{color}{damage}"` | MiniMessage format for a normal-hit indicator. |
| `damage-indicator.show-as-int` | `true` | Whether `{damage}` is rounded to an integer instead of showing decimals. |

See `docs/modules/user/combat.md`.

---

## `skills`

| Field | Default | Meaning |
|---|---|---|
| `defaults.max-level` | `60` | Central fallback max level used when a skill's own `skills/*.yml` omits `max-level`. |
| `defaults.xp-curve` | `default` | Fallback XP-curve id used when a skill's own YAML omits `xp-curve`. |
| `curves.default-max-level` | `60` | Fallback max-level for a formula-based XP curve (`xp_curves.yml`) whose own entry omits `max-level`. |

---

## `mobs`

| Field | Default | Meaning |
|---|---|---|
| `tasks.ai-interval-ticks` | `40` | How often (ticks) the leash/AI task polls. Direct CPU knob on populated servers. |
| `tasks.natural-spawn-interval-ticks` | `200` | How often (ticks) the ambient natural-spawn task runs. |
| `natural-spawn.search-radius` | `32.0` | Radius (blocks) searched for existing same-mob nearby entities before allowing another natural spawn. |
| `natural-spawn.min-distance` | `8.0` | Minimum distance (blocks) from the player a candidate spawn point is picked at. |
| `natural-spawn.max-distance` | `24.0` | Maximum distance (blocks) from the player a candidate spawn point is picked at. |
| `boss.tick-period-ticks` | `10` | How often (ticks) boss-mob logic (bar update, announce checks) runs. |
| `boss.announce-radius` | `40.0` | Radius (blocks) within which players are announced a boss's presence/actions. |
| `boss-bar.default-range` | `40.0` | Default boss-bar visibility range when a boss's own definition doesn't set one. |
| `damage-scaling` | `""` (blank) | Optional `Expression` for damage-per-level scaling; variables `$mob.base_damage$`/`$mob.level$` available. Blank keeps the built-in linear default: `baseDamage + (level-1)`. Example: `"$mob.base_damage$ + ($mob.level$ - 1)"`. |
| `xp-reward-formula` | `""` (blank) | Optional `Expression` for XP-per-level scaling; `$mob.base_xp$`/`$mob.level$` available. Blank keeps the built-in default: `baseXp * level`. |
| `combat-skill-id` | `combat` | Which skill id mob kills grant XP to. |
| `defaults.natural-spawn-chance` | `0.1` | Global fallback when a mob's own YAML omits `natural-spawn-chance`. |
| `defaults.natural-spawn-max-nearby` | `3` | Global fallback for `natural-spawn-max-nearby`. |
| `defaults.base-damage` | `5.0` | Global fallback for `base-damage`. |
| `defaults.base-xp` | `2` | Global fallback for `base-xp`. |
| `defaults.gold-reward` | `0` | Global fallback for `gold-reward`. |
| `ai.leash-return-speed` | `1.0` | Pathfinder speed multiplier used when a mob returns toward its leash/home point. |
| `loot.luck-divisor` | `100.0` | Divisor applied to the `luck` stat for loot-chance bonuses. `100.0` = 1 luck point gives +1% loot chance. |
| `abilities.defaults.interval` | `100` | Global fallback (ticks) for a mob-ability YAML that omits `interval`. |
| `abilities.defaults.chance` | `1.0` | Global fallback for `chance`. |
| `abilities.defaults.health-percent` | `50.0` | Global fallback for `health-percent`-gated abilities. |

See `docs/modules/user/mob.md`.

---

## `mining`

Stat-role mapping used by the mining/tool-breaking system — change only if you rename these stats.

| Field | Default |
|---|---|
| `mining-fortune-stat` | `mining_fortune` |
| `mining-speed-stat` | `mining_speed` |
| `breaking-power-stat` | `breaking_power` |
| `mining-spread-stat` | `mining_spread` |

---

## `resource`

| Field | Default | Meaning |
|---|---|---|
| `autosave-interval-seconds` | `30` | How often mid-progress resource-block state (mining node depletion, regen timers) is flushed to disk for crash recovery. |
| `limits.min-regen-delay-ticks` | `20` | Floors a misconfigured resource node's `regen-delay` so a typo (e.g. `regen-delay: 1`) can't schedule a near-per-tick regen task. |

---

## `quests`

| Field | Default | Meaning |
|---|---|---|
| `poll.timer-interval-ticks` | `20` | How often (ticks) `TIMER` objectives are polled. Cost scales O(players × active quests) — raise on large servers with many concurrent timer objectives. |
| `poll.npcrange-interval-ticks` | `20` | Same, for `NPCRANGE` objectives. |
| `limits.delay-min-interval-ticks` | `20` | Floors a `DELAY` objective's `interval:` so a typo can't schedule a near-per-tick task for the whole delay duration. |

---

## `scripting`

| Field | Default | Meaning |
|---|---|---|
| `limits.max-expression-depth` | `100` | Max recursion/nesting depth the expression parser accepts (nested parens, function args, unary minus) before rejecting an expression as malformed — protects the main thread from a `StackOverflowError` caused by a malformed or malicious content-pack formula. |

---

## `npc-skin-server`

Enables a tiny built-in HTTP server so admins can apply skins from PNG files placed in
`plugins/Valmora/skins/`, via `/npc skin <id> file <filename.png>`.

| Field | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Whether the server starts at all. |
| `port` | `2525` | Port it listens on. |
| `host` | *unset (auto-detect)* | Set to your public IP if clients connect from outside the local network. |

---

## `anvil`

Unified anvil tunables (see `docs/modules/design/recipe.md` §"The Unified Anvil"). Costs are in XP
levels; the "prior work penalty" is charged on top of these automatically.

| Field | Default | Meaning |
|---|---|---|
| `templates.merge.cost-per-level` | `2` | XP levels charged per enchant level merged/transferred. |
| `templates.merge.base-cost` | `0` | Flat XP levels charged on every gear+gear/book merge. |
| `templates.merge.durability-bonus-percent` | `0.12` | Gear+gear durability-merge bonus, as a fraction of max durability. |
| `templates.repair.base-cost` | `0` | Flat XP levels charged on every repair. |
| `templates.repair.percent-per-unit` | `0.25` | % of max durability repaired per consumed repair-material unit. |
| `repair-materials` | `{}` | Tool-material substring → repair material, checked **before** the built-in hint map (`NETHERITE`/`DIAMOND`/`GOLD`/`IRON`/`STONE`/`LEATHER`/`TURTLE`). Add entries for custom tool tiers (e.g. content-pack alloys) without a code change. |
| `prior-work.max-work-clamp` | `30` | Overflow-safety clamp on the `2^work - 1` "prior work" penalty curve. The curve shape itself mirrors vanilla's own anvil escalation math and isn't meant to be retuned lightly — this only bounds it. |

---

## `recipes`

| Field | Default | Meaning |
|---|---|---|
| `vanilla-fallback-machines` | `["crafting_table"]` | Machine ids that fall through to standard Bukkit/vanilla crafting recipes when nothing else matches, so e.g. an anvil/forge/alchemy GUI never silently matches a vanilla recipe. |

---

## `pack`

Guards for `/valmora pack install <url|github:owner/repo@tag>` (`docs/modules/design/pack.md` §4).

| Field | Default | Meaning |
|---|---|---|
| `max-extracted-size-mb` | `200` | Refuses to extract a downloaded pack archive past this total uncompressed size — a zip-bomb guard, checked incrementally while extracting (not just the archive's own reported sizes). |
| `max-entries` | `5000` | Refuses to extract an archive with more than this many entries — a second, independent zip-bomb guard (many tiny files can also exhaust disk/inodes). |
| `index-url` | `""` (blank) | Optional base URL of a JSON pack index, letting admins run `/valmora pack install <id>` with a bare pack id instead of a full URL. Unused until the index feature is built. |
| `download.user-agent` | `"Valmora-PackManager/1.0"` | User-Agent header sent on outbound pack-download HTTP requests. |

---

## `items`

Controls item-translation/stat-scaling and how a generated item's lore is composed. See
`docs/modules/user/item.md` §"Item Lore Layout" for the full block-by-block breakdown.

| Field | Default | Meaning |
|---|---|---|
| `breaking-power.<tier>` | `netherite:5, diamond:4, iron:3, stone:2, wood:1` | Tool-tier substring → breaking power (which blocks a tool can mine). |
| `vanilla-rarity-mapping.<material>` | `NETHERITE:MYTHIC, DIAMOND:EPIC, GOLDEN:RARE, IRON:UNCOMMON` | Vanilla material substring → rarity, used the first time a plain vanilla item is translated into a Valmora item. Checked in order: netherite/elytra, diamond/trident, golden/enchanted, iron. |
| `target-resolver.defaults.enemies-radius` | `5.0` | Default radius for the `@enemies_in_radius`/`@allies_in_radius` target selectors, used only when an ability's own selector args don't set one. |
| `target-resolver.defaults.cone-range` | `8.0` | Default range for the `@cone` selector. |
| `target-resolver.defaults.cone-angle` | `45.0` | Default angle (degrees) for the `@cone` selector. |
| `vanilla-stats.mining-speed.<tier>` | `netherite:450, diamond:400, iron:300, stone:200, golden:500, wood:100` | Mining-speed stat by tool tier, for translated vanilla tools. |
| `vanilla-stats.weapon-damage.<tier>` | `netherite:8, diamond:7, iron:6, stone:5, wood:4` | Weapon-damage stat by tool tier. |
| `vanilla-stats.bow-damage` | `6.0` | Damage stat given to translated vanilla bows. |
| `vanilla-stats.crossbow-damage` | `9.0` | Damage stat given to translated vanilla crossbows. |
| `vanilla-stats.armor-base.<tier>` | `netherite:5, diamond:4, iron:3, gold:2, leather:1` | Base defense stat by armor material tier. |
| `vanilla-stats.armor-multiplier.<piece>` | `chestplate:2.5, leggings:2.0, helmet:1.5, boots:1.0` | Multiplier applied to `armor-base` per armor slot. |
| `lore.sections` | `[breaking-power, base-lore, lore-template, stats, modifiers, enchantments, abilities, rarity-tag]` | Ordered list of lore blocks to render. Remove an entry to hide that block entirely (e.g. remove `modifiers` to fold gemstone lines out of the lore). Unknown entries log a load-time warning and are skipped. |
| `lore.spacer-between-sections` | `true` | Whether a blank line is inserted between two consecutive non-empty blocks. Turn off for denser lore. |
| `lore.breaking-power.format` | `"<dark_gray>Breaking Power {power}"` | Format string for the breaking-power line. Placeholder: `{power}`. |
| `lore.stats.line-format` | `"<gray> ◈ {stat}"` | Per-line wrapper around each stat's own formatted text. Placeholder: `{stat}`. |
| `lore.modifiers.line-format` | `"<gray> ◆ {label}"` | Per-line wrapper around each attached gemstone/trait. Placeholder: `{label}`. |
| `lore.abilities.header-format` | `"<gold>Ability: {name} <yellow><bold>{trigger}"` | Ability header line (only for `display: FULL` abilities). Placeholders: `{name}`, `{trigger}`. |
| `lore.abilities.mana-cost-format` | `"<dark_gray>Mana Cost: <aqua>{mana}"` | Shown only when the ability's mana cost > 0. Placeholder: `{mana}`. |
| `lore.abilities.cooldown-format` | `"<dark_gray>Cooldown: <green>{cooldown}s"` | Shown only when the ability's cooldown > 0. Placeholder: `{cooldown}`. |
| `lore.rarity-tag.format` | `"{color}<bold>{rarity}{type}"` | The bottom "EPIC SWORD" line. Placeholders: `{color}`, `{rarity}`, `{type}` (includes a leading space, empty for `NONE`). |
| `trample.protected-block` | `"FARMLAND"` | Which block PHYSICAL-interact trample protection (`CANCEL_TRAMPLE` boots) applies to. |

---

## `mechanics`

Server-wide fallback values for item-ability mechanic params, consulted only when an individual
ability YAML's own `params:` omits that field — every ability can still override any of these
per-instance. Every default below reproduces the mechanic's original hardcoded literal, so leaving
this section untouched changes no behavior.

| Field | Default | Meaning |
|---|---|---|
| `damage.default-amount` | `1.0` | Fallback flat damage amount. |
| `damage.default-type` | `"MAGIC"` | Fallback damage-type id. |
| `damage.default-ticks` | `1` | Fallback tick count for repeating damage. |
| `damage.default-interval-seconds` | `1.0` | Fallback interval between repeat-damage ticks. |
| `heal.target` | `"@player"` | Fallback target selector for the `heal` mechanic. |
| `heal.interval` | `1.0` | Fallback interval (seconds) for repeating heals. |
| `launch-projectile.projectile` | `"ARROW"` | Fallback projectile type. |
| `launch-projectile.velocity` | `2.0` | Fallback launch velocity. |
| `launch-projectile.count` | `1` | Fallback number of projectiles fired. |
| `launch-projectile.spread` | `0.0` | Fallback spread angle. |
| `launch-projectile.pierce` | `false` | Fallback pierce-through-entities flag. |
| `launch-projectile.damage` | `0.0` | Fallback projectile damage. |
| `aoe-mine.radius` | `1` | Fallback AoE mining radius (blocks). |
| `pull-entities.period-ticks` | `4` | Fallback tick period between pull steps. |
| `pull-entities.strength-default` | `1.0` | Fallback pull strength. |
| `pull-entities.range-default` | `20.0` | Fallback pull range. |
| `pull-entities.duration-default` | `2.0` | Fallback pull duration (seconds). |
| `pull-entities.target-default` | `"@enemies_in_radius{r=10}"` | Fallback target selector. |
| `push-entities.force-default` | `1.0` | Fallback push force. |
| `push-entities.y-clamp` | `0.3` | Fallback vertical-velocity clamp. |
| `push-entities.y-factor` | `0.4` | Fallback vertical-force factor. |
| `ignite.duration-default` | `3.0` | Fallback burn duration (seconds). |
| `apply-effect.duration-default` | `5.0` | Fallback potion-effect duration (seconds). |
| `apply-effect.amplifier-default` | `1` | Fallback potion-effect amplifier. |
| `apply-effect.hide-particles` | `false` | Fallback particle-visibility flag. |
| `modify-stat.amount-default` | `0.0` | Fallback stat-modification amount. |
| `modify-stat.duration-default` | `-1.0` | Fallback duration (seconds); `-1` means permanent. |
| `launch-player.y-force-default` | `1.0` | Fallback vertical launch force. |
| `launch-player.forward-force-default` | `1.0` | Fallback forward launch force. |
| `launch-player.no-fall-damage-default` | `false` | Fallback fall-damage-negation flag. |
| `charge-jump.max-charge-ms` | `2000` | Fallback max charge-hold time. |
| `charge-jump.min-y-force` | `0.4` | Fallback minimum jump force (no charge). |
| `charge-jump.max-y-force` | `2.2` | Fallback maximum jump force (full charge). |
| `teleport.distance-default` | `8.0` | Fallback teleport distance. |

---

## `enchants`

| Field | Default | Meaning |
|---|---|---|
| `etable.cost-per-level` | `2` | XP levels charged per level of an enchant applied at the enchanting table. |
| `defaults.sharpness.percent-per-level` | `5.0` | Power-curve default for the `sharpness` logic, used when an enchant's own YAML doesn't set `logic-params.percent-per-level`. |
| `defaults.growth.percent-per-level` | `10.0` | Same, for `growth`. |
| `defaults.fortune.percent-per-level` | `10.0` | Same, for `fortune`. |
| `defaults.efficiency.percent-per-level` | `50.0` | Same, for `efficiency`. |
| `defaults.stat_bonus.percent-per-level` | `1.0` | Same, for `stat_bonus`. |
| `defaults.damage_multiplier.percent-per-level` | `5.0` | Same, for `damage_multiplier`. |
| `defaults.defense_reduction.percent-per-level` | `3.0` | Same, for `defense_reduction`. |
| `defaults.protection.percent-per-level` | `4.0` | Same, for `protection`. |
| `defaults.execute.percent-per-level` | `0.2` | Same, for `execute`. |
| `defaults.first_strike.percent-per-level` | `25.0` | Power-curve default for `first_strike`. |
| `defaults.first_strike.max-hits` | `3` | Max hits counted within the first-strike window. |
| `defaults.first_strike.reset-window-ms` | `10000` | Window (ms) after combat starts during which first-strike applies. |
| `defaults.life_steal.percent-per-level` | `0.5` | Power-curve default for `life_steal`. |
| `defaults.lethality.percent-per-level` | `0.2` | Power-curve default for `lethality`. |
| `defaults.lethality.max-stacks` | `4` | Max stacking lethality bonuses. |
| `defaults.lethality.stack-duration-ms` | `4000` | How long each lethality stack lasts. |
| `defaults.respite.percent-per-level` | `0.5` | Power-curve default for `respite`. |
| `defaults.thorns.percent-per-level` | `15.0` | Power-curve default for `thorns`. |
| `defaults.etable-max-level` | `5` | Global level cap at the enchanting table, used when an individual enchant's own YAML omits it. |
| `defaults.absolute-max-level` | `10` | Global absolute level cap (e.g. via books/anvil merge), used when an individual enchant's own YAML omits it. |
| `transient.cleanup-interval-ticks` | `6000` | How often the transient (combat-only) enchant-state tracker sweeps for stale entries. |
| `transient.idle-purge-millis` | `900000` | How long a player's transient enchant state can sit idle before being purged. |

See `docs/modules/user/enchant.md`.

---

## `pets`

Pet leveling (`xp-formula`/`max-level`) lives in its own `pets/defaults.yml` (extracted on first
launch); this section is server-operator feel/perf tuning instead.

| Field | Default | Meaning |
|---|---|---|
| `follow.follow-distance` | `2.5` | How close (blocks) a pet gets before it stops closing distance to its owner. |
| `follow.teleport-distance` | `12.0` | Distance (blocks) beyond which a pet snaps (teleports) to catch up instead of walking. |
| `follow.step` | `0.35` | How far the pet steps toward the owner each follow tick. |
| `follow.tick-interval-ticks` | `5` | Follow-poll rate — trade-off between CPU and follow smoothness. |

---

## `modifiers`

| Field | Default | Meaning |
|---|---|---|
| `group-defaults.application-mode` | `"MULTIPLE"` | Server-wide policy fallback used when a modifier group's own YAML omits an `application:` field. Every group can still override individually. |
| `group-defaults.max` | `2147483647` (`Integer.MAX_VALUE`) | Fallback max number of modifiers of a group a single item can hold. |
| `group-defaults.replacement` | `false` | Fallback: whether applying a new modifier of the group replaces an existing one. |
| `group-defaults.removal` | `true` | Fallback: whether the group's modifiers can be removed once applied. |
| `tier-source.formula` | `""` (blank) | `Expression` evaluated with `$rarity.rank$` for `TierSource.RARITY_RANK` groups (e.g. reforges). Blank keeps the built-in `rank + 1` default. |

See `docs/modules/user/modifier.md` and `docs/modules/design/modifier.md`.

---

## `alchemy`

| Field | Default | Meaning |
|---|---|---|
| `splash-radius` | `4.0` | Block radius for splash-potion area of effect. |
| `tick-interval` | `20` | Ticks between active-effect expiry checks (`20` = once a second). |
| `max-active-effects` | `10` | Max concurrent active alchemy effects per player. |
| `effects.healing.values` | `[20, 50, 100, 150, 200, 250, 300, 350]` | Per-level healing amounts for the built-in hardcoded `healing` alchemy effect. |
| `effects.absorption.values` | `[20, 40, 60, 80, 100, 150, 200, 300]` | Per-level absorption amounts for the built-in hardcoded `absorption` alchemy effect. |
| `effects.vanilla.<id>` | *unset (commented example)* | Additional vanilla-potion-backed alchemy effects beyond the built-in `jump_boost`/`night_vision`/`invisibility`/`fire_resistance`. Example: `blindness: { type: blindness, amplifier-scales: false }`. |

---

## `fishing`

| Field | Default | Meaning |
|---|---|---|
| `loot.default-weight` | `10` | Fallback loot-table weight for a fishing-loot entry that omits its own `weight`. |

---

## `notify`

| Field | Default | Meaning |
|---|---|---|
| `categories.info.io` | `"chat"` | Output channel used for the `info` notification category. |
| `categories.error.io` | `"actionbar"` | Output channel used for the `error` notification category. |

---

## `zones`

| Field | Default | Meaning |
|---|---|---|
| `spawner-tick-interval-ticks` | `20` | How often the mob-spawner task polls. Large servers with many zone spawners may want to spread this to 40–100 ticks (2–5s). |
| `mob-home-interval-ticks` | `40` | How often the "return home if too far from a zone spawner" task scans living entities. |
| `visualization-interval-ticks` | `40` | How often zone-border particle visualization refreshes for players with it toggled on. |
| `selection-visualization-interval-ticks` | `10` | How often the admin-only pos1/pos2 selection wireframe refreshes while drawing a zone. |
| `visualization-max-distance` | `200` | Beyond this distance (blocks) from a zone's center, its border stops rendering to a viewer. |
| `mob-wander-radius-multiplier` | `2.0` | Multiplier applied to a spawner's own spawn-radius to get a spawned mob's wander radius. |
| `mob-wander-min-radius` | `4` | Floor on the computed wander radius. |
| `spawn-search-attempts` | `20` | Retry attempts when searching for a safe natural-spawn location. |
| `spawn-occupancy-radius` | `0.8` | Radius (blocks) checked for an already-occupying entity before spawning at a candidate spot. |
| `enter-title-duration-ticks` | `60` | How long the zone-enter actionbar popup stays. |
| `wand.material` | `"GOLDEN_AXE"` | Item material used as the zone-selection wand. |
| `wand.name` | `"<gold><bold>Zone Wand"` | Display name of the zone-selection wand. |
| `wand.lore` | `[]` | Lore lines for the wand. Empty falls back to the built-in "Left-click: Set Pos1 / Right-click: Set Pos2" lines. |
| `messages.wilderness-name` | `"<green>Wilderness"` | Shown for `$zone.current$`/the scoreboard "Zone:" line when a player isn't inside any zone. |

---

## `warps`

| Field | Default | Meaning |
|---|---|---|
| `defaults.world` | `"world"` | Fallback world, used only when an individual warp's own YAML omits `world`. |
| `defaults.y` | `64` | Fallback Y coordinate. **Unsafe** on a void world or a build well above/below y=64 — a load-time warning is logged for a warp relying on this default. |
| `defaults.unlock-condition` | `"always"` | Fallback unlock-condition expression. |
| `defaults.cost` | `0.0` | Fallback coin cost to use the warp. |
| `defaults.cooldown` | `0` | Fallback cooldown (seconds) between uses. |
| `defaults.warmup` | `0` | Fallback warmup (seconds) before teleport fires. |
| `warmup.cancel-on-move-distance` | `1.0` | How far (blocks) a player can drift during a warp warmup before it's cancelled as "moved". `1.0` matches the original block-level check's rough tolerance. |
| `warmup.cancel-on-damage` | `false` | Whether taking damage during a warmup cancels the warp. |

---

## `gui`

| Field | Default | Meaning |
|---|---|---|
| `defaults.title` | `"Inventory"` | Fallback GUI title, used when a GUI's own YAML omits `title`. |
| `defaults.update-interval-ticks` | `0` | Fallback `on-update` timer interval (`0` = disabled). |
| `defaults.machine` | *unset — falls back to the GUI's own id* | Fallback `machine:` field. |
| `defaults.command-permission` | *unset (commented)* | When set, required by default for any GUI whose own YAML doesn't set `command-permission`. Unset (`null`) stays permissive, matching the original hardcoded behavior. |
| `max-lore-lines` | `0` | Guards against a runaway dynamic/looped lore list exceeding the client's line cap. `0` disables the check (no cap was ever enforced originally). |
| `crafting.max-mass-crafts` | `64` | Cap on how many crafts a single mass-craft action can perform at once — an anti-dupe/anti-bulk-exploit lever. |

See `docs/modules/user/gui.md`.

---

## `ui`

| Field | Default | Meaning |
|---|---|---|
| `tick-interval-ticks` | `2` | Scoreboard/actionbar clock tick rate — the hottest loop in the UI module (ticks every online player). `2` = 10 Hz; large servers may want `4`–`10` (5–2 Hz). |
| `chat.prefix` | `"<dark_gray>[<gold>Valmora<dark_gray>] <white>"` | Chat-message prefix branding. |

---

## `hud`

| Field | Default | Meaning |
|---|---|---|
| `respawn-restore-delay-ticks` | `1` | How long after respawn HUD items are re-given. Can be fragile with lag or `keepInventory` interactions — raise slightly if items aren't reliably restored. |

---

## `npc` / `dialogue`

| Field | Default | Meaning |
|---|---|---|
| `npc.look-range` | `10.0` | Distance (blocks) within which an NPC turns to look at a nearby player. |
| `npc.hologram.origin-y` | `2.0` | Vertical offset (blocks) for an NPC's nameplate/hologram. |
| `npc.tasks.respawn-interval` | `1200` | How often (ticks) despawned NPCs are checked for respawn. |
| `npc.tasks.look-interval` | `5` | How often (ticks) the look-at-player task runs. |
| `npc.messages.prefix` | `"<dark_gray>[<gold>NPC<dark_gray>] "` | Chat prefix on NPC-related messages. |
| `npc.skin.urls.profile` | `https://api.mojang.com/users/profiles/minecraft/` | Mojang profile-lookup endpoint. Point at a self-hosted proxy if running one. |
| `npc.skin.urls.session` | `https://sessionserver.mojang.com/session/minecraft/profile/` | Mojang session-server endpoint. |
| `npc.skin.urls.mineskin` | `https://api.mineskin.org/generate/url` | MineSkin skin-generation endpoint. |
| `npc.skin.user-agent` | `"Valmora-NPC/1.0 (contact: server-admin)"` | User-Agent header sent on outbound skin-related HTTP requests. |
| `dialogue.auto-advance.ticks-per-char` | `3` | NPC-to-NPC auto-advance reading speed: ticks held per character of dialogue text. |
| `dialogue.auto-advance.min-ticks` | `40` | Minimum hold time regardless of text length. |
| `dialogue.auto-advance.max-ticks` | `200` | Maximum hold time regardless of text length. |
| `dialogue.chat-clear-lines` | `20` | Blank lines sent to "clear" chat before opening a dialogue. |
| `dialogue.hint-interval-ticks` | `40` | How often the "press to continue" hint is re-sent during a dialogue. |
| `dialogue.stop-check-interval-ticks` | `5` | How often the dialogue system polls for a stop/cancel condition. |
| `dialogue.history-size` | `100` | Max dialogue lines retained in a player's conversation history buffer. |

See `docs/modules/user/npc.md`.

---

_This page is generated from a direct read of the shipped `config.yml`; if you add or rename a
field there, update this page in the same change. Last verified against `config.yml` 2026-09-02._
