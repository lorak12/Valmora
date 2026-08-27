# V1 Manual QA Guide — Full In-Game Test Script

> Companion to `docs/V1_RELEASE_CHECKLIST.md` §3/§4/§7 — everything in this file needs a running
> server and a keyboard, which is exactly what the checklist's automated pass couldn't cover. Work
> top to bottom; each phase assumes the previous one passed. Every command/item/mob/zone/GUI id
> below was pulled from the actual shipped resource files (not guessed), so you can paste them
> directly — but always cross-check the specific module's `docs/modules/user/<module>.md` for the
> full command reference, since this guide only lists the commands needed to *run* each test, not
> the complete feature surface. Check boxes off as you go; this is a working document, not a
> permanent one.

---

## 0. Before you touch the server

Read this whole section once — several tests below need one-time setup that isn't in the shipped
default config, and it's faster to do all of it up front than to stop mid-test-run later.

### 0.1 Launch the dev server

```bash
./gradlew runServer
```

First run downloads Paper 1.21.11 automatically. Wait for `Done (X.XXXs)! For help, type "help"`
before connecting.

### 0.2 Get yourself OP

In the server console:

```
op <your_ign>
```

### 0.3 Set up a second, non-admin test account

You need this for §5 (Security & Permissions) and for anything requiring two players (trading,
shared-machine GUI concurrency, dialogue with an NPC while another player is nearby, etc.).

- **Easiest (offline-mode dev server):** `run-paper`'s dev server defaults to `online-mode: false`.
  Log in a second Minecraft client with any different username (no premium account needed) —
  it'll create a brand-new, non-OP player automatically. **Do not** run `op` on this account.
- **If you only have one account:** most single-player tests still work; skip the two-player
  scenarios explicitly marked "2 players" below and note them as untested rather than skipping
  silently.

### 0.4 New config you'll need to add — MySQL test environment

The shipped `config.yml` ships with `database.type: sqlite` and the MySQL block **commented out**.
To exercise §6 (Persistence) against real MySQL, you need both a MySQL server and an edited config
that doesn't exist by default:

**Get a local MySQL instance** (skip if you already have one):

```bash
docker run --name valmora-test-mysql -e MYSQL_ROOT_PASSWORD=password123 \
  -e MYSQL_DATABASE=valmora -p 3306:3306 -d mysql:8
```

**Create `plugins/Valmora/config.yml`'s database block like this** (stop the server first, edit,
then restart — `database.type` is read once at startup, not hot-reloadable):

```yaml
database:
  type: mysql
  mysql:
    host: "127.0.0.1"
    port: 3306
    database: "valmora"
    username: "root"
    password: "password123"
    use-ssl: false
```

Keep a copy of the original SQLite config (`config.yml.sqlite-backup`) so you can switch back
after §6 without redoing setup.

### 0.5 New config you'll need to add — NPC skin server (optional feature, off by default)

`config.yml`'s `npc-skin-server` block is present but `enabled: false` by default. If you want to
test the `/npc skin <id> file <filename.png>` flow (§3.16 below), you need to:

1. Set `npc-skin-server.enabled: true` in `plugins/Valmora/config.yml` and pick a free `port`
   (default `2525`).
2. Create `plugins/Valmora/skins/` and drop a test PNG (any 64×64 or 64×32 Minecraft skin file)
   into it, e.g. `test_skin.png`.
3. Restart (or `/valmora reload` — this one *is* just a config read at apply-time, check the
   feature doesn't cache the `enabled` flag from startup before assuming reload is enough).

If you don't care about custom NPC skins for v1, skip this — it's an optional feature, not a
release blocker, and it's fine to note "not tested" rather than force it.

### 0.6 Nothing else needs new config

Every other test below uses content that already ships in `src/main/resources/` — test items,
test mobs, test zones, example recipes, etc. You do **not** need to write new YAML for anything
except the two cases explicitly called out inline below (§3.6's `TAKE_COINS` mechanic has zero
shipped items using it).

---

## 1. Phase 1 — Core Lifecycle

- [ ] **Fresh install.** Stop the server, delete `plugins/Valmora/` entirely, start it again.
      Console should show no errors/stack traces, and `plugins/Valmora/` should regenerate with
      `config.yml`, `database.db`, all the seeded YAML folders (`items/`, `mobs/`, `guis/`, etc.),
      and a `.resources_seeded` marker file.
- [ ] **Reload stress test.** Log in, open any GUI (e.g. `/effects`), start a fight with a mob,
      apply a potion effect to yourself, then run `/valmora reload` **5 times in a row** rapidly.
      Check: no duplicate scoreboard lines, no doubled action bar/boss bar, GUI still functions
      after reload (close and reopen it), potion effect didn't duplicate or vanish.
- [ ] **Full restart (not reload).** With items in your inventory, a bank balance, quest progress,
      and an active pet out, run `/stop`, then start the server again. Verify all of the above
      survived intact.
- [ ] **Unclean shutdown (crash simulation).** While mining a resource block in the `mine` zone
      (see §3.13) so a block is mid-regen-timer, find the server process and kill it hard:
      - Windows: `taskkill /F /IM java.exe` (careful if you have other Java processes running —
        filter by PID via `tasklist | findstr java` first)
      - Or from the `run-paper` task: Ctrl+C twice in the terminal running `./gradlew runServer`
      Restart the server. Check console for `[Resource] Restored N mid-progress resource block(s)
      after an unclean shutdown.` and confirm the block you were mining is still mid-regen (not
      reset to a full ore block, not stuck as air forever).

---

## 2. Phase 2 — Per-Module Walkthrough

For each module, `docs/modules/user/<module>.md` is the authoritative command/feature list — this
section gives you concrete, ready-to-run test steps, not the full surface.

### 2.1 Core / Module system

- [ ] `/valmora reload` with a deliberately broken YAML file (see §7.2) recovers cleanly.
- [ ] Confirm no command throws a raw stack trace to console for any malformed input (spam
      `/item`, `/mob`, `/zone` etc. with garbage args — see §5.3).

### 2.2 Stat

- [ ] `/stat` (as a player) shows your current stat block.
- [ ] Equip an item with stats (e.g. `iron_sword`, `/item give iron_sword`) and confirm the stat
      GUI/scoreboard updates live without needing a relog.

### 2.3 Player / Profile

- [ ] `/profile help` then create a second profile (up to `profiles.max-profiles: 4`). Switch
      between them and confirm inventory/stats/economy are isolated per profile.
- [ ] Try creating a 5th profile — should be blocked at the configured cap.

### 2.4 UI / HUD

- [ ] `/ui toggle` hides/shows the sidebar scoreboard.
- [ ] Confirm the default HUD item(s) from `hud-items/default.yml` appear in your hotbar on join
      and can't be dropped/moved out of their configured slot if `prevent-move` is true for them.
- [ ] Left-click and right-click the HUD item and confirm its configured action fires.

### 2.5 Economy

- [ ] `/eco get <you>` shows purse/bank (both should be 0 on a fresh profile).
- [ ] `/eco add <you> purse 1k` → confirm `/eco get <you> purse` shows 1,000.
- [ ] `/eco remove <you> purse 5k` (more than you have) → confirm it clamps to 0, not negative.
- [ ] `/eco set <you> bank -50` → should be rejected ("Amount must be ≥ 0").
- [ ] Die with coins in your purse and confirm `economy.death-loss-percent` (50% by default) is
      lost, bank untouched.
- [ ] Wait past `bank-interest-interval-seconds` (3600s default — lower it temporarily in
      `config.yml` to e.g. `60` for this test, then restart) with a positive bank balance and
      confirm interest was applied.
- [ ] Kill a mob with `gold-reward` set (e.g. `test_zombie`, `gold-reward: 5`) and confirm coins
      land in your purse.

### 2.6 Item / Ability mechanics

Give yourself each item with `/item give <id> [amount] [player]` and trigger its ability. This is
the most important section — it exercises all 16 registered mechanics plus the GUI-registered
`OPEN_CONTAINER_GUI`.

| Mechanic | Test item | Trigger | How to fire it | What to check |
|---|---|---|---|---|
| `DAMAGE` | `fire_veil_wand` | RIGHT_CLICK | Right-click at a mob | Mob takes magic damage |
| `HEAL` | `sanguine_carver` | RIGHT_CLICK | Right-click while hurt | Your health increases |
| `APPLY_EFFECT` | `spider_shortbow` | ON_HIT | Shoot and hit a mob | Correct potion effect applies to the target |
| `MODIFY_STAT` | `warden_helmet` | PASSIVE | Wear it | A stat changes in `/stat` immediately on equip |
| `TELEPORT` | `aspect_of_the_end` | RIGHT_CLICK | Right-click | You teleport forward |
| `PUSH_ENTITIES` | `aspect_of_the_dragons` | RIGHT_CLICK | Right-click near mobs | Mobs are knocked away |
| `PULL_ENTITIES` | `gyrokinetic_wand` | RIGHT_CLICK | Right-click near mobs | Mobs are pulled toward you |
| `SCRIPT` | `forge_titan` (boss mob, `mobs/test_boss.yml`) | mob ability | `/mob spawn forge_titan`, let it act | Its scripted ability fires (check console/effects for its custom behavior) |
| `LAUNCH_PROJECTILE` | `halberd_of_the_shredded` | ON_HIT | Hit a mob in melee | A secondary projectile launches |
| `LAUNCH_PLAYER` | `leaping_sword` | RIGHT_CLICK | Right-click | You're launched forward/up |
| `GIVE_COINS` | `raider_axe` | ON_KILL | Kill any mob while holding it | +20 coins in purse |
| `TAKE_COINS` | **none shipped** — see below | — | — | — |
| `CANCEL_TRAMPLE` | `rancher_boots` | PASSIVE | Wear boots, jump onto farmland (till dirt with a hoe first) | Farmland does **not** turn to dirt |
| `CHARGE_JUMP` | `spring_boots` | SNEAK | Wear boots, hold sneak up to 2s, release | You jump higher the longer you charged (up to the 2000ms cap) |
| `IGNITE` | `fire_fury_staff` | RIGHT_CLICK | Right-click at a mob | Target catches fire |
| `AOE_MINE` | any `shardworks_*` pickaxe (e.g. `ferrite_pickaxe`) | passive, via Mining Spread stat | Mine an ore in the `shardworks` zone with several adjacent same-type ore blocks | Multiple adjacent blocks break in one hit, scaling with your Mining Spread stat |
| `OPEN_CONTAINER_GUI` | `backpack_tier1` | RIGHT_CLICK | Right-click while holding it | Its storage GUI opens |

- [ ] **`TAKE_COINS` has no shipped item.** Add a temporary test item to exercise it — create
      `plugins/Valmora/items/qa_test_items.yml`:
      ```yaml
      qa_take_coins_test:
        name: "QA: Take Coins Test"
        material: "PAPER"
        rarity: "COMMON"
        item-type: "NONE"
        abilities:
          test_take_coins:
            name: "Test"
            trigger: "RIGHT_CLICK"
            mechanics:
              - type: "TAKE_COINS"
                params:
                  amount: 10
      ```
      `/valmora reload`, then `/item give qa_take_coins_test`, right-click with a purse balance
      ≥10, confirm 10 coins are deducted. Delete the file (and reload) once done — it's QA-only
      content, not something to ship.
- [ ] Confirm every trigger type not covered above also fires at least once somewhere:
  - `LEFT_CLICK` — pick any sword and left-click; check console/effects for its ability (most
    swords only use `ON_HIT`/`RIGHT_CLICK`, so grep `items/*.yml` for `LEFT_CLICK` if you want a
    concrete example, or accept this as covered by the parser-level test coverage instead).
  - `ON_DAMAGE_TAKEN` — wear any armor piece with this trigger (grep for it if none of the above
    already gave you one) and take damage; confirm it fires.
  - `ON_TELEPORT` — use any warp (`/warp hub_spawn`) while holding/wearing an item with an
    `ON_TELEPORT` ability, or just confirm no error occurs even with a plain item (the trigger
    should silently no-op for items without that ability).
  - `EQUIP`/`UNEQUIP` — swap armor pieces in your inventory (any two different pieces) and confirm
    no error; if you have an item with an EQUIP/UNEQUIP ability, confirm it fires on the swap.
- [ ] **Ability system reload safety**: mid-cooldown on an ability (e.g. right after using
      `leaping_sword`), run `/valmora reload`. Confirm the cooldown either persists sensibly or
      resets cleanly — not stuck permanently on cooldown.

### 2.7 Mob

- [ ] `/mob spawn test_zombie` and `/mob spawn test_skeleton` — confirm equipment, health, and
      loot table drops (`ROTTEN_FLESH`/`DIAMOND` for the zombie, `BONE`/`ARROW` for the skeleton;
      kill each ~20 times and eyeball that the low-chance drops — 1% diamond, 80% arrow — land in
      roughly the right ballpark, not exactly).
- [ ] `/mob spawn forge_titan` — this is the test boss; confirm it's noticeably tougher and its
      `SCRIPT` ability fires (see §2.6's SCRIPT row).
- [ ] `/mob spawn shardworks_cave_guardian` and `/mob spawn shardworks_crystal_wraith` — confirm
      they spawn and behave (these are in `mobs/shardworks_mobs.yml`, tied to the `shardworks`
      zone content).
- [ ] `/mob spawn zombie_slayer_boss_1` (and optionally the other slayer bosses/tiers) — confirm
      it spawns; full slayer-chain testing happens in §2.19 (Quest).
- [ ] `/mob list` and `/mob info <id>` for a couple of the above — confirm accurate info, no
      errors.
- [ ] **Mob abilities from the recent backlog passes** — confirm `ON_DAMAGE_TAKEN`/`ON_TELEPORT`
      triggers work on a mob if any shipped mob definition uses them (check `mobs/*.yml` for
      those trigger names; if none currently do, note this as an untested gap rather than skip
      silently).

### 2.8 Skill

- [ ] `/skill get <you> mining` — check current level/XP.
- [ ] `/skill give <you> mining 10000` — give a large XP chunk that crosses at least one level
      boundary. Confirm: level-up message/sound fires exactly once, XP curve matches
      `skills/mining.yml`'s `xp-curve`, and any level-up rewards (stat increases, unlocks) apply.
- [ ] `/skill set <you> combat level 5` — confirm it lands exactly at level 5 with the correct XP
      floor for that level.
- [ ] `/skill reset <you> mining` — confirm it zeroes out cleanly.
- [ ] Repeat for at least one more skill (`combat`, `fishing`, `foraging`, `farming`, `enchanting`,
      `carpentry`, `taming` — pick 2-3, not all 9, unless you have time).

### 2.9 Combat

- [ ] Melee a mob with a plain sword (`wooden_sword` up through `diamond_sword`) — damage numbers
      should scale with the weapon's `DAMAGE` stat and your own `STRENGTH`/`CRIT_*` stats per the
      documented formula (`docs/modules/design/combat.md`).
- [ ] Ranged: shoot a mob with `bow` (or any of the named bows, e.g. `wither_bow`) — confirm
      `ON_SHOOT`/`ON_HIT` abilities on bows that have them fire (e.g. `spider_shortbow`'s
      `APPLY_EFFECT` from §2.6).
- [ ] Magic/ability damage: right-click with `fire_veil_wand` (DAMAGE mechanic) at a mob.
- [ ] PvP (needs 2 players): have your second test account attack you (or vice versa) in a zone
      where `pvp: true` (not the `mine`/`forest`/`test_site` zones, which default `pvp: false` —
      you'll need a zone with pvp allowed, or test in unclaimed wilderness outside any zone).
- [ ] Mob-vs-mob: spawn two hostile mobs near each other (or a mob near a wolf) and confirm damage
      resolves without errors even when neither side is a player.
- [ ] Damage indicators: confirm the floating damage number appears, respects
      `damage-indicator-rate-limit-ms` (400ms — rapid-fire hits shouldn't spam one per tick), and
      despawns after `damage-indicator-lifetime-ticks` (20 ticks = 1s).

### 2.10 GUI / Recipe

- [ ] **Craft in every machine type**, confirming each recipe type:
  - `EXACT_SLOT`: open the `forge` GUI (right-click a real Forge block, or use
    `/gui open <you> forge` to skip finding/placing one), put 2 `IRON_INGOT` in the base slot,
    craft `reinforced_ingot` (`recipes/forge.yml`).
  - `SHAPED`: open `crafting_table` (or `/gui open <you> crafting_table`), craft `diamond_sword`
    (2 `DIAMOND` + 1 `STICK` in the documented slot pattern) and `iron_pickaxe`.
  - `SHAPELESS`: craft `wood_planks` from 1 `OAK_LOG` (fixed this pass — previously broken, see
    the release checklist's fixed-bugs list; confirm it now actually works).
- [ ] **Dupe protection**: in any machine GUI, rapidly double-click/shift-click the output slot
      the instant a craft completes, and try closing the GUI mid-craft (as fast as you can manage
      manually — this is inherently timing-sensitive, so repeat several times). Confirm you never
      get 2x output for 1x input consumed.
- [ ] **DynamicMachineHandler-owned machines** (no YAML recipes exist for these — see the release
      checklist's handler allow-list): open `alchemy`, `anvil`, `forge_random` (via the `reforge`
      GUI), and `reforge_anvil` and confirm each still produces sensible output through its custom
      Java handler rather than erroring with "no recipe found".
- [ ] **GUI admin debug**: `/gui open <you> stats` (or any other GUI id from `guis/*.yml`) opens it
      directly without needing to trigger its normal open-condition — use this throughout the rest
      of this guide to save time reaching GUIs that are normally block/command-gated.
- [ ] Every `PAGINATED` component GUI (e.g. `skills_list`, `collections_categories`) — page forward
      and backward, confirm no crash at the last/first page.

### 2.11 Alchemy

- [ ] `/potion give <effect_id> <level> [player]` — check `alchemy/effects.yml` for valid effect
      ids, give yourself one, confirm it applies at the right level/duration.
- [ ] Brew in the `alchemy` GUI (SHAPELESS-style, `NETHER_WART` + `GLASS_BOTTLE` per CLAUDE.md's
      example) — confirm the output matches `alchemy/effects.yml`/`modifiers.yml`/
      `healing_boost.yml`'s tiers.
- [ ] Apply a splash/lingering potion effect to a **non-player entity** (a mob) — this is
      specifically flagged in `docs/modules/design/alchemy.md` as an area with known DOT-on-mobs
      edge cases; confirm the effect applies and expires correctly on the mob, not just players.
- [ ] With an active effect running, `/valmora reload` — confirm the effect is cleared (not left
      running forever with no way to clear it) per the design doc's noted caveat.
- [ ] Hit `alchemy.max-active-effects` (10 by default) by stacking many different effects and
      confirm the 11th either replaces the oldest or is rejected gracefully, not crashing.

### 2.12 Enchant

- [ ] `/item enchant sharpness 5` on a held sword — confirm the enchant applies and its effect
      (from `enchant/logic/*Logic`) actually triggers in combat.
- [ ] `/item enchantbook fortune 3` — confirm you get an enchanted book, then apply it via the
      `enchanting_table` GUI/anvil to a real item.
- [ ] Test at least 3 of the 11 shipped enchants end-to-end (`sharpness`, `growth`, `execute`,
      `first_strike`, `life_steal`, `lethality`, `protection`, `respite`, `thorns`, `fortune`,
      `efficiency`) — pick ones with clearly observable effects (`life_steal` heals you,
      `thorns` reflects damage, `fortune` boosts mining drops).

### 2.13 Reforge

- [ ] `/reforge preview fierce diamond` (or another rarity) — confirm the stat preview is sane.
- [ ] `/reforge force sharp` on a held item — confirm the reforge applies and its stat bonus is
      reflected in `/stat`.
- [ ] Use the `reforge_anvil` GUI (`/gui open <you> reforge_anvil`) to reforge an item and confirm
      the forge-stone cost is deducted per `enchant/forge_costs.yml`'s rarity table.
- [ ] `/reforge reset` — confirm it strips the reforge cleanly.
- [ ] Test all 8 reforges at least once (`fierce`, `sharp`, `fabled`, `heroic`, `rapid`,
      `fortified`, `reinforced`, `titanic`) if time allows — otherwise at least 3-4 covering
      different stat focuses.

### 2.14 Zone & Resource

- [ ] Enter/exit the `mine` zone (world `world`, roughly X -100..-79, Y 131..153, Z -100..-89) —
      confirm the zone's flags apply: `pvp: false`, `natural-mob-spawning: false`,
      `block-breaking: false` (you should NOT be able to break arbitrary blocks) except for
      configured resource blocks.
- [ ] Mine a `COAL_ORE` in the `mine` zone — confirm it drops `COAL`, then turns into
      `COBBLESTONE` (stage 2), and after `regen-delay: 200` ticks (10s) reverts back to the
      original ore. Try mining it again before the regen timer completes — should be blocked
      (`BreakResult.DEPLETED`).
- [ ] Repeat for `IRON_ORE` (drops `RAW_IRON`, same regen pattern).
- [ ] `/zone flag mine pvp true` then confirm PvP is now allowed inside; flip it back.
- [ ] `/zone spawner add mine test_zombie 3 5 400` — confirm zombies spawn within the zone,
      capped at `maxAlive`.
- [ ] `/zone info mine` — confirm accurate boundary/flag reporting.
- [ ] `forest`, `mob_spawn`, and `test_site` zones (also in `test_zones.yml`) and the `shardworks`
      zone — spot-check entry/exit and any zone-specific config each defines.
- [ ] **Breaking Power gating**: with a low-tier pickaxe (or bare hand) attempt to mine a resource
      block whose `required-power` (if configured — check the zone's `resource-blocks` config)
      exceeds what you have equipped; confirm `BreakResult.INSUFFICIENT_POWER` blocks the break
      with appropriate player feedback, not a silent failure.
- [ ] **Mining Fortune scaling**: compare drop amounts from the same ore with and without a
      Mining Fortune stat bonus equipped — higher fortune should yield more per the
      `applyFortune` multiplier formula.

### 2.15 Fishing

- [ ] Fish in the hub fishing zone (`fishing/hub_fishing.yml`) long enough to sample multiple loot
      tiers — confirm rarer catches are actually rarer, not uniformly distributed.
- [ ] Equip a bait item (`worm_bait`, `glow_bait`, `enchanted_bait` from `items/fishing_bait.yml`)
      via the `baitbag` GUI (`/baitbag` or `/gui open <you> bait_bag`) and confirm it affects catch
      odds/rates as documented.
- [ ] Confirm the bait bag GUI's `STORAGE` component correctly gates on `item-type: FISHING_BAIT`
      — try placing a non-bait item into it and confirm it's rejected.
- [ ] Fish for `squid` (`mobs/fishing_mobs.yml`) — a fishing-triggered mob spawn — confirm it
      spawns correctly from a fishing catch rather than a normal mob spawn path.

### 2.16 NPC

- [ ] `/npc create test_npc VILLAGER` then `/npc rename test_npc "<gold>Test NPC"`,
      `/npc settype test_npc ZOMBIE`, `/npc setyaw test_npc 90`, `/npc move test_npc` (move to your
      current position), `/npc tp test_npc` (teleport to it) — confirm each works and persists
      across `/valmora reload` and a restart.
- [ ] `/npc delete test_npc` — confirm clean removal.
- [ ] Talk to the shipped NPCs: `shardworks_prospector` and `general_store_keeper` — walk through
      their full dialogue trees to a terminal node, confirming no dead ends or errors mid-tree.
- [ ] If you set up the skin server (§0.5): `/npc skin <id> file test_skin.png` — confirm the NPC's
      appearance updates.

### 2.17 Warp

- [ ] `/warp hub_spawn` and `/warp coal_mine_warp` — confirm teleport lands at the exact configured
      coordinates.
- [ ] `/warp` with no args — confirm it opens the fast-travel GUI (`guis/fast_travel.yml`) instead
      of erroring.
- [ ] If any warp has a `cost` or `cooldown-seconds` configured, confirm coins are deducted and the
      cooldown is enforced (a second immediate `/warp` to the same destination should be blocked
      or delayed).
- [ ] Test warp signs if any exist in the world (`WarpSignListener` requires `valmora.admin` to
      create) — as your non-admin test account, confirm you're denied creating one but can still
      use an existing one.

### 2.18 Points / Progression

- [ ] `/progression info geomancy` — check current tree state.
- [ ] Earn Quest Points (via a quest reward, §2.19) and spend them in the `geomancy_tree` GUI
      (`/gui open <you> geomancy_tree` or `/geomancy`) to unlock a node.
- [ ] `/progression reset geomancy` — confirm `progression.refund-percent` (100% by default) is
      refunded correctly.

### 2.19 Notify

- [ ] Trigger something that fires each notify channel: chat message, action bar (e.g. a cooldown
      message), title/subtitle (e.g. a level-up), and sound (e.g. `/potion give` or a craft's
      `on-craft` sound line). Confirm all render/play correctly and don't stack/overlap badly when
      triggered in quick succession.

### 2.20 Quest

- [ ] `/quest` (no args) — opens the Quest GUI. Browse to the `shardworks` quest board
      (`quest_boards/shardworks.yml`) and accept a quest from it.
- [ ] Complete the `blacksmith_hub` quest package's `blacksmith_hub` quest (talk to an NPC using
      the `blacksmith` dialogue — you may need to `/npc create` one with the right conversation
      wired, or check `docs/modules/user/quest.md`/`docs/modules/user/npc.md` for how this package
      expects its NPC to already exist): collect 10 `COAL` (`getCoal` objective), confirm it
      completes and fires its reward events.
- [ ] `/quest journal` — confirm active + completed quests display correctly.
- [ ] `/quest track blacksmith_hub` then `/quest track none` — confirm the sidebar tracker
      appears/disappears correctly.
- [ ] **Slayer chains** (`quests/slayers/`): start a zombie slayer tier 1 via the `slayers` GUI
      (`/gui open <you> slayers`), kill the required mob count, confirm the tier's boss
      (`zombie_slayer_boss_1`) spawns, kill it, confirm the reward event fires. Per the in-file
      comment, re-running a tier is `quest_cancel <id>` then `quest_start <id>` via the same GUI —
      confirm a repeat run re-zeroes objectives rather than carrying over stale progress.
- [ ] `forgotten_mine` quest package — repeat the same completion flow.
- [ ] As an op: `/quest complete <player> <questId>` and `/quest reset <player> <questId>` — force
      both paths and confirm they behave (reward fires exactly once on forced completion; reset
      zeroes progress).
- [ ] `/points` balance check (however the points module exposes it — see
      `docs/modules/user/quest.md`'s Quest Points section) before/after completing a quest with a
      points reward.

### 2.21 Collection

- [ ] Collect an item tracked by a collection (e.g. mine `COAL_ORE` enough times for the `coal`
      collection, or `iron_ingot`; farm `wheat`; catch `cod`/`salmon`; chop `oak_log`; kill
      `zombie`/`skeleton`) and confirm progress increments and tier-up rewards fire at the right
      thresholds.
- [ ] `/collections view <you> coal` — confirm accurate progress display.
- [ ] `/collections force <you> wheat 500` (op-only) — confirm it jumps straight to that count and
      fires any tier rewards crossed.
- [ ] `/collections reset <you> zombie` — confirm it zeroes cleanly.
- [ ] Browse the `collections_categories`/`collections_list`/`collections_detail` GUIs end to end.

### 2.22 Calendar

- [ ] `/calendar list` — confirm all 3 events show (`harvest_festival`, `winter_blessing`,
      `spring_renewal`).
- [ ] `/calendar force-start harvest_festival` — confirm any event-specific content
      (shop items, spawns, whatever `calendar/seasonal.yml` defines) activates.
- [ ] `/calendar force-end harvest_festival` — confirm it deactivates cleanly.
- [ ] `/calendar preview <eventId>` — confirm it previews without actually starting the event.
- [ ] Let a full in-game day cycle pass (or use `/time set <year> <season> <phase> <day>` to jump)
      and confirm season/hour-dependent content changes at the right boundary (e.g. `/time info`
      shows the season you jumped to, and anything gated on season in scripts/conditions reacts).
- [ ] Restart the server and confirm `time.yml`'s day-offset survived (didn't reset to
      `start-year`/`start-season`/etc. from `config.yml`).

### 2.23 Pet

- [ ] `/pet give <you> baby_wolf 1` — confirm it spawns, follows you, and its XP formula matches
      `PetXpFormulaTest`'s expectations (100 × level² by default, or the pet's own override).
- [ ] Level it up (however pets gain XP in normal play — check `docs/modules/user/pet.md`) past a
      level boundary and confirm stat/ability scaling applies.
- [ ] Unequip/re-equip the pet and confirm state (level/XP) persists.
- [ ] Repeat briefly for `baby_sheep` and `ender_dragon_pet`.

### 2.24 Backpack / Accessory

- [ ] `/item give backpack_tier1` through `backpack_tier5` — right-click each, confirm its storage
      GUI opens with the correct number of slots per tier, and items placed persist across
      close/reopen and a server restart.
- [ ] `/item give lucky_charm` and `/item give speed_scarab` — equip via the accessory bag GUI
      (`/gui open <you> accessory_bag` or `/accessories`) and confirm their passive stat bonuses
      apply.

### 2.25 Set bonuses

- [ ] Equip a full matching armor set from `items/armor_sets.yml` (e.g. all 4 `diamond_armor_*`
      pieces) and check `set_bonuses/sets.yml`/`armor_sets.yml`/`shardworks_sets.yml` for what
      bonus should apply at 2/3/4 pieces; confirm the bonus stacks in correctly as you equip each
      piece and drops off correctly as you remove one.

---

## 3. Phase 3 — Edge Cases & Abuse Testing

- [ ] **Concurrent GUI access** (needs 2 players, or you + your alt): both players open the same
      machine GUI type at the same time (e.g. both open separate `forge` instances) and craft
      simultaneously — confirm no cross-player item leakage between the two sessions.
- [ ] **Disconnect mid-craft**: start a craft animation/GUI interaction, then immediately
      disconnect (close the client) before it resolves. Reconnect and confirm no item was
      duplicated or lost, and no orphaned `GuiSession` lingers (check server memory/logs after a
      few reload cycles for anything suspicious).
- [ ] **Full-inventory edge cases**: fill your inventory completely, then complete a quest with an
      item reward, craft something, and get a mob loot drop. Confirm items drop on the ground
      rather than vanishing in all three cases.
- [ ] **PDC-vs-displayname spoofing**: rename a plain item via an anvil to exactly match a GUI
      button's display name/lore (e.g. copy a `DISPLAY` component's name from `guis/stats.yml`)
      and try using it in place of the real button context (this is hard to stage without direct
      inventory manipulation — if you can't construct a realistic exploit path, treat this as
      already covered by the earlier code-review finding of zero `getDisplayName()`-as-identity
      usages, and don't force an artificial test).
- [ ] **Command injection / bad input**: run every custom command with garbage args —
      `/item give ' OR 1=1 --`, `/eco add nonexistent_player purse abc`, `/zone create ""`,
      `/npc create test SOME_INVALID_ENTITY_TYPE`, `/mob spawn nonexistent_mob`. Confirm graceful
      error messages every time, never a raw stack trace in console or chat.
- [ ] **Permission boundary sweep** — log in on your non-admin test account and attempt, in order:
      `/valmora reload`, `/item give diamond_sword`, `/mob spawn test_zombie`, `/zone create x`,
      `/npc create x VILLAGER`, `/eco add <you> purse 1000`, `/reforge force fierce`,
      `/calendar force-start harvest_festival`, `/gui open <you> stats`, `/collections force <you>
      coal 100`, `/quest complete <you> blacksmith_hub`. **Every one of these must be denied** with
      a permission message, not silently succeed.

---

## 4. Phase 4 — Performance & Load

- [ ] Baseline TPS/MSPT with 1 player idle at spawn (use `/tps` if a diagnostics plugin is
      installed, or Paper's built-in `/tps`).
- [ ] TPS/MSPT while actively using GUIs, fighting mobs, and firing item abilities rapidly —
      confirm no sustained drop below ~19.5 TPS on modest hardware.
- [ ] Spawn a large batch of mobs at once (`/zone spawner add mine test_zombie 5 30 100` briefly,
      or manually `/mob spawn` in a loop-ish burst) and watch for TPS impact.
- [ ] Fully populate a `PAGINATED` GUI's backing list (e.g. give yourself enough distinct
      collectibles/items to fill many pages of `collections_list`) and confirm pagination doesn't
      lag or misrender at high page counts.
- [ ] With MySQL configured (§0.4), generate a burst of economy/quest activity and watch the
      HikariCP pool (`Valmora-Pool`, max size 10) via console/logs for exhaustion or long-running
      query warnings.
- [ ] **Long-soak**: leave the server running with 1+ players idling for a few hours; periodically
      check memory usage (`/timings` if available, or just OS-level RAM tracking for the `java`
      process) for steady growth that would indicate a listener/task leak the earlier automated
      sweep might have missed.

---

## 5. Phase 5 — Persistence & Migration

- [ ] With SQLite (default), confirm `plugins/Valmora/database.db` exists and grows as you play;
      back it up, then restore it after making changes and confirm state reverts correctly (this
      IS your backup/restore procedure if you don't have another one documented).
- [ ] **Switch to MySQL** (§0.4): stop the server, edit config, restart. Confirm:
  - Schema initializes cleanly against the empty `valmora` database (check for the
    `valmora_schema_version` table and all expected tables).
  - Profile create/read/write, economy get/set/add/remove, and quest progress all work identically
    to the SQLite path.
  - No SQL dialect errors in console (this is exactly the class of bug that can't be caught by the
    SQLite-only automated test suite).
- [ ] **Switch back to SQLite** — confirm the plugin doesn't choke on having a stale `database.db`
      from before the MySQL detour (it shouldn't touch it at all if you kept the original file).
- [ ] Confirm `time.yml`'s day-offset (§2.22's last bullet) and `resource_state.yml` (only present
      after an unclean shutdown, per §1's crash test) both round-trip correctly.

---

## 6. Phase 6 — Security & Permissions (in-game confirmation of the earlier static audit)

- [ ] Re-run the permission boundary sweep from §3 if you haven't already — this is the actual
      in-game proof of the static `plugin.yml`/code-level audit already done.
- [ ] Confirm the `valmora.admin.gui` permission specifically (separate from blanket
      `valmora.admin`) — grant your test account only `valmora.admin` (not `.gui`) via a
      permissions plugin if you have one, and confirm `/gui open` is still denied.
- [ ] If you set up a permissions plugin, test the granularity gap already flagged in the release
      checklist: try granting only `/eco` without `/npc`/`/zone`/etc. — confirm (as expected) this
      isn't currently possible since every admin command shares one node. This isn't something to
      "fix" here, just confirm the limitation is real and decide if it matters for your v1 launch.

---

## 7. Phase 7 — Operational Readiness

- [ ] Confirm `/stop` produces clean shutdown logs — no stack traces, no "still running" warnings
      for the `dbExecutor` thread pool (should shut down within its 10-second grace period per
      `SQLDataStore`'s shutdown logic).
- [ ] **Broken YAML recovery**: pick any shipped file, e.g.
      `plugins/Valmora/items/example.yml`, and intentionally break its YAML syntax (remove a colon,
      unbalanced quote, etc.). Run `/valmora reload`. Confirm:
  - The specific file's parse error is reported clearly in console/to the reloading admin.
  - Other modules/files still loaded successfully — one broken file shouldn't cascade into a
    half-enabled plugin state.
  - Fix the file and reload again to confirm recovery.
- [ ] Note (not a test, just a reminder): there's currently no debug/verbose logging toggle in
      `config.yml` (flagged in the release checklist) — if you need more detail than the default
      `logger.info`/`warning` calls provide while debugging something in this pass, you'll need to
      temporarily add logging in code rather than flip a config flag.
- [ ] Confirm no plugin (other than the required `packetevents` hard dependency) needs to be
      present for Valmora to start cleanly — test a startup with `packetevents` deliberately
      **removed** from `plugins/` and confirm the failure mode is a clear "missing dependency"
      message, not a silent partial start.

---

## 8. Final sign-off

- [ ] Every phase above is either checked off or has a written note on why it was skipped (missing
      hardware/second account/time) — no silent gaps.
- [ ] Any new bug found during this pass is filed (or fixed directly, per your usual workflow) and
      cross-referenced back into `docs/V1_RELEASE_CHECKLIST.md`.
- [ ] Delete `plugins/Valmora/items/qa_test_items.yml` (§2.6's `TAKE_COINS` test item) and revert
      any temporary config changes (`bank-interest-interval-seconds`, `npc-skin-server.enabled`,
      the MySQL switch) back to their intended v1 defaults before calling this pass complete.
