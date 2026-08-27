export type DocSection = {
  heading: string;
  body?: string[];
  code?: { lang: string; content: string };
  list?: string[];
  table?: { headers: string[]; rows: string[][] };
};

export type DocEntry = {
  slug: string;
  title: string;
  category: string;
  summary: string;
  sections: DocSection[];
};

export const DOC_CATEGORIES = [
  "Tutorials",
  "Core Systems",
  "Items & Combat",
  "World & Content",
  "Progression",
] as const;

export const DOCS: DocEntry[] = [
  {
    slug: "tutorial-first-item",
    title: "Tutorial: Your First Custom Item",
    category: "Tutorials",
    summary: "Go from a blank YAML file to a working legendary sword with a scripted ability, in five steps.",
    sections: [
      {
        heading: "1. Create the file",
        body: [
          "Every item lives in a YAML file under plugins/Valmora/items/ — any filename works, and one file can hold as many items as you like. Create plugins/Valmora/items/my_items.yml.",
        ],
      },
      {
        heading: "2. Give it a body",
        body: [
          "Pick a top-level key — that becomes the item's ID everywhere else in the plugin (recipes, mob loot, /item give). Give it a material, a rarity, and a couple of stats.",
        ],
        code: {
          lang: "yaml",
          content: `# plugins/Valmora/items/my_items.yml
frostbrand:
  name: "Frostbrand"
  material: DIAMOND_SWORD
  rarity: EPIC
  item-type: SWORD
  stats:
    damage: 35
    strength: 8
    crit_chance: 10`,
        },
      },
      {
        heading: "3. Load it and check it",
        body: [
          "Run /valmora reload (requires valmora.admin). Then confirm it actually parsed:",
        ],
        code: { lang: "text", content: "/item list          # your ID should be in there\n/item info frostbrand\n/item give frostbrand" },
      },
      {
        heading: "4. Add an ability",
        body: [
          "Give the sword a right-click ability: a short cooldown, a mana cost, and one mechanic. This uses the same abilities: block documented in full on the Items & Abilities page.",
        ],
        code: {
          lang: "yaml",
          content: `frostbrand:
  name: "Frostbrand"
  material: DIAMOND_SWORD
  rarity: EPIC
  item-type: SWORD
  stats:
    damage: 35
    strength: 8
    crit_chance: 10
  abilities:
    frost_nova:
      name: "Frost Nova"
      trigger: RIGHT_CLICK
      cooldown: 8.0
      mana-cost: 25.0
      description:
        - "Freezes nearby enemies."
      mechanics:
        - type: apply_effect
          params:
            effect: slowness
            duration: 4.0
            amplifier: 3
            target: "@enemies_in_radius{r=6}"`,
        },
      },
      {
        heading: "5. Reload and test",
        body: [
          "/valmora reload again, then /item give frostbrand and right-click. If nothing happens, check the console log for a parse warning first — a bad stat key or unknown mechanic type fails the whole item silently rather than half-loading it.",
          "From here: read Items & Abilities for the other 13 mechanics and every target selector, or open the Item Generator to build the next one without hand-writing YAML at all.",
        ],
      },
    ],
  },
  {
    slug: "tutorial-quest-chain",
    title: "Tutorial: Build a Quest Chain",
    category: "Tutorials",
    summary: "Package up an NPC, a conversation, and a two-objective quest that pays out on completion.",
    sections: [
      {
        heading: "1. Create the package folder",
        body: [
          "A folder becomes a quest package the moment it contains a quest.yml. Create plugins/Valmora/quests/my_village/quest.yml.",
        ],
        code: { lang: "yaml", content: "# plugins/Valmora/quests/my_village/quest.yml\npackage:\n  enabled: true\n\nnpc_conversations:\n  elder: elder_greeting" },
      },
      {
        heading: "2. Place the NPC",
        body: [
          "NPCs live in plugins/Valmora/npcs/, not inside the quest package. Bind it to the conversation id you just declared.",
        ],
        code: {
          lang: "yaml",
          content: `# plugins/Valmora/npcs/my_village.yml
elder:
  display-name: "<gold>Village Elder"
  entity-type: VILLAGER
  world: world
  x: 100
  y: 64
  z: 100
  conversation: elder_greeting`,
        },
      },
      {
        heading: "3. Write the quest",
        body: [
          "Add a sibling file in the same package folder — plugins/Valmora/quests/my_village/quests.yml — with the actual objectives. Rewards belong on the objective that finishes the quest.",
        ],
        code: {
          lang: "yaml",
          content: `# plugins/Valmora/quests/my_village/quests.yml
quests:
  wolf_problem:
    name: "<yellow>The Wolf Problem"
    objectives:
      talk_first:
        type: talk_to_npc
        target: elder
        amount: 1
      kill_wolves:
        type: kill
        target: WOLF
        amount: 5
        events:
          - "notify <green>The wolves are dealt with! category:quest_complete"
          - "give EMERALD:5"
          - "sound player entity.player.levelup"`,
        },
      },
      {
        heading: "4. Write the conversation",
        body: [
          "Add conversations.yml in the same folder. The player's choice fires quest_start, and the NPC's later greeting branches on whether the quest is done — both via named conditions.",
        ],
        code: {
          lang: "yaml",
          content: `# plugins/Valmora/quests/my_village/conversations.yml
conditions:
  not_started: "!quest wolf_problem in_progress"
  done: "quest wolf_problem completed"

conversations:
  elder_greeting:
    quester: "<gold>Village Elder"
    first: [greeting]
    NPC_options:
      greeting:
        text: "<yellow>Wolves are circling the village. Will you help?"
        conditions: "not_started"
        pointers: [accept]
    player_options:
      accept:
        text: "I'll take care of it."
        events: "quest_start wolf_problem"`,
        },
      },
      {
        heading: "5. Reload and play it",
        body: [
          "/valmora reload, then right-click the Elder in-game to trigger the conversation. Track it with /quest track wolf_problem to watch progress on your sidebar.",
          "From here: the Quests page covers all 36 objective types, named/reusable events and conditions, templates, and quest boards for pool-style repeatable quests.",
        ],
        code: { lang: "text", content: "/quest track wolf_problem\n/quest journal" },
      },
    ],
  },
  {
    slug: "tutorial-boss-fight",
    title: "Tutorial: A Boss Fight From Scratch",
    category: "Tutorials",
    summary: "A leveled mob, a boss bar, a timed ability, and a pipeline-driven phase transition — no Java.",
    sections: [
      {
        heading: "1. Start with a plain mob",
        body: ["Base it on a vanilla entity type and give it a real stat block before touching anything boss-specific."],
        code: {
          lang: "yaml",
          content: `# plugins/Valmora/mobs/my_boss.yml
stone_warden:
  category: BOSS
  type: IRON_GOLEM
  name: "<red>Stone Warden"
  level: 30
  stats:
    health: 8000
    damage: 120
    defense: 150
  persistent: true`,
        },
      },
      {
        heading: "2. Add the boss bar",
        body: ["This alone is what makes it read as a boss fight in-game, independent of anything mechanical."],
        code: {
          lang: "yaml",
          content: `  boss-bar:
    enabled: true
    color: RED
    style: SEGMENTED_10
    range: 40`,
        },
      },
      {
        heading: "3. Give it a timed ability",
        body: ["Abilities use the same trigger/mechanic system as items — ON_TIMER fires on an interval, independent of anything the player does."],
        code: {
          lang: "yaml",
          content: `  abilities:
    ground-slam:
      name: "Ground Slam"
      trigger: ON_TIMER
      interval: 140
      cooldown: 6.0
      mechanics:
        - type: damage
          params:
            damage: 40
            damage-type: PHYSICAL
            target: "@enemies_in_radius{r=6}"`,
        },
      },
      {
        heading: "4. Reload and spawn it",
        body: ["Admin-only, same as every other mob."],
        code: { lang: "text", content: "/valmora reload\n/mob spawn stone_warden" },
      },
      {
        heading: "5. Give it a phase transition with a pipeline stage",
        body: [
          "This is the part that isn't possible from the mob file alone: disable Ground Slam once the boss drops below 50% HP, using a mob_pipeline.yml stage. mob.ability_id is only available at mob:pre_ability/post_ability — see the Pipelines page for the full insertion-point reference.",
        ],
        code: {
          lang: "yaml",
          content: `# plugins/Valmora/mob_pipeline.yml
stages:
  - id: stone_warden_phase_lock
    when: pre_ability
    conditions:
      - "$mob.ability_id$ == ground-slam"
      - "$target.health$ / $target.max_health$ < 0.5"
    on-pass:
      - "interrupt"`,
        },
      },
      {
        heading: "6. Add a phase-2 ability instead of just silencing phase 1",
        body: [
          "A second ON_TIMER ability with the opposite health condition on its own conditions: list (not the pipeline) gives you a real two-phase fight without touching Java at all.",
        ],
        code: {
          lang: "yaml",
          content: `  abilities:
    enrage-slam:
      name: "Enrage Slam"
      trigger: ON_TIMER
      interval: 80
      cooldown: 3.0
      conditions:
        - "$target.health$ / $target.max_health$ < 0.5"
      mechanics:
        - type: script
          params:
            events:
              - "notify <red><bold>The Stone Warden enrages!</bold></red>"
        - type: damage
          params:
            damage: 60
            damage-type: PHYSICAL
            target: "@enemies_in_radius{r=8}"`,
        },
      },
    ],
  },
  {
    slug: "items",
    title: "Items & Abilities",
    category: "Items & Combat",
    summary:
      "Every sword, ring, and boot on the server is one YAML entry — stats, rarity, and active abilities included.",
    sections: [
      {
        heading: "What it is",
        body: [
          "Every custom item is a top-level key in a YAML file under items/. It carries a material, a rarity, a stat block, and an optional set of abilities that trigger on right-click, on hit, on kill, while worn, and more.",
          "Items live as data, not code — adding a new legendary sword means writing YAML, not touching a Java class. All custom items carry their identity, rarity, type, stats, and abilities as PersistentDataContainer data on the underlying ItemStack, so any other system (recipes, quests, mob loot) can reference an item purely by its ID.",
          "Vanilla items are translated automatically into Valmora items with a vanilla_<material> ID, so the stat and lore pipeline treats every item in the game uniformly — a plain DIAMOND_SWORD a player finds in a chest gets the same lore formatting as a hand-authored legendary.",
        ],
      },
      {
        heading: "Minimal item",
        body: ["The smallest useful item is a name, a material, and a stat block."],
        code: {
          lang: "yaml",
          content: `ironbreaker:
  name: "Ironbreaker"
  material: IRON_SWORD
  rarity: UNCOMMON
  item-type: SWORD
  stats:
    DAMAGE: 25
    STRENGTH: 10
    CRIT_CHANCE: 15`,
        },
      },
      {
        heading: "Adding an ability",
        body: [
          "Abilities are triggers (RIGHT_CLICK, ON_HIT, ON_KILL, SNEAK, PASSIVE, EQUIP, and more) wired to an ordered list of mechanics — small, composable effects like damage, heal, launch_projectile, or apply_effect.",
          "An ability can gate itself behind conditions (script expressions), a cooldown in seconds, and a mana cost — all three are checked before any mechanic runs, and each failure gives the player its own feedback (a red cooldown message, an aqua \"Not enough Mana!\" message).",
        ],
        code: {
          lang: "yaml",
          content: `flame_wave:
  name: "Flame Wave"
  material: DIAMOND_SWORD
  rarity: EPIC
  item-type: SWORD
  stats: { damage: 30.0, strength: 5.0 }
  abilities:
    flame_wave:
      name: "Flame Wave"
      trigger: RIGHT_CLICK
      target-range: 12.0
      cooldown: 5.0
      mana-cost: 20.0
      description:
        - "Deals damage in a radius."
      conditions:
        - "$player.weapon_damage$ > 100"
      mechanics:
        - type: damage
          params:
            damage: 10
            damage-type: MAGIC
            target: "@target"`,
        },
      },
      {
        heading: "Chaining mechanics and targeting",
        body: [
          "The mechanics list runs top to bottom — an ability can knock enemies up, then ignite them, then apply a slow, all from one right-click. Each mechanic resolves its own target selector, so a single ability can hit the caster with one effect and nearby enemies with another.",
          "Projectile mechanics go one step further: launch_projectile accepts nested on-hit mechanics that only fire on impact, which is how a staff can shoot a bolt that only applies its slow effect when it actually lands on something.",
        ],
        list: [
          "@player / @self — the caster",
          "@target — the ability's resolved target entity",
          "@enemies_in_radius{r=10} — hostile mobs within 10 blocks",
          "@allies_in_radius{r=10} — players within 10 blocks, caster included",
          "@cone{range=8, angle=45} — a forward-facing cone",
        ],
      },
      {
        heading: "Damage over time, in one mechanic",
        body: [
          "The damage and heal mechanics both accept ticks and interval for damage/heal-over-time — the first hit lands immediately, then the remaining ticks are scheduled interval seconds apart.",
        ],
        code: {
          lang: "yaml",
          content: `- type: DAMAGE
  params:
    damage: 42
    damage-type: MAGIC
    target: "@enemies_in_radius{r=10}"
    ticks: 10
    interval: 1.0`,
        },
      },
      {
        heading: "Rarities",
        body: ["Rarity drives the display-name color and reforge cost, from common to the top of the ladder."],
        list: ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE"],
      },
      {
        heading: "Validation and reloading",
        body: [
          "The loader is strict on purpose: material must be a real Bukkit material, every stats key must be a known stat ID, and every mechanics type must be a registered mechanic. An unknown key doesn't just get ignored — it fails the whole item's load, and a warning is logged, so a typo can never silently ship a broken sword.",
          "Edit a YAML file and run /valmora reload — items, mobs, and every other module reload in dependency order with no restart. Use /item list to confirm your item registered, and /item info <id> to inspect its parsed stats, rarity, and abilities in-game.",
        ],
      },
      {
        heading: "Going further: hooking an ability from outside the item file",
        body: [
          "An ability's own trigger/condition/cooldown/mana gate isn't the only place you can react to it firing. item_pipeline.yml exposes item:pre_ability (before the mechanics run — interrupt cancels just that firing, cooldown/mana already spent stays spent) and item:post_ability (after), both carrying $item.ability_id$ and $item.ability_trigger$. That's how you add a server-wide rule — \"no ability may fire for a player standing in a PvP-disabled zone\", \"log every legendary ability use to a Discord webhook via a Java hook\" — without editing every item file that has an ability.",
          "See the Pipelines page for the full insertion-point list, the exact context each one carries, and worked examples combining this with custom stats and other modules.",
        ],
        code: {
          lang: "yaml",
          content: `# item_pipeline.yml — silence one specific ability server-wide without touching its item file
stages:
  - id: disable_flame_wave_in_hub
    when: pre_ability
    conditions:
      - "$item.ability_id$ == flame_wave"
      - "zone hub"
    on-pass:
      - "notify <red>Abilities are disabled here.</red>"
      - "interrupt"`,
        },
      },
    ],
  },
  {
    slug: "mobs",
    title: "Mobs",
    category: "World & Content",
    summary:
      "Custom mobs with their own stat pools, loot tables, and abilities — layered directly on vanilla entity types.",
    sections: [
      {
        heading: "What it is",
        body: [
          "Mob definitions extend a vanilla entity type with a health/damage profile, equipment, drop tables, and the same ability/mechanic system items use — a zombie and a raid boss are the same schema at different scale.",
          "Every field but category and type has a sensible default, but a mob without name or stats.health will crash on spawn or spawn with 0 HP — always set both.",
        ],
      },
      {
        heading: "A minimal mob",
        body: ["Base a mob on any vanilla entity type, give it a level and a stat block, and it's ready to spawn."],
        code: {
          lang: "yaml",
          content: `# plugins/Valmora/mobs/mymobs.yml
forest_goblin:
  category: BEAST
  name: "<green>Forest Goblin"
  type: ZOMBIE
  level: 5
  stats:
    health: 80.0
    damage: 12.0
    speed: 0.28
  equipment:
    main-hand: WOODEN_SWORD`,
        },
      },
      {
        heading: "Loot tables",
        body: [
          "Each drop entry rolls independently with its own chance and a min/max amount range — there's no shared drop pool to balance against. Marking a drop luck-affected scales its chance with the killer's Luck stat: chance × (1 + Luck / 100), so a Luck of 100 doubles it.",
          "Drops are placed directly into the killer's inventory list, not dropped as an item entity — you don't need to interrupt combat to pick anything up.",
        ],
        code: {
          lang: "yaml",
          content: `cave_guardian:
  category: GOLEM
  name: "<gray>Cave Guardian"
  type: IRON_GOLEM
  level: 15
  stats:
    health: 120.0
    damage: 12.0
    speed: 0.25
  loot-table:
    drops:
      - item: raw_ferrite
        min-amount: 1
        max-amount: 3
        chance: 0.5
        luck-affected: true`,
        },
      },
      {
        heading: "Bosses: boss bars and timed abilities",
        body: [
          "A boss is just a mob with a boss-bar block and/or abilities — there's no separate boss system to learn. Abilities fire on triggers: a repeating timer, crossing a health percentage, attacking, being damaged, spawning, or dying, and timer abilities can carry their own random chance and cooldown on top of the interval.",
          "Bosses aren't persistent by default — set persistent: true or the entity (and its HP/cooldown state) can be lost to a chunk unload or restart.",
        ],
        code: {
          lang: "yaml",
          content: `forge_titan:
  category: BOSS
  type: IRON_GOLEM
  name: "<red>Forge Titan</red>"
  level: 50
  base-xp: 500
  gold-reward: 1000
  stats:
    health: 25000
    damage: 300
    defense: 400
    strength: 60
    crit-chance: 25
    crit-damage: 80
  resistances:
    FIRE: 1.0          # immune to fire
    EXPLOSION: 0.5     # takes half explosion damage
  persistent: true
  boss-bar:
    enabled: true
    color: RED
    style: SEGMENTED_10
    range: 40
  abilities:
    ground-slam:
      name: "Ground Slam"
      trigger: ON_TIMER
      interval: 120
      chance: 0.5
      cooldown: 6.0`,
        },
      },
      {
        heading: "Combat math and resistances",
        body: [
          "Mob damage scales as base-damage + level − 1, so a higher-level tag on the same base stats is a quick way to make an encounter hit harder without redesigning it. Mobs read defense through the same 100 / (defense + 100) formula players use, and can carry strength and crit chance/damage of their own, including landing crits on the player.",
          "Per-damage-type resistances (FIRE, EXPLOSION, and the rest of the damage-type list) are a flat multiplier from 0 (no reduction) to 1 (immune) — useful for a fire-elemental boss that shrugs off a fire-mage's kit entirely.",
        ],
      },
      {
        heading: "Nameplates and rewards",
        body: [
          "A live nameplate — [Lv.15] Cave Guardian 104/120❤ — re-renders on every hit so the numbers always track current HP. Landing the killing blow pays base-xp × level combat skill XP and, if set, the mob's gold-reward straight into the killer's purse.",
        ],
      },
      {
        heading: "Full YAML schema",
        body: [
          "Every field beyond category and type has a working default, straight from the parser — this is the complete list, not just the fields the examples above happened to use.",
        ],
        table: {
          headers: ["Field", "Default", "Notes"],
          rows: [
            ["category", "required", "BEAST, UNDEAD, ENDER, NETHER, AQUATIC, ARTHROPOD, ILLAGER, GOLEM, BOSS, OTHER, or any category added in mob_categories.yml."],
            ["type", "required", "Any vanilla EntityType (ZOMBIE, IRON_GOLEM, …)."],
            ["name", "the mob's id", "MiniMessage display name shown in the nameplate."],
            ["level", "1", "Feeds the damage formula (base-damage + level − 1) and combat XP payout."],
            ["stats.health / damage / speed / defense / strength / crit-chance / crit-damage", "0 (health defaults to 0 HP if unset — always set it)", "Same combat stats players have; can also be set unnested as stats: {} is optional — health/base-damage/speed/defense also read as flat top-level keys if stats: is omitted."],
            ["resistances.<DAMAGE_TYPE>", "0.0", "0 = no reduction, 1 = immune, per damage type."],
            ["knockback-resistance", "0.0", "0–1, vanilla knockback resistance."],
            ["no-ai / silent / glowing / baby / persistent / prevent-sun-burn", "false", "Direct passthroughs to the equivalent vanilla entity flags."],
            ["ai.aggro-range / ai.leash-range", "vanilla default", "Overrides the entity's targeting/leash distances."],
            ["natural-spawn.enabled / chance / max-nearby", "enabled: true", "Governs whether this mob can replace vanilla natural spawns, and how densely."],
            ["base-xp", "0", "Combat XP paid to the killer is base-xp × level."],
            ["gold-reward", "0", "Coins paid directly into the killer's purse on death."],
            ["damage-type", "MELEE", "The DamageType this mob's own attacks are tagged with (affects resistances on the victim side)."],
            ["equipment.main-hand / off-hand / helmet / chestplate / leggings / boots", "none", "Vanilla material or Valmora item ID — resolved through the item module either way."],
            ["loot-table.drops[].item / min-amount / max-amount / chance / luck-affected", "min/max 1, chance 1.0", "Rolled independently per entry; luck-affected scales chance by (1 + Luck/100)."],
            ["persistent", "false", "Keeps a boss's entity (and its HP/cooldown state) alive across chunk unload/restart."],
            ["boss-bar.enabled / color / style / range", "disabled", "color is a BossBar.Color name, style a BossBar.Overlay name (e.g. SEGMENTED_10), range in blocks."],
            ["abilities.<id>.trigger / interval / chance / cooldown", "—", "Same ability/mechanic system as items — see Items & Abilities."],
          ],
        },
      },
    ],
  },
  {
    slug: "gui-machines",
    title: "GUIs & Machines",
    category: "Core Systems",
    summary:
      "A data-driven inventory UI system — crafting tables, alchemy stations, and forges are all YAML-defined screens.",
    sections: [
      {
        heading: "Component model",
        body: [
          "Every menu in Valmora — the stats page, the skill tree, the bank, and every crafting machine — is a GUI defined entirely in YAML and rendered live into the player's inventory. Nothing is hardcoded: redesigning a menu, changing a button's behavior, or adding a page is a file edit and a reload.",
          "Each GUI is built from typed components: INPUT slots that accept player items, OUTPUT slots that display and dispense results, DISPLAY buttons with per-click actions, and PAGINATED lists for browsing long collections.",
        ],
      },
      {
        heading: "Anatomy of a definition",
        body: [
          "A layout is a list of strings — one string per inventory row, one character per slot — mapped onto a components block that says what each character does. Multi-character keys are supported for readability in bigger menus.",
        ],
        code: {
          lang: "yaml",
          content: `bank:
  title: "<dark_aqua>Grand Bank"
  rows: 4
  layout:
    - "aaaaaaaaaaa"
    - "abcdefghijk"
    - "aaaaaaaaaaa"
  components:
    a:
      type: DISPLAY
      display-item:
        material: GRAY_STAINED_GLASS_PANE
        name: "<gray> "
    b:
      type: INPUT
      id: deposit_value
  on-open:
    actions:
      - "open_dialog_input pending_amount"`,
        },
      },
      {
        heading: "Event blocks",
        body: [
          "Four event blocks cover the whole lifecycle: on-open, on-close, on-slot-update (an INPUT slot changed), and on-update (a repeating timer). Each runs an ordered action list, and a leading condition action can short-circuit straight into fail-actions if it doesn't hold.",
          "Click actions are keyed by Bukkit ClickType (LEFT, RIGHT, SHIFT_LEFT, MIDDLE, and so on), so the same button can behave completely differently depending on how it's clicked.",
        ],
        code: {
          lang: "yaml",
          content: `b:
  type: DISPLAY
  display-item:
    material: PLAYER_HEAD
    name: "<gold>Back to bank"
  actions:
    LEFT:
      conditions:
        - "$prop.pending_amount$ != ''"
      actions:
        - "open_gui bank"`,
        },
      },
      {
        heading: "Crafting pipeline",
        body: [
          "Machines that declare a machine: id are wired straight into the recipe engine — the output slot fills in the instant the placed ingredients match a recipe, and clears the moment they stop matching.",
          "The gui_force_craft event snapshots the INPUT slots, hands them to RecipeEngine.craft(), places the result in OUTPUT, and re-renders — guarded by a per-session craftingLocked flag so a second craft call mid-pipeline is a no-op instead of a dupe.",
        ],
        list: ["Crafting Table — 3×3 grid, shaped/shapeless/vanilla", "Forge — 2 exact slots: base + material", "Alchemy Table — timer-based brews", "Anvil / Reforge Anvil — dynamic handlers, no YAML recipes"],
      },
      {
        heading: "Why items are safe to close out on",
        body: [
          "Closing a machine — Esc or the close button — returns everything in its INPUT slots to the player's inventory automatically, or drops it at their feet if the inventory is full. There's no way to lose items by backing out of a craft mid-way.",
        ],
      },
      {
        heading: "Full YAML schema",
        body: ["The top-level fields any GUI file can set, and the field set each component type reads."],
        table: {
          headers: ["Field", "Where", "Notes"],
          rows: [
            ["title", "top level", "MiniMessage inventory title."],
            ["rows", "top level", "Inventory height; layout can define fewer rows and let this pad the rest blank."],
            ["layout", "top level", "List of strings, one per row, one character per slot — maps onto components."],
            ["components.<key>.type", "component", "DISPLAY (default), INPUT, OUTPUT, PAGINATED, PREVIOUS_PAGE, or NEXT_PAGE."],
            ["components.<key>.display-item", "DISPLAY / page buttons", "material, name, lore — the button's rendered item."],
            ["components.<key>.actions.<CLICK_TYPE>", "DISPLAY", "Keyed by Bukkit ClickType (LEFT, RIGHT, SHIFT_LEFT, MIDDLE, …); each holds its own conditions/actions/fail-actions."],
            ["components.<key>.id", "INPUT / OUTPUT", "Names the slot for $gui.input.<id>.*$ variables and for EXACT_SLOT recipes."],
            ["components.<key>.list / iterator / sort / states", "PAGINATED", "Drives what data renders across the paginated slots and how each item's look varies by state."],
            ["machine", "top level", "Links this GUI to a recipe engine machine id — enables live output-slot preview and gui_force_craft."],
            ["command / command-permission", "top level", "Registers a dynamic /command opening this GUI directly, gated by the given permission."],
            ["on-open / on-close / on-slot-update / on-update.conditions / actions / fail-actions", "top level", "The four lifecycle blocks — see Event blocks above."],
            ["update-interval", "top level", "Ticks between on-update firings."],
          ],
        },
      },
    ],
  },
  {
    slug: "recipes",
    title: "Recipe Engine",
    category: "Core Systems",
    summary: "Three-tier matching — a custom handler, then YAML recipes, then vanilla crafting — for every machine.",
    sections: [
      {
        heading: "Match order",
        body: [
          "A craft attempt is checked against a registered DynamicMachineHandler first (for fully custom logic like the anvil's enchant-merging rules), then static YAML recipes for that machine, then vanilla Bukkit recipes as a fallback scoped to the crafting table.",
          "An item: value in a recipe can be a Valmora item ID or a plain Bukkit Material name — the two are freely mixable in the same recipe, and every output is formatted as a proper Valmora item automatically (rarity color, stat lore, the works).",
        ],
      },
      {
        heading: "Recipe types",
        body: [
          "EXACT_SLOT matches specific named input slots exactly — used by the forge (base + material) and other two-slot machines. SHAPED matches a 3×3 grid position by position. SHAPELESS matches a bag of ingredients regardless of which slot each one sits in.",
        ],
        code: {
          lang: "yaml",
          content: `healing_potion:
  machine: alchemy
  type: SHAPELESS
  inputs:
    - { item: NETHER_WART, amount: 1 }
    - { item: GLASS_BOTTLE, amount: 1 }
  outputs:
    result: { item: healing_potion, amount: 1 }
  on-craft:
    - "sound player block.brewing_stand.brew"`,
        },
      },
      {
        heading: "A shaped recipe",
        body: [
          "Shaped recipes key their inputs 0–8 across the 3×3 grid (left to right, top to bottom) — the pattern can be placed anywhere in the grid, but the relative positions must match.",
        ],
        code: {
          lang: "yaml",
          content: `# 0 1 2
# 3 4 5
# 6 7 8
reinforced_ingot:
  machine: crafting_table
  type: SHAPED
  inputs:
    "1": { item: IRON_INGOT, amount: 1 }
    "4": { item: raw_ferrite, amount: 1 }
    "7": { item: IRON_INGOT, amount: 1 }
  outputs:
    result: { item: reinforced_ingot, amount: 1 }`,
        },
      },
      {
        heading: "An exact-slot recipe",
        body: [
          "EXACT_SLOT keys its inputs by the GUI's own named INPUT slot ids rather than a grid position — the forge's base + material layout, for instance.",
        ],
        code: {
          lang: "yaml",
          content: `sharpened_pickaxe:
  machine: forge
  type: EXACT_SLOT
  inputs:
    base: { item: DIAMOND_PICKAXE, amount: 1 }
    material: { item: raw_ferrite, amount: 5 }
  outputs:
    result: { item: ferrite_pickaxe, amount: 1 }`,
        },
      },
      {
        heading: "Machine IDs",
        body: ["A recipe's machine: must match a GUI's own machine: id, or it never matches anything."],
        table: {
          headers: ["Machine ID", "GUI"],
          rows: [
            ["crafting_table", "guis/crafting.yml"],
            ["forge", "guis/forge.yml"],
            ["alchemy", "guis/alchemy.yml"],
            ["anvil", "guis/anvil.yml (dynamic — no YAML recipes)"],
            ["reforge_anvil", "guis/reforge_anvil.yml (dynamic)"],
          ],
        },
      },
      {
        heading: "Reload and folders",
        body: [
          "Recipes live under recipes/, with subfolders supported purely for organization (recipes/crafting/, recipes/alchemy/, recipes/anvil/ — YamlLoader recurses). Shipped defaults are copied to disk on first run and never overwritten, so edits are always safe. Run /valmora reload after any change; the log reports how many recipes loaded and flags any parse errors by file.",
        ],
      },
    ],
  },
  {
    slug: "scripting",
    title: "Script DSL",
    category: "Core Systems",
    summary: "A compact string-based language for rewards, GUI actions, and ability effects — no plugin restart needed.",
    sections: [
      {
        heading: "Three subsystems, one engine",
        body: [
          "The script module is the DSL underneath quests, GUIs, items, NPCs, skills, collections, and zones. It has three parts that always work together: variables read live values, conditions gate behavior on those values, and events cause side effects.",
          "All three run through an internal ExecutionContext carrying the current caster, target, location, and parameters — you never construct this yourself, the engine builds it for each evaluation.",
        ],
      },
      {
        heading: "Event strings",
        body: [
          "Every scripted line is one event call: an event name, its arguments, and optional notify / delay:<ticks> modifiers. notify sends a confirmation message back to the player who triggered it; delay schedules the event that many ticks in the future instead of running it immediately.",
        ],
        code: { lang: "text", content: "give DIAMOND:3 notify\nvariable add player.var.strength 5 delay:20" },
      },
      {
        heading: "Variables",
        body: [
          "Any string can embed a live value with $namespace.path$ — player stats, GUI slot contents, the in-game season, or a random range — and expressions support arithmetic on top, resolved fresh every time the string is rendered.",
        ],
        code: {
          lang: "text",
          content: "$player.stat.HEALTH$\n$gui.input.ingredient.id$\n$time.season$\n$param.level$*10\n$range.1.6$",
        },
      },
      {
        heading: "Conditions",
        body: [
          "A conditions: list is AND logic — every entry must hold, and a leading ! negates a single check. Conditions aren't limited to raw expressions: dedicated shorthand covers the checks admins write most often.",
        ],
        table: {
          headers: ["Form", "Example"],
          rows: [
            ["Tag", '"tag quest_started" / "!tag quest_complete"'],
            ["Expression", '"$player.stat.HEALTH$ > 50 and $player.stat.SPEED$ < 200"'],
            ["Health / hunger", '"health 20" / "hunger 15"'],
            ["Location", '"location 100;64;200;world 10"'],
            ["Zone", '"zone pvp_arena"'],
            ["Quest", '"quest my_quest COMPLETED"'],
          ],
        },
      },
      {
        heading: "In a GUI click action",
        body: [
          "Conditions and events combine everywhere the DSL is read — here a bank button only lets the player back out once a pending amount has actually been typed.",
        ],
        code: {
          lang: "yaml",
          content: `actions:
  LEFT:
    conditions:
      - "$prop.pending_amount$ != ''"
    actions:
      - "open_gui bank"
    fail-actions:
      - "sound player entity.villager.no"`,
        },
      },
      {
        heading: "What players actually see",
        body: [
          "Players never see the scripts themselves — they see the results: stat-scaled damage numbers, an action-bar \"Not enough Mana!\", a scoreboard line updating every tick, a quest journal entry ticking forward. The DSL is the plumbing; the player experience is entirely ordinary Minecraft feedback.",
        ],
      },
      {
        heading: "Composing everything: a self-escalating event with no Java",
        body: [
          "The DSL's real power shows up once foreach, run_script, counter, and a pipeline stage compose. Here's a full worked chain: a boss ability schedules a ring of ticking fire around every nearby player, and a separate combat_pipeline.yml stage tracks how many players are simultaneously standing in it — escalating a warning the more crowded it gets. Nothing here is a special case; it's five ordinary events and one ordinary pipeline stage.",
        ],
        code: {
          lang: "yaml",
          content: `# A boss ability — ignites the ground under every nearby player, once per second, for 5 seconds
abilities:
  meteor_storm:
    name: "Meteor Storm"
    trigger: ON_TIMER
    interval: 200
    mechanics:
      - type: script
        params:
          events:
            - "run_script 20 5 foreach @nearby:15 apply_potion slowness 1 1.2"

# combat_pipeline.yml — count how many players are slowed right now and escalate a warning
stages:
  - id: meteor_crowd_warning
    when: post_application
    on-pass:
      - "counter increment player.var.meteor_hits"
      - "condition $player.var.meteor_hits$ >= 3"
      - "notify <red><bold>Spread out! Meteor Storm is stacking!</bold></red>"`,
        },
      },
      {
        heading: "Reaching across modules from one condition",
        body: [
          "Because every namespace shares one resolver, a single condition string can mix a stat, a quest, a zone, and the calendar in one line — there's no per-system condition language to relearn. This is what makes combos like \"double XP for VIPs during the Harvest Festival, but only inside the farm zone\" a one-line gate rather than a feature request.",
        ],
        code: {
          lang: "text",
          content: 'condition $time.season$ == "AUTUMN" and $player.var.vip$ == "true" and $player.stat.farming_fortune$ > 0\nzone farm',
        },
      },
    ],
  },
  {
    slug: "economy",
    title: "Economy",
    category: "Progression",
    summary: "A coin-based economy wired into items, quests, and shops through the shared script and stat systems.",
    sections: [
      {
        heading: "Two balances",
        body: [
          "Every player has a purse (spendable, carried on them) and a bank (safe storage). Both save automatically on logoff and clean shutdown — there's nothing a player needs to do to protect their coins beyond banking them.",
        ],
        table: {
          headers: ["Balance", "What it is", "Risk"],
          rows: [
            ["Purse", "Spendable wallet — pays for quests, reforging, ability costs", "Dying removes exactly half of it"],
            ["Bank", "Storage moved in from the purse", "Never touched by death"],
          ],
        },
      },
      {
        heading: "Earning and spending",
        body: [
          "Coins enter the economy through mob gold-reward payouts, quest and slayer completion via the economy_add script event, and item abilities with a give_coins mechanic. They leave it through slayer activation costs, reforging, and item abilities with a take_coins mechanic — all flowing through the same purse.",
        ],
      },
      {
        heading: "The /eco command",
        body: [
          "Admin-only, gated by valmora.admin. Amounts accept abbreviations and arithmetic, not just plain numbers.",
        ],
        code: {
          lang: "text",
          content: "/eco get Steve            # both balances\n/eco set Steve bank 0\n/eco add Steve purse 2.5k\n/eco remove Steve purse (1k+500)*2",
        },
      },
      {
        heading: "Scripting the economy",
        body: [
          "Any reward list — a quest completion, a collection milestone, an item ability — can pay or charge coins with the same two events.",
        ],
        code: { lang: "text", content: "economy_add 250 notify\neconomy_remove 100" },
      },
      {
        heading: "The legacy coins variable",
        body: [
          "Some older content (the anvil's item-merging cost, for instance) still reads a separate player.var.coins profile variable rather than the purse/bank economy — if a feature doesn't touch a player's purse, that's usually why.",
        ],
      },
    ],
  },
  {
    slug: "skills-progression",
    title: "Skills & Progression",
    category: "Progression",
    summary: "XP-driven skill trees and account-wide progression tracks that other modules read stats from.",
    sections: [
      {
        heading: "Nine skills, one curve",
        body: [
          "Mining, Farming, Foraging, Fishing, Combat, Alchemy, Carpentry, Enchanting, and Taming each cap at level 60 and share the same cumulative XP curve — so a level 20 in any skill represents the same total investment.",
        ],
        list: ["mining", "farming", "foraging", "fishing", "combat", "alchemy", "carpentry", "enchanting", "taming"],
      },
      {
        heading: "The XP curve",
        body: [
          "Thresholds are cumulative total XP, not per-level cost — reaching level 10 means 3,000 total XP earned in that skill, regardless of how it was earned.",
        ],
        code: {
          lang: "text",
          content: "Lvl 1 → 10 XP\nLvl 5 → 200 XP\nLvl 10 → 3,000 XP\nLvl 20 → 75,000 XP\nLvl 30 → 450,000 XP\nLvl 60 → capped",
        },
      },
      {
        heading: "Why it fits together",
        body: [
          "Skills sit early in the module chain so combat, items, and mobs can all read a player's skill level when resolving formulas — a sword's damage formula can scale off $player.skill.mining.level$ just as easily as a flat stat, and skill level-up rewards (stat boosts, coins, items, tags) run through the same script event system as everything else.",
        ],
      },
      {
        heading: "Checking progress",
        body: ["/skills opens the in-game menu; admins and console can also query a level directly."],
        code: { lang: "text", content: "/skills\n/skill list\n/skill get Steve mining" },
      },
    ],
  },
  {
    slug: "zones",
    title: "Zones",
    category: "World & Content",
    summary: "Named world regions that gate spawns, abilities, and scripted behaviour to a place, not just a condition.",
    sections: [
      {
        heading: "What it is",
        body: [
          "A zone is an axis-aligned box (plus optional extra boxes for non-rectangular shapes) in one world, carrying an ID other modules can reference. Flags control PvP, block breaking/placing, natural spawning, hunger, entry, teleportation, and leaf decay inside the box.",
          "Entering a zone shows its display name in the action bar and updates the scoreboard's Zone: line; outside every zone the scoreboard reads Wilderness.",
        ],
      },
      {
        heading: "What a zone can carry",
        body: [
          "Beyond flags, a zone can host resource nodes (ore blocks that regenerate with custom drops and a required tool power), mob spawners (periodic custom-mob spawns with an alive cap), a fishing loot table, and enter/exit scripted actions.",
        ],
      },
      {
        heading: "Resource nodes in practice",
        body: [
          "Mining inside a zone is different from vanilla: breaking is gated by required-power (too weak a tool cancels the break with a message), drops go straight into the miner's inventory, and a node can have multiple stages — ore → cobblestone → bedrock — before it regenerates after regen-delay ticks. Drop quantities scale with the miner's Mining Fortune stat.",
        ],
      },
      {
        heading: "The /zone command",
        body: ["Admin-only (valmora.admin). A selection wand marks the two corners before creating a zone."],
        code: {
          lang: "text",
          content: "/zone wand\n/zone pos1\n/zone pos2\n/zone create shardworks_mine \"Shardworks Mine\"\n/zone flag shardworks_mine pvp false\n/zone spawner add shardworks_mine iron_golem 10 3 600 20",
        },
      },
      {
        heading: "A flagged, spawner-backed zone",
        body: [
          "min and max are each a 3-number list ([x, y, z]), not a map — that's what /zone create writes from your wand selection, and it's also fine to hand-author. Flags live under allow:, not flags:.",
        ],
        code: {
          lang: "yaml",
          content: `mine:
  world: world
  min: [100, 40, 200]
  max: [160, 70, 260]
  display-name: "<gold>Shardworks Mine"
  allow:
    pvp: false
    hunger: false
    natural-mob-spawning: false
  mob-spawners:
    zombie_spot:
      mob: forest_goblin
      x: 130
      y: 65
      z: 230
      spawn-interval: 200
      max-alive: 5
      radius: 20.0
      spawn-radius: 3
  resource-blocks:
    DEEPSLATE_IRON_ORE:
      regen-delay: 600
      required-power: 7
      drops:
        - item: raw_ferrite
          min: 1
          max: 3
          chance: 0.6`,
        },
      },
      {
        heading: "Full YAML schema",
        body: ["allow: flags default to the safe/permissive side except pvp, breaking, and placing, which default closed."],
        table: {
          headers: ["Field", "Default", "Notes"],
          rows: [
            ["world", "world", "Which world this zone's bounds apply to."],
            ["min / max", "required", "3-number lists — [x, y, z] corners of the box."],
            ["extra-boxes[].min / max", "none", "Additional boxes unioned into the same zone, for non-rectangular shapes."],
            ["display-name", "<green><id>", "Shown in the action bar on entry and the scoreboard's Zone: line."],
            ["allow.pvp", "false", "Whether players can damage each other inside the zone."],
            ["allow.natural-mob-spawning", "false", "Vanilla natural spawns inside the zone."],
            ["allow.block-breaking / block-placing", "false", "Plain block break/place, independent of resource-blocks below."],
            ["allow.hunger / entry / teleportation / leaf-decay", "true", "Each defaults open; set false to restrict."],
            ["mob-spawners.<id>.mob / x / y / z / spawn-interval / max-alive / radius / spawn-radius", "—", "A named spawn point: which Valmora mob, where, how often, and its alive cap."],
            ["resource-blocks.<MATERIAL>.regen-delay / required-power / drops / stages", "regen-delay 600", "Turns a vanilla ore block into a regenerating, tool-gated resource node — see Resource nodes in practice above."],
            ["fishing-loot-table", "none", "Links this zone to a custom fishing loot table."],
          ],
        },
      },
    ],
  },
  {
    slug: "npc",
    title: "NPCs",
    category: "World & Content",
    summary: "World-entity NPCs with click actions, holograms, and an optional bound conversation — all one YAML file.",
    sections: [
      {
        heading: "NPCs are world entities",
        body: [
          "An NPC definition sets an entity type, a spawn position, a display name, click actions, and an optional bound conversation. NPCs auto-spawn at their configured coordinates on plugin load and respawn on reload — a background task checks every 60 seconds for any NPC whose entity vanished (a chunk unload, for instance) and brings it back.",
          "Right-click starts a bound conversation, or runs the NPC's on-right-click script actions if none is bound; left-click runs on-left-click. Both always fire the quest module's talk_to_npc objective check, so any NPC can double as a quest trigger without extra wiring.",
        ],
      },
      {
        heading: "An NPC definition",
        body: [
          "Position fields are flat (world, x, y, z, yaw), not a nested location: block, and the entity type field is entity-type — this is straight from the loader.",
        ],
        code: {
          lang: "yaml",
          content: `elder_thorin:
  display-name: "<gold>Elder Thorin"
  entity-type: VILLAGER
  world: world
  x: 120
  y: 65
  z: 40
  yaw: 90
  look-at-player: true
  show-name: true
  conversation: thorin_main
  on-right-click:
    - "notify <gray>Thorin nods at you."
  holograms:
    - name: title
      text: "<gold>Elder Thorin"
      vector: { x: 0, y: 2.4, z: 0 }
      check_interval: 60`,
        },
      },
      {
        heading: "Full YAML schema",
        table: {
          headers: ["Field", "Default", "Notes"],
          rows: [
            ["display-name", "<white><id>", "Name tag shown above the NPC."],
            ["entity-type", "VILLAGER", "Any vanilla EntityType."],
            ["world / x / y / z / yaw", "world, 0, 64, 0, 0", "Flat spawn coordinates — not nested under a location: block."],
            ["conversation", "none", "A dialogue id defined in a quest package's conversations: section — see the Quests page."],
            ["on-right-click / on-left-click", "none", "Plain script event lists, run only when no conversation is bound (or always, for left-click)."],
            ["skin-texture / skin-signature", "none", "Mojang skin texture/signature pair for MANNEQUIN-style NPCs."],
            ["look-at-player", "false", "Whether the NPC's head tracks nearby players."],
            ["show-name", "true", "Whether the nameplate renders at all."],
            ["holograms[].name / text / vector.x,y,z / conditions / check_interval", "check_interval 60", "One or more floating TextDisplay lines offset from the NPC, re-evaluated on an interval so conditions can hide/show them live."],
          ],
        },
      },
      {
        heading: "Conversations",
        body: [
          "Dialogue renders directly in chat, one line per message, with numbered clickable choices. Players can click a choice, type its number, or use jump/sneak/movement keys to navigate and confirm — and while a conversation is open, other players' chat is hidden so nothing scrolls the dialogue away.",
          "Conversations themselves aren't defined here — they live inside a quest package's conversations: section, since a dialogue tree and the quest it assigns are almost always authored together. See the Quests page for the full conversation schema.",
        ],
        code: {
          lang: "text",
          content: `<gold><bold>Elder Thorin ▶ <yellow>The mine has gone silent. Will you help us?
  ► [1] I will investigate the mine
  ► [2] I am not ready for that yet.`,
        },
      },
    ],
  },
  {
    slug: "quests",
    title: "Quests",
    category: "World & Content",
    summary:
      "Two loading systems, 36 objective types, named events/conditions, templates, and cross-package references — the full quest YAML surface.",
    sections: [
      {
        heading: "Two loading systems",
        body: [
          "Valmora actually has two different ways to author a quest, and both are real, shipped, and active at once — this page covers both rather than picking one.",
          "Flat quest definitions (quests/*.yml, e.g. shardworks_quests.yml): one file, one or more top-level quest ids, no shared events/conditions, no dialogue. Good for quest-board pool quests that don't need a conversation.",
          "Quest packages (quests/<package>/quest.yml plus sibling files in the same folder): a richer, two-pass loader with named reusable events and conditions, a compact one-line objective DSL, conversation trees, notifications, and cross-package references. This is what powers Forgotten Mine and Blacksmith Hub.",
        ],
      },
      {
        heading: "Flat quest definitions",
        body: [
          "Each top-level key is a quest id. objectives is a list of maps; reward-events is NOT fired automatically by anything in the quest engine itself — it's there for something else (a quest board's Collect button, typically) to fire explicitly via the quest_board_collect event. Actual rewards on completion belong on the specific objective that finishes the quest, in that objective's own events: list.",
        ],
        code: {
          lang: "yaml",
          content: `shardworks_mine_ferrite:
  name: "<gray>Ferrite Quota"
  objectives:
    - id: mine_ferrite
      type: collect
      target: raw_ferrite
      amount: 150
      notify: 25
  reward-events:
    - "point ferrite_powder add 40"
    - "point geomancy_tokens add 1"`,
        },
      },
      {
        heading: "Flat format schema",
        table: {
          headers: ["Field", "Default", "Notes"],
          rows: [
            ["name", "the quest's id", "Display name."],
            ["objectives[].id", "none", "Optional; needed for delay objectives and for referencing this objective's progress elsewhere."],
            ["objectives[].type", "kill", "Lowercase objective type id — see the full type table below."],
            ["objectives[].target", "\"\"", "What the type matches against — a mob id, item id, zone id, block material, etc., or any."],
            ["objectives[].amount", "1", "How many; also doubles as a threshold/range/level depending on the type."],
            ["objectives[].conditions", "none", "Inline condition strings, same ConditionParser as everywhere else."],
            ["objectives[].events", "none", "Script event list fired the instant this objective reaches amount."],
            ["objectives[].persistent", "false", "Resets this objective's progress back to 0 after completing instead of leaving it done — for repeating sub-goals inside one quest."],
            ["objectives[].auto-once", "false", "Starts automatically for every player, once ever, without an explicit quest_start."],
            ["objectives[].notify", "0", "Sends a progress message every N completions toward amount; omit for none."],
            ["reward-events", "none", "NOT auto-fired by quest completion — only fired by an explicit trigger elsewhere, e.g. a quest board's Collect click."],
            ["cooldown-seconds", "0 (not repeatable)", "Seconds after completion before the quest can be started again; 0 means once ever."],
          ],
        },
      },
      {
        heading: "Quest packages: the package manifest",
        body: [
          "A folder is a package if it contains a quest.yml. Every other .yml file in that folder (and its non-package subfolders) is loaded alongside it and merged into the same package — that's why forgotten_mine's quest.yml, quests.yml, and conversations.yml all contribute to one package.",
        ],
        code: {
          lang: "yaml",
          content: `# quests/forgotten_mine/quest.yml
package:
  enabled: true
  templates: []       # optional — names of packages under templates/ to inherit from

npc_conversations:
  thorin: thorin_main   # binds the NPC id "thorin" to the "thorin_main" conversation`,
        },
      },
      {
        heading: "Named events and conditions",
        body: [
          "Any sibling file in the package can declare events: and conditions: blocks — named, reusable action lists and condition strings that objectives and conversations reference by name instead of repeating inline. A named event can also be a single folder <name> line that concatenates other named events together.",
        ],
        code: {
          lang: "yaml",
          content: `events:
  reward_coins_small:
    - "point currency add 100"
    - "sound player entity.player.levelup"
  reward_coins_large:
    - "point currency add 500"
    - "notify <gold>You earned 500 coins! category:quest_complete"
  bonus_mining_reward: "folder reward_coins_small"   # expands to reward_coins_small's list

conditions:
  in_mine:   "zone coal_mine"
  is_day:    "$time.is_day$ == true"
  mine_done: "tag forgotten_mine.done"`,
        },
      },
      {
        heading: "The compact objective DSL",
        body: [
          "Inside a package, a top-level objectives: block can define named, reusable objectives as one-line strings instead of full YAML maps: <type> <target> <amount> [conditions:a,b] [events:x,y] [persistent] [auto-once] [notify[:n]]. delay is a special case with its own syntax.",
        ],
        code: {
          lang: "text",
          content: "kill cave_spider 5 conditions:in_mine\nreach_zone coal_mine 1 events:reward_coins_small,forgotten_mine.done notify\ndelay 30 interval:20 events:reward_coins_small   # 30 seconds, ticking every 20 ticks",
        },
      },
      {
        heading: "Quests inside a package",
        body: [
          "Under quests:, each objective can be either that same compact DSL string or a full structured YAML block — mix and match freely inside one quest. Named event/condition references (in events:/conditions:) are resolved automatically; unlike the flat format, a package quest's reward-events aren't parsed at all — put completion rewards on the finishing objective's own events: instead.",
        ],
        code: {
          lang: "yaml",
          content: `quests:
  forgotten_mine:
    name: "<yellow>A Miner's Burden"
    objectives:
      meet_thorin:
        type: talk_to_npc
        target: thorin
        amount: 1
      mine_coal:
        type: block_break
        target: COAL_ORE
        amount: 20
        notify: 5
      kill_spiders: "kill cave_spider 5 conditions:in_mine"
      reach_mine:
        type: reach_zone
        target: coal_mine
        amount: 1
        events:
          - "notify <green>Quest complete! category:quest_complete"
          - "point currency add 250"
          - "tag add forgotten_mine.done"
          - "give COAL:32"`,
        },
      },
      {
        heading: "Templates and cross-package references",
        body: [
          "A package can list templates: [name] in its package: block to inherit named events, conditions, objectives, quests, conversations, and notifications from a package under plugins/Valmora/templates/ — anything the package doesn't already define itself. And any named event reference can reach into another package entirely with pkgPath>eventName — useful for a hub package's NPCs granting rewards defined once in a shared rewards package.",
        ],
      },
      {
        heading: "Conversations",
        body: [
          "A conversation lives in the same package as its quest. quester names the speaker, first lists candidate opening nodes (the first whose conditions pass is used — this is how the same NPC greets a player differently before/during/after a quest), NPC_options are the NPC's lines with pointers to player choices, and player_options are those choices, each able to fire events and point onward.",
        ],
        code: {
          lang: "yaml",
          content: `conversations:
  thorin_main:
    quester: "<gold><bold>Elder Thorin"
    stop: true
    first: [greeting_new, greeting_progress, greeting_done]
    final_events:
      - "sound player entity.villager.no"
    NPC_options:
      greeting_new:
        text: "<yellow>Will you help us?"
        conditions: "mine_not_started, mine_not_completed"
        pointers: [accept_quest, decline_quest]
    player_options:
      accept_quest:
        text: "I will investigate the mine."
        events: "start_forgotten_mine"
        pointers: [greeting_progress]`,
        },
      },
      {
        heading: "Conversation schema",
        table: {
          headers: ["Field", "Default", "Notes"],
          rows: [
            ["quester", "the conversation's id", "Speaker name shown above NPC lines."],
            ["stop", "false", "If true, walking more than one block away teleports the player back until they finish or sneak out."],
            ["first", "\"start\"", "List of candidate opening node ids; the first whose conditions pass wins."],
            ["final_events", "none", "Events fired when the conversation ends naturally."],
            ["NPC_options.<node>.text / conditions / pointers", "—", "One NPC line; conditions gate whether this node can be the current one, pointers list the player choices offered."],
            ["player_options.<node>.text / events / pointers", "—", "One player choice; events fire when chosen, pointers name where it leads next."],
          ],
        },
      },
      {
        heading: "The 36 objective types",
        body: [
          "Every type is triggered from a real Bukkit listener, matched by exact target string, category, or \"any\". A few have non-obvious target/amount conventions, called out below the table.",
        ],
        table: {
          headers: ["Type", "target means", "amount means"],
          rows: [
            ["kill", "mob id, EntityType name, or mob category (e.g. UNDEAD)", "kill count"],
            ["collect", "item id or Material; picked up, not crafted", "item count"],
            ["reach_zone", "a zone id", "1"],
            ["talk_to_npc", "an NPC id", "1"],
            ["craft", "item id or Material of the crafted result", "craft count"],
            ["die", "\"die\" (fixed)", "death count"],
            ["location", "\"x;y;z;world\"; amount doubles as the match radius in blocks", "radius"],
            ["block_break / block_place", "block Material", "count"],
            ["jump", "\"jump\" (fixed)", "jump count"],
            ["breed / tame", "EntityType", "count"],
            ["enchant", "enchantment key, or \"any\"", "times applied"],
            ["smelt", "item id or Material of the smelted result", "count (attributed to whoever placed the furnace)"],
            ["brew", "potion effect/result", "count"],
            ["fish", "caught item Material, or \"any\"", "catch count"],
            ["shear", "sheared EntityType", "count"],
            ["variable", "a player.var.* name", "the numeric threshold — checked via checkVariableObjective whenever that variable changes"],
            ["drink_potion", "potion Material (legacy alias fired alongside consume for any potion)", "count"],
            ["login / logout", "fixed string", "1 (fires once per join/quit)"],
            ["level_skill", "a skill id", "1 (fires on that skill's level-up)"],
            ["stat_reach", "a stat id", "the stat value to reach"],
            ["exp_gain", "a skill id", "cumulative skill XP"],
            ["delay", "matched by objective id, not target", "computed from delay/interval — see the compact DSL above"],
            ["ride", "mount EntityType, or \"any\"", "1"],
            ["consume", "item id or Material consumed", "count"],
            ["step", "\"x;y;z;world\" of a pressure plate", "count"],
            ["action", "\"<left|right|any>:<BLOCK_MATERIAL|any>\"", "count"],
            ["arrow", "\"x;y;z;world\" impact point; amount is the match radius", "radius"],
            ["command", "a command string, lowercased, no leading slash", "count"],
            ["equip", "\"<HEAD|CHEST|LEGS|FEET|any>:<item id|any>\"", "count"],
            ["experience", "unused", "vanilla level to reach"],
            ["interact", "\"<right|any>:<entity id or EntityType|any>\"", "count"],
            ["npcrange", "\"<enter|leave|inside|outside>:<npc id>\"; amount is the range in blocks", "radius"],
            ["tag", "a profile tag name added via the tag event", "1"],
            ["timer", "any string (conventionally the objective id); polled once per second per player", "seconds to wait"],
            ["point", "a Quest Points category", "the points threshold to reach"],
          ],
        },
      },
      {
        heading: "Reward delivery: the one thing to get right",
        body: [
          "Completing a quest's last objective only sets its status to completed and sends a chat message — verified directly against QuestManager, not assumed. It does not fire any rewards on its own. Every reward a player actually receives comes from one of two places: an objective's own events: list (fired the moment that objective individually reaches its amount — this is where 90% of quest rewards belong), or a quest's top-level reward-events (only in the flat format, and only fired by something else calling it explicitly, like a quest board's Collect button via quest_board_collect).",
          "Put your rewards on the objective that finishes the quest, not on a top-level reward-events block, unless you're specifically building a quest-board-style \"claim later\" flow.",
        ],
      },
      {
        heading: "Quest boards",
        body: [
          "A board is a fixed number of always-filled slots drawn randomly from a pool of quest ids — collecting a completed slot's reward rerolls a new quest into it from the same pool.",
        ],
        code: {
          lang: "yaml",
          content: `shardworks:
  slots: 2
  pool:
    - shardworks_mine_ferrite
    - shardworks_mine_lumicite
    - shardworks_guardian_hunt`,
        },
      },
      {
        heading: "Quest-related script events",
        body: ["Every scripted quest action — from a conversation choice to a board slot — runs through these."],
        table: {
          headers: ["Event", "Syntax", "What it does"],
          rows: [
            ["quest_start", "quest_start <questId>", "Starts a quest for the caster."],
            ["quest_complete", "quest_complete <questId>", "Force-completes a quest immediately."],
            ["quest_cancel", "quest_cancel <questId>", "Cancels a quest, resetting its status to not-started."],
            ["quest_fail", "quest_fail <questId>", "Marks a quest failed."],
            ["objective_start", "objective_start <objectiveId>", "Manually starts a single objective (e.g. to kick off a delay/timer type)."],
            ["objective_delete", "objective_delete <objectiveId>", "Clears a single objective's tracked state."],
            ["quest_board_assign", "quest_board_assign <boardId>", "Assigns a quest from the named board to the caster, if they don't already have one from it."],
            ["quest_board_collect", "quest_board_collect <boardId> <slot>", "Collects a completed board slot's rewards and rerolls it."],
            ["point", "point <category> <add|set|take> <amount>", "Adjusts a Quest Points category balance — this is what quest and board rewards actually pay out with."],
            ["journal", "journal [open]", "Opens the player's quest journal."],
          ],
        },
      },
      {
        heading: "Commands",
        body: ["/quest is the base command; players never need a quest-package-specific command."],
        code: { lang: "text", content: "/quest\n/quest journal\n/quest track <questId>\n/quest points\n/quest points top [page]" },
      },
    ],
  },
  {
    slug: "time-calendar",
    title: "Time & Calendar",
    category: "World & Content",
    summary: "An in-game calendar with seasons and a persisted day count that scripts and mobs can react to.",
    sections: [
      {
        heading: "A calendar on top of Minecraft's clock",
        body: [
          "Vanilla day/night still ticks normally — the time module just reads it and derives a richer date: a hour:minute clock, a day 1–30 within the current phase, a phase (Early → Mid → Late), a season (Spring → Summer → Autumn → Winter), and a year that counts up from 1. A full year is 360 days.",
          "The calendar's position survives restarts — it's persisted to plugins/Valmora/time.yml, so day 74 of Year 3 is still day 74 when the server comes back.",
        ],
      },
      {
        heading: "What players see",
        body: [
          "A sidebar scoreboard commonly shows the clock and season together, and a season change broadcasts a temporary action-bar message to everyone online."],
        code: {
          lang: "text",
          content: "⏰ 14:30  ☀ Early Summer\nDay 12  │  Year 3\n\n✦ A new season begins — Early Summer ✦",
        },
      },
      {
        heading: "Seasonal events",
        body: [
          "The calendar module runs scripted events keyed to a season/phase/day-in-phase window: actions fire when the window starts, once per day while it's active, and when it ends. No commands or permissions are needed — events fire automatically off the day cycle. This is the real, shipped calendar/seasonal.yml — trigger: is its own nested block, day-start/day-end (not a days: range string) mark the window, and the daily block is called recurring-daily:, not on-day:.",
        ],
        code: {
          lang: "yaml",
          content: `harvest_festival:
  trigger:
    season: AUTUMN
    phase: EARLY
    day-start: 1
    day-end: 30
  on-start:
    - "notifyall io:title Harvest Festival!"
    - "notifyall io:subtitle <gold>The crops are ready to harvest!"
    - "foreach @all stat_modify add farming_fortune 25"
  on-end:
    - "notifyall io:chat <gold>The Harvest Festival has ended."
    - "foreach @all stat_modify add farming_fortune -25"
  recurring-daily:
    - "notifyall io:actionbar <gold>✦ Harvest Festival active! Bonus Farming Fortune!"`,
        },
      },
      {
        heading: "Full YAML schema",
        body: [
          "There's no name or display-name field — the definition is purely mechanical. Any text a player sees has to come from inside the event strings themselves.",
        ],
        table: {
          headers: ["Field", "Default", "Notes"],
          rows: [
            ["trigger.season", "any season", "SPRING, SUMMER, AUTUMN, or WINTER. Omit to match every season."],
            ["trigger.phase", "any phase", "EARLY, MID, or LATE. Omit to match every phase."],
            ["trigger.day-start / day-end", "1 / 30", "Inclusive day-in-phase range; clamped to 1–30, and day-start must be ≤ day-end."],
            ["on-start", "none", "Event list fired the instant the calendar day rolls into this window."],
            ["on-end", "none", "Event list fired the instant the calendar day rolls out of this window."],
            ["recurring-daily", "none", "Event list fired once per day for every day the window is active, including the start day."],
          ],
        },
      },
      {
        heading: "Reading time in scripts",
        body: [
          "$time.season$, $time.hour$, and $time.is_day$ are available to every condition and formula in the DSL — a mob that only spawns at night, or a recipe that only works in Summer, is a one-line condition away.",
        ],
        code: { lang: "text", content: 'condition $time.season$ == "SUMMER"\ncondition $time.is_day$ == true' },
      },
    ],
  },
  {
    slug: "reforge-pets",
    title: "Reforging & Pets",
    category: "Progression",
    summary: "Reforge stones re-roll an item's bonus stat pool; pets grant passive bonuses that scale with their own level.",
    sections: [
      {
        heading: "Reforging",
        body: [
          "A reforge is a named modifier whose stat bonus scales with the target item's rarity — the same fierce reforge grants a Common sword +5 Strength but a Legendary sword +48. If a rarity tier has no entry, the next lower defined tier is used instead.",
          "Reforging fully recalculates an item's stats from base + the new reforge's bonus, so reforges never stack — applying a new one always replaces whatever was there before.",
        ],
      },
      {
        heading: "Deterministic vs. random",
        body: [
          "The Reforge Anvil applies exactly what's written on a Reforge Stone (both item and stone are consumed, result appears in the output slot). The Random Forge applies a random valid reforge — excluding the item's current one — directly in place. Both charge a coin cost based on the item's rarity.",
        ],
        code: { lang: "text", content: "/item give fierce_reforge_stone\n/item give titanic_reforge_stone" },
      },
      {
        heading: "A reforge definition",
        body: [
          "Eight combat reforges ship by default; new ones are plain YAML under plugins/Valmora/reforges/. The stat bucket per rarity is stat-bonuses-by-rarity, and the item-type gate is applicable-types — both straight from the loader, not applies-to/bonuses.",
        ],
        code: {
          lang: "yaml",
          content: `fierce:
  name: "Fierce"
  applicable-types: [SWORD, AXE]
  weight: 1.0
  generate-stone: true
  stat-bonuses-by-rarity:
    COMMON:   { strength: 5,  crit-damage: 3 }
    UNCOMMON: { strength: 12, crit-damage: 6 }
    EPIC:     { strength: 32, crit-damage: 15 }
    DIVINE:   { strength: 85, crit-damage: 40 }`,
        },
      },
      {
        heading: "Reforge schema",
        table: {
          headers: ["Field", "Default", "Notes"],
          rows: [
            ["name", "the reforge's id", "Display name shown on the stone and in lore."],
            ["applicable-types", "none", "List of ItemType values this reforge is valid for."],
            ["stat-bonuses-by-rarity.<RARITY>", "none", "A stat:amount map per rarity tier; a tier with no entry falls back to the next lower defined tier."],
            ["weight", "1.0", "Relative selection probability when the Random Forge picks among an item's valid reforges."],
            ["generate-stone", "false", "Whether /item give auto-generates a matching <id>_reforge_stone item for this reforge."],
          ],
        },
      },
      {
        heading: "Pets",
        body: [
          "Pets are items, not entities you're handed directly — a pet item carries hidden data for which pet it is, its level, and its XP, so progress lives on the item and survives restarts. Right-click to summon it beside you, right-click again to unsummon.",
          "While summoned a pet adds base-stats plus stats-per-level × level to the owner, recalculated automatically whenever stats refresh, and gains XP from kills and skill XP toward a level cap defined per pet (200 by default). Only one pet can be active at a time. Summoned pets are spawned with AI disabled and stepped toward their owner by a lightweight follow task — not vanilla pathfinding, so they cut in a straight line and teleport back if they fall too far behind, but they do keep up.",
        ],
        table: {
          headers: ["Pet", "Stat", "Lvl 1", "Lvl 100"],
          rows: [
            ["Baby Wolf", "Strength", "5.5", "55"],
            ["Baby Sheep", "Health", "15.8", "95"],
            ["Ender Dragon", "Strength", "52", "250"],
          ],
        },
      },
      {
        heading: "A pet definition",
        body: [
          "Ability triggers are just three: ON_KILL, ON_HIT, ON_DEFEND — each firing the same script event list every other ability system uses.",
        ],
        code: {
          lang: "yaml",
          content: `baby_wolf:
  name: "Baby Wolf"
  entity-type: WOLF
  base-stats:
    strength: 5.5
  stats-per-level:
    strength: 0.5
  xp-formula: "100 * $curve.level$ * $curve.level$"
  max-level: 100
  abilities:
    - trigger: ON_KILL
      events:
        - "notify <gray>Your pet yips happily!"
  milestones:
    25:
      - "give BONE:5 notify"
    50:
      - "give DIAMOND:1 notify"`,
        },
      },
      {
        heading: "Pet schema",
        table: {
          headers: ["Field", "Default", "Notes"],
          rows: [
            ["name", "the pet's id", "Display name."],
            ["entity-type", "WOLF", "Vanilla EntityType the pet is rendered as."],
            ["base-stats / stats-per-level", "none", "A stat:amount map each — total bonus at a level is base + per-level × level."],
            ["abilities[].trigger", "—", "ON_KILL, ON_HIT, or ON_DEFEND."],
            ["abilities[].events", "—", "Script event list fired on that trigger, same DSL as everywhere else."],
            ["milestones.<level>", "none", "A script event list fired once, the moment the pet reaches that level."],
            ["xp-formula", "pets/defaults.yml server default", "Formula evaluated once per level at load time; $curve.level$ is the level being computed."],
            ["max-level", "pets/defaults.yml server default (200)", "Caps how high this specific pet can level."],
          ],
        },
      },
    ],
  },
  {
    slug: "config-yml",
    title: "Server Configuration (config.yml)",
    category: "Core Systems",
    summary: "Every key in the shipped config.yml, block by block — what it defaults to and what it actually changes.",
    sections: [
      {
        heading: "The full file",
        body: [
          "This is the complete, unedited config.yml shipped inside the plugin jar. It's copied to plugins/Valmora/config.yml on first run and never overwritten after that — every key below is safe to change in place, then reload with /valmora reload.",
        ],
        code: {
          lang: "yaml",
          content: `database:
  type: sqlite
  # mysql:
  #   host: "127.0.0.1"
  #   port: 3306
  #   database: "valmora"
  #   username: "root"
  #   password: "password123"
  #   use-ssl: false

economy:
  autosave-interval-seconds: 60
  death-loss-percent: 50.0
  bank-interest-percent: 0.5
  bank-interest-interval-seconds: 60

profiles:
  max-profiles: 4
  default-name: Earth
  planet-names: [Mars, Venus, Jupiter, Saturn, Mercury, Neptune, Uranus, Pluto, "Kepler-22b", "Proxima b", Titan, Europa]

time:
  world: world
  start-year: 1
  start-season: SPRING
  start-phase: EARLY
  start-day: 1
  season-names: [Spring, Summer, Autumn, Winter]
  phase-names: [Early, Mid, Late]
  scoreboard-enabled: true

progression:
  refund-percent: 100.0

combat:
  health-stat: health
  mana-stat: mana
  damage-stat: damage
  strength-stat: strength
  defense-stat: defense
  crit-chance-stat: crit_chance
  crit-damage-stat: crit_damage
  speed-stat: speed
  health-regen-stat: health_regen
  mana-regen-stat: mana_regen
  luck-stat: luck
  environment-damage-multiplier: 5.0
  damage-indicator-rate-limit-ms: 400
  damage-indicator-lifetime-ticks: 20
  post-hit-no-damage-ticks: 20
  combat-window-ms: 3000
  visual-health-hearts: 10

mining:
  mining-fortune-stat: mining_fortune
  mining-speed-stat: mining_speed
  breaking-power-stat: breaking_power
  mining-spread-stat: mining_spread

npc-skin-server:
  enabled: false
  port: 2525
  # host: ""

alchemy:
  splash-radius: 4.0
  tick-interval: 20
  max-active-effects: 10`,
        },
      },
      {
        heading: "database:",
        body: [
          "Picks the persistence engine. SQLite needs zero setup and stores everything in plugins/Valmora/database.db — the right default for almost every server. Switch to mysql for a network syncing player data across multiple backend servers, then uncomment and fill in the mysql: sub-block.",
        ],
        table: {
          headers: ["Key", "Default", "What it does"],
          rows: [
            ["type", "sqlite", "sqlite or mysql."],
            ["mysql.host / port / database / username / password", "commented out", "Connection details, only read when type: mysql."],
            ["mysql.use-ssl", "false", "Enable if your MySQL server requires/serves over SSL/TLS."],
          ],
        },
      },
      {
        heading: "economy:",
        table: {
          headers: ["Key", "Default", "What it does"],
          rows: [
            ["autosave-interval-seconds", "60", "How often dirty balances are batch-flushed to the database. Balances live in memory between flushes and are always safe to use; only an unclean crash inside this window can lose progress."],
            ["death-loss-percent", "50.0", "Percentage of a player's purse (not bank) removed on death. There is currently no way to disable this — set 0 to effectively turn it off."],
            ["bank-interest-percent", "0.5", "Flat-rate interest applied to every bank balance each interval. 0 disables interest entirely (no task is even scheduled)."],
            ["bank-interest-interval-seconds", "60", "How often bank interest is applied."],
          ],
        },
        body: [],
      },
      {
        heading: "profiles:",
        table: {
          headers: ["Key", "Default", "What it does"],
          rows: [
            ["max-profiles", "4", "Maximum number of character profiles a single player account can hold."],
            ["default-name", "Earth", "Name given to the first profile created for a new player."],
            ["planet-names", "Mars, Venus, Jupiter, Saturn, Mercury, Neptune, Uranus, Pluto, Kepler-22b, Proxima b, Titan, Europa", "Pool of names randomly assigned to a player's additional profiles — only unused names are picked."],
          ],
        },
        body: [],
      },
      {
        heading: "time:",
        table: {
          headers: ["Key", "Default", "What it does"],
          rows: [
            ["world", "world", "Which world's time (world.getTime()) drives the hour/minute clock."],
            ["start-year / start-season / start-phase / start-day", "1 / SPRING / EARLY / 1", "Calendar starting position — applied once on first launch, then persisted to plugins/Valmora/time.yml and ignored on every later start."],
            ["season-names / phase-names", "Spring/Summer/Autumn/Winter, Early/Mid/Late", "Display strings used in the scoreboard and $time.season$/$time.phase$."],
            ["scoreboard-enabled", "true", "Whether the sidebar scoreboard shows the two time lines at all."],
          ],
        },
        body: [],
      },
      {
        heading: "progression:",
        table: {
          headers: ["Key", "Default", "What it does"],
          rows: [["refund-percent", "100.0", "Percentage of every point ever spent on a progression tree that's refunded when a player runs /progression reset."]],
        },
        body: [],
      },
      {
        heading: "combat:",
        body: [
          "The first eleven keys are a stat-ID indirection layer, not raw numbers — they tell the combat engine which stats/*.yml entry plays the role of health, mana, damage, and so on. Rename or replace a core stat and update the pointer here instead of touching Java.",
        ],
        table: {
          headers: ["Key", "Default", "What it does"],
          rows: [
            ["health-stat … luck-stat (11 keys)", "health, mana, damage, strength, defense, crit_chance, crit_damage, speed, health_regen, mana_regen, luck", "Points each engine role at a stat ID defined in stats/core.yml."],
            ["environment-damage-multiplier", "5.0", "Multiplies raw vanilla environmental damage (fall/fire/lava/drowning/etc.) before defense mitigation, so it stays meaningful against the RPG stat curve."],
            ["damage-indicator-rate-limit-ms", "400", "Minimum time between floating damage-indicator spawns per victim — stops indicator spam from fast DoT ticks."],
            ["damage-indicator-lifetime-ticks", "20", "How long a floating damage indicator stays visible."],
            ["post-hit-no-damage-ticks", "20", "Vanilla invulnerability ticks applied after a hit, preventing overlapping DoT ticks from double-counting."],
            ["combat-window-ms", "3000", "How long a player is considered \"in combat\" after dealing or taking damage."],
            ["visual-health-hearts", "10", "How many vanilla hearts the health bar is visually scaled to, independent of the player's real max-health stat."],
          ],
        },
      },
      {
        heading: "mining:",
        body: ["The same stat-indirection pattern as combat:, scoped to mining."],
        table: {
          headers: ["Key", "Default"],
          rows: [
            ["mining-fortune-stat", "mining_fortune"],
            ["mining-speed-stat", "mining_speed"],
            ["breaking-power-stat", "breaking_power"],
            ["mining-spread-stat", "mining_spread"],
          ],
        },
      },
      {
        heading: "npc-skin-server: and alchemy:",
        table: {
          headers: ["Key", "Default", "What it does"],
          rows: [
            ["npc-skin-server.enabled", "false", "Turns on a tiny built-in HTTP server so /npc skin <id> file <filename.png> can apply skins from plugins/Valmora/skins/."],
            ["npc-skin-server.port", "2525", "Port that tiny server listens on."],
            ["npc-skin-server.host", "auto-detect", "Set to your public IP if clients connect from outside the local network."],
            ["alchemy.splash-radius", "4.0", "Block radius for a splash potion's area of effect."],
            ["alchemy.tick-interval", "20", "Ticks between active-effect expiry checks (20 = once a second)."],
            ["alchemy.max-active-effects", "10", "Max concurrent active potion effects tracked per player."],
          ],
        },
      },
    ],
  },
  {
    slug: "default-files",
    title: "Default Config Files",
    category: "Core Systems",
    summary: "Every YAML file shipped inside the plugin jar, grouped by folder — what each one defines.",
    sections: [
      {
        heading: "How defaults work",
        body: [
          "Every file below ships inside the plugin jar and is copied to its matching plugins/Valmora/ subfolder the first time the server starts. None of them are ever overwritten after that first copy — edit the on-disk copy freely and run /valmora reload (or restart) to apply changes. Deleting a shipped file just removes its content; the plugin doesn't re-create it.",
        ],
      },
      {
        heading: "Root-level files",
        table: {
          headers: ["File", "What's in it"],
          rows: [
            ["config.yml", "Main server settings — see the dedicated Server Configuration page."],
            ["plugin.yml", "The Bukkit/Paper plugin descriptor: name, version, main class, every /command and its permission, and soft/hard dependencies (PacketEvents)."],
            ["stats/core.yml", "The stat registry — every stat ID's display name, icon, color, default/max value, and whether it's a \"pool\" stat (health, mana) with a current/max split."],
            ["item_types.yml", "Extra ItemType values beyond the 21 built-ins (SWORD, BOW, HELMET, …) — add a new item type with zero recompile."],
            ["damage_types/core.yml", "The damage-type registry — per-type color, whether it ignores victim defense, and an on-hit list of script events fired every time that type lands."],
            ["entity_categories.yml", "Category-matching rules (e.g. monster → instanceof Monster) used anywhere a system needs \"any entity of category X\" instead of one exact id — quest KILL objectives, mostly."],
            ["mob_categories.yml", "Extra mob category values beyond the 10 built-ins (UNDEAD, BOSS, GOLEM, …) — referenced by category: in mobs/*.yml."],
            ["ui.yml", "The scoreboard, action bar, and tab-list layout — line-by-line MiniMessage templates with $variable$ tokens."],
            ["combat_pipeline.yml", "Optional hook points into the combat hit lifecycle (pre_damage, post_calculation, post_application). Empty by default — combat behaves identically to having no pipeline at all until a stage is added."],
            ["item_pipeline.yml", "Optional hook points around firing an item ability's mechanics (pre_ability, post_ability). Same zero-cost-when-empty design as combat_pipeline.yml."],
            ["mob_pipeline.yml", "Optional hook points around mob combat/death, same pipeline pattern."],
            ["fishing_pipeline.yml", "Optional hook points around a fishing catch (pre_catch, post_catch), same pipeline pattern."],
            ["resource_pipeline.yml", "Optional hook points around breaking a zone resource node (pre_break, post_break), same pipeline pattern."],
          ],
        },
      },
      {
        heading: "items/ — every shipped item file",
        table: {
          headers: ["File", "What's in it"],
          rows: [
            ["example.yml", "A single annotated example item, meant as a copy-paste starting point."],
            ["swords.yml / bows.yml / catacombs_swords.yml / slayer_swords.yml / wands.yml", "Weapons — vanilla-tier through legendary, with abilities."],
            ["new_items.yml", "A grab-bag of additional weapons and tools ported from the source SkyBlock data dump."],
            ["armor_sets.yml / individual_pieces.yml / shardworks_armor.yml", "Armor pieces, including set-linked pieces that grant a bonus via set_bonuses/."],
            ["accessories.yml / backpacks.yml", "Accessory-slot items and backpack items (see the item-type table on the Items & Abilities page)."],
            ["alchemy_ingredients.yml", "Brewing ingredients consumed by the alchemy table's recipes."],
            ["fishing_bait.yml", "Bait items used with the fishing bait bag GUI."],
            ["shardworks_ores.yml / shardworks_pickaxes.yml", "The Shardworks mining line — raw ores mined from zone resource nodes, and the pickaxes with the Breaking Power needed to mine them."],
          ],
        },
      },
      {
        heading: "mobs/ and set_bonuses/",
        table: {
          headers: ["File", "What's in it"],
          rows: [
            ["mobs/test_mobs.yml / mobs/test_boss.yml", "Minimal example mobs and a boss, used for testing the mob engine end-to-end."],
            ["mobs/shardworks_mobs.yml", "Mobs tied to the Shardworks zone and its mining loop."],
            ["mobs/fishing_mobs.yml", "Sea-creature-style mobs spawned from fishing."],
            ["mobs/slayer_bosses.yml", "Boss mobs used as slayer-quest targets."],
            ["set_bonuses/sets.yml / armor_sets.yml / shardworks_sets.yml", "Named armor sets — the bonus a player gets for wearing every piece linked via an item's set: field."],
          ],
        },
      },
      {
        heading: "guis/ — every shipped menu",
        body: ["One machine or menu per file (a few files define more than one related GUI, like bank.yml's deposit/withdrawal dialogs)."],
        table: {
          headers: ["File", "GUI"],
          rows: [
            ["crafting.yml", "The 3×3 crafting table"],
            ["forge.yml", "The forge (base + material)"],
            ["anvil.yml", "The anvil (dynamic enchant merging)"],
            ["alchemy.yml", "The alchemy table (timed brews)"],
            ["reforge.yml / reforge_anvil.yml", "Random Forge and the deterministic Reforge Anvil"],
            ["enchanting.yml", "The two-page enchantment catalog + level list"],
            ["bank.yml", "The bank, plus its deposit/withdrawal dialogs"],
            ["stats.yml", "The player stats page"],
            ["skills_list.yml / skills_details.yml", "The /skills overview and per-skill detail page"],
            ["collections_categories.yml / collections_list.yml / collections_detail.yml", "The /collections browser"],
            ["geomancy_tree.yml", "The /geomancy progression tree"],
            ["active_effects.yml", "The /effects active-potion-effects page"],
            ["accessory_bag.yml / backpack_tier1–5.yml / bait_bag.yml / quiver.yml", "Storage containers — accessories, tiered backpacks, bait, arrows"],
            ["general_store.yml", "A generic buy/sell shop menu"],
            ["fast_travel.yml", "A warp-picker menu"],
            ["slayers.yml", "The slayer tier-selection menu"],
            ["shardworks_quest_board.yml", "The Shardworks quest board"],
          ],
        },
      },
      {
        heading: "recipes/, reforges/, and enchant(s)/",
        table: {
          headers: ["File", "What's in it"],
          rows: [
            ["recipes/crafting_table.yml", "Shaped/shapeless crafting-table recipes."],
            ["recipes/forge.yml", "Exact-slot forge recipes (base + material)."],
            ["recipes/anvil_templates.yml", "Reference templates for anvil-style merging (the anvil itself is a dynamic handler, not YAML-matched)."],
            ["recipes/shardworks_recipes.yml", "Recipes specific to the Shardworks content pack."],
            ["reforges/combat.yml", "The eight shipped combat reforges (Fierce, Sharp, Fabled, Heroic, Rapid, Fortified, Reinforced, Titanic) and their per-rarity bonuses."],
            ["enchant/forge_costs.yml", "Coin cost to reforge an item, keyed by rarity tier."],
            ["enchants/example_enchantments.yml", "Custom enchantment definitions — name, targets, conflicts, and max level, each backed by a logic: key."],
          ],
        },
      },
      {
        heading: "zones/, npcs/, warps/, and pets/",
        table: {
          headers: ["File", "What's in it"],
          rows: [
            ["zones/test_zones.yml", "Minimal example zones for testing flags and spawners."],
            ["zones/shardworks.yml", "The Shardworks mine zone — resource nodes, spawners, flags."],
            ["npcs/shopkeeper.yml", "A minimal shop NPC."],
            ["npcs/shardworks_npcs.yml", "NPCs tied to the Shardworks quest line, including bound conversations."],
            ["warps/hub.yml", "The default hub warp point(s)."],
            ["pets/defaults.yml", "Server-wide pet XP defaults — the xp-formula and max-level every pet inherits unless it overrides them."],
            ["pets/baby_wolf.yml", "The shipped Baby Wolf pet definition."],
          ],
        },
      },
      {
        heading: "quests/, quest_boards/, collections/, and progression/",
        table: {
          headers: ["File", "What's in it"],
          rows: [
            ["quests/shardworks_quests.yml", "Standalone quests for the Shardworks content pack."],
            ["quests/blacksmith_hub/quest.yml, quests.yml, blacksmith.yml, events.yml", "The blacksmith hub quest chain, its dialogue-adjacent data, and calendar-style event hooks."],
            ["quests/forgotten_mine/quest.yml, quests.yml, conversations.yml, notifications.yml", "The Forgotten Mine quest line, including its NPC conversation trees."],
            ["quests/slayers/quest.yml, quests.yml, notifications.yml", "The slayer quest packages — the current home of the old slayer system (see the Slayer design doc)."],
            ["quest_boards/shardworks.yml", "The pool of quests the Shardworks quest board can hand out."],
            ["collections/categories.yml", "The top-level collection categories shown in /collections."],
            ["collections/mining, farming, foraging, fishing, combat (subfolders)", "One file per collection group (e.g. mining/coal.yml, farming/wheat.yml) — kill/gather thresholds and their milestone rewards."],
            ["progression/geomancy.yml", "The Geomancy mining progression tree — tiers, node costs, and the level-currency/tier-currency items that pay for them."],
          ],
        },
      },
      {
        heading: "calendar/, alchemy/, skills/, and hud-items/",
        table: {
          headers: ["File", "What's in it"],
          rows: [
            ["calendar/seasonal.yml", "The three shipped seasonal events — Harvest Festival, Winter Blessing, Spring Renewal."],
            ["alchemy/effects.yml", "Potion-effect definitions the alchemy table can brew."],
            ["alchemy/modifiers.yml", "Ingredient modifiers that adjust a brew's strength/duration."],
            ["alchemy/healing_boost.yml", "A worked example potion recipe/effect."],
            ["skills/mining.yml, farming.yml, foraging.yml, fishing.yml, combat.yml, alchemy.yml, carpentry.yml, enchanting.yml, taming.yml", "One file per skill — its display metadata and per-level reward hooks."],
            ["hud-items/default.yml", "The default hotbar HUD item(s) — e.g. the menu_button item pinned to slot 8 with its own click actions."],
            ["fishing/hub_fishing.yml", "A worked example fishing loot table."],
          ],
        },
      },
    ],
  },
  {
    slug: "variables",
    title: "Script Variables Reference",
    category: "Core Systems",
    summary: "Every $namespace.path$ token the script engine resolves, exhaustively — one section per namespace.",
    sections: [
      {
        heading: "player.*",
        body: ["Resolves against the caster's active profile. Returns null before a profile is loaded (e.g. for a non-player caster)."],
        table: {
          headers: ["Variable", "Returns"],
          rows: [
            ["$player.name$", "Player name"],
            ["$player.world$", "World name the player is in"],
            ["$player.ping$", "Client ping"],
            ["$player.biome$", "Biome key at the player's location (e.g. plains)"],
            ["$player.profile$", "Active profile's display name"],
            ["$player.hp$ / $player.max_hp$", "Current / max health"],
            ["$player.health_percent$ / $player.missing_hp_percent$", "Health as a 0–100 percentage, or its inverse"],
            ["$player.mana$ / $player.max_mana$", "Current / max mana"],
            ["$player.last_damage$", "Most recent damage this player dealt, for ON_HIT abilities that scale off the triggering hit"],
            ["$player.weapon_damage$", "The player's current damage stat value"],
            ["$player.stat.<id>$", "Any stat's current value (e.g. $player.stat.strength$); $player.stat.list$ returns every stat as a JSON array"],
            ["$player.skill.<id>$", "A skill's current level; $player.skill.<id>.xp$, .next_level, .progress, .xp_in_level, .xp_required also work; $player.skill.list$ returns every skill as JSON"],
            ["$player.var.<name>$", "A custom profile variable set via the variable event"],
          ],
        },
      },
      {
        heading: "target.*",
        body: ["The current ability/combat target. Resolves to null if the context has no target."],
        table: {
          headers: ["Variable", "Returns"],
          rows: [
            ["$target.type$", "Bukkit entity type name (e.g. ZOMBIE)"],
            ["$target.health$ / $target.max_health$", "Current / max health"],
            ["$target.level$", "Custom mob level if tracked, otherwise 1"],
            ["$target.name$", "Entity name"],
          ],
        },
      },
      {
        heading: "time.*",
        body: ["Backed by the Time module's live snapshot — see the Time & Calendar page for the underlying calendar model."],
        table: {
          headers: ["Variable", "Returns"],
          rows: [
            ["$time.hour$ / $time.minute$", "Hour (0–23) / minute"],
            ["$time.day$", "Day within the current phase (1–30)"],
            ["$time.phase$ / $time.season$ / $time.year$", "Current phase / season / RPG year"],
            ["$time.total_days$ / $time.total_minutes$", "Absolute day count / minute count since world start"],
            ["$time.is_day$", "true/false"],
            ["$time.time_of_day$ / $time.emote$ / $time.color$", "Combined emote+label, just the emote, or the MiniMessage color for the current time of day"],
            ["$time.formatted_time$", "Pre-formatted clock string"],
          ],
        },
      },
      {
        heading: "world.*, server.*, and system.*",
        table: {
          headers: ["Variable", "Returns"],
          rows: [
            ["$world.name$", "Current world's name"],
            ["$world.dimension$", "OVERWORLD / NETHER / THE_END"],
            ["$server.online$", "Online player count"],
            ["$server.max_players$", "Configured max players"],
            ["$server.motd$", "Server MOTD"],
            ["$system.time$", "System.currentTimeMillis()"],
          ],
        },
      },
      {
        heading: "prop.* and param.*",
        body: [
          "prop.* reads a GUI session's property bag (the $prop$/variable set prop.key values used in click actions) — resolves to null outside a GUI context. param.* reads the parameters passed into the current mechanic or ability invocation. Both support deep dotted paths into a nested map or ConfigurationSection, e.g. $param.target.radius$.",
        ],
      },
      {
        heading: "range.* and math.*",
        table: {
          headers: ["Variable", "Returns"],
          rows: [
            ["$range.<start>.<end>$", "A list of integers from start to end inclusive (reversible: range.60.1 counts down). end can itself be a variable path, e.g. $range.1.player.skill.mining.max_level$."],
            ["$math.random$", "A fresh [0, 1) double every time it's resolved — for RNG-driven conditions like condition $math.random$ < 0.3."],
          ],
        },
      },
      {
        heading: "dmg.*, mob.*, resource.*, fishing.*, and item.*",
        body: [
          "These five namespaces are all the same pattern: a system attaches a handful of key-value pairs to the current ExecutionContext right before running a pipeline stage or formula, and the matching provider just reads them back. They only resolve to something meaningful inside the exact insertion point or formula file that sets them — and critically, the same namespace can carry different keys at different insertion points, since it's just \"whatever the last caller attached.\" See the Pipelines page for exactly which point sets which key.",
        ],
        table: {
          headers: ["Namespace", "Set by", "Known keys"],
          rows: [
            ["dmg.*", "DamageCalculator's own formula evaluation (damage_formula.yml only — not visible to pipeline stages)", "base_damage, strength, crit_damage, defense"],
            ["dmg.*", "CombatListener, from combat:post_calculation onward (post_calculation, post_application, on_dmg_dealt)", "final_damage, is_critical, damage_type, is_immune"],
            ["mob.*", "MobDeathListener, before the combat:on_death pipeline point only", "id, level"],
            ["mob.*", "BossController, before mob:pre_ability / mob:post_ability only", "ability_id, ability_trigger"],
            ["resource.*", "ResourceManager, before the resource:pre_break / post_break pipeline points", "material, stage"],
            ["fishing.*", "FishingManager, before the fishing:pre_catch / post_catch pipeline points", "table, sea_creature (set before the roll); item, amount (set once the roll resolves to a normal item reward)"],
            ["item.*", "AbilityExecutor, before the item:pre_ability / post_ability pipeline points", "ability_id, ability_trigger"],
          ],
        },
      },
      {
        heading: "curve.*",
        body: [
          "Exposes $curve.level$ inside an XP-curve formula (skills/*.yml xp-formula, pets/defaults.yml xp-formula) — the level (1..max) currently being evaluated, resolved once per level at load time rather than live.",
        ],
        code: { lang: "text", content: '"100 * $curve.level$ * $curve.level$"   # pets/defaults.yml xp-formula' },
      },
    ],
  },
  {
    slug: "events",
    title: "Script Events Reference",
    category: "Core Systems",
    summary: "Every registered event name in the script DSL, exhaustively — syntax, module of origin, and what it does.",
    sections: [
      {
        heading: "Core (registered by the script module itself)",
        table: {
          headers: ["Event", "Syntax", "What it does"],
          rows: [
            ["give", "give <Material:Amount> [notify]", "Adds a vanilla item stack to the caster's inventory."],
            ["variable", "variable <add|set|remove> <path> <value>", "Reads/writes player.var.<name>, player.stat.<id>, or (inside a GUI) prop.<key>. Also drives the quest module's VARIABLE objective type."],
            ["tag", "tag <add|remove> <tagName>", "Adds/removes a string tag on the player's profile — the backbone of the tag condition."],
            ["condition", "condition <expression>", "Evaluates an expression and aborts the remaining action list if it's false — the GUI/ability equivalent of a conditions: list inline in an actions: block."],
            ["teleport", "teleport warp:<id> · teleport @look <blocks> · teleport <x> <y> <z> · teleport <world> <x> <y> <z>", "Teleports the caster to a warp, forward along their look direction, or to absolute coordinates."],
            ["spawn_mob", "spawn_mob <mob_id> [count] [radius:<r>]", "Spawns one or more custom mobs near the caster."],
            ["stat_modify", "stat_modify <add|set|reset> <stat_id> [value]", "Adds to, sets, or resets a player's base stat value. value supports $variable$ expressions."],
            ["foreach", "foreach @all <inner_event…> · foreach @nearby:<radius> <inner_event…>", "Runs an inner event once per matched online player. notify/delay: on the inner event are not supported."],
            ["run_script", "run_script <interval_ticks> <times> <inner_event…>", "Schedules an inner event to repeat on the main thread — e.g. run_script 20 5 spawn_mob zombie_minion 1 spawns once a second for 5 seconds."],
            ["interrupt", "interrupt", "Aborts the rest of the current pipeline stage/action list unconditionally."],
            ["notify", "notify <message> [category:<name>] [io:<type>] [key:value…]", "Sends a formatted notification. The richer version from the notify module (see below) registers after this one and wins."],
            ["counter", "counter <increment|decrement|add|reset> player.var.<name> [amount]", "Numeric convenience wrapper around a player.var.* counter."],
            ["entity", "entity set <health|max_health|name> <value> [target-selector]", "Sets a live property on a resolved entity — health/max_health accept a full expression, name accepts MiniMessage with $variable$ substitution."],
            ["apply_potion", "apply_potion <effect> <amplifier> <durationSeconds> [target-selector]", "Applies a vanilla potion effect to the resolved target(s)."],
            ["give_coins / take_coins", "give_coins <amount> [target-selector] · take_coins <amount> [target-selector]", "Adds/removes purse coins via the economy service — the script-DSL counterpart to the GIVE_COINS/TAKE_COINS ability mechanics."],
          ],
        },
      },
      {
        heading: "Economy module",
        table: {
          headers: ["Event", "Syntax", "What it does"],
          rows: [
            ["economy_add", "economy_add <amount>", "Adds coins directly to the caster's purse."],
            ["economy_remove", "economy_remove <amount>", "Removes coins from the caster's purse."],
            ["economy_deposit", "economy_deposit <amount|all|half>", "Moves coins from purse to bank."],
            ["economy_withdraw", "economy_withdraw <amount|all|half|X%>", "Moves coins from bank to purse."],
            ["economy_deposit_all", "economy_deposit_all", "Moves the entire purse into the bank in one call."],
          ],
        },
        body: ["amount arguments accept abbreviations and arithmetic through the same CoinExpressionParser the /eco command uses (2.5k, 1m, (1k+500)*2)."],
      },
      {
        heading: "GUI module",
        table: {
          headers: ["Event", "Syntax", "What it does"],
          rows: [
            ["open_gui", "open_gui <gui-id> [propKey=value…]", "Opens another GUI for the caster, optionally seeding its session prop bag."],
            ["gui_back", "gui_back", "Closes the current GUI and reopens whichever GUI opened it."],
            ["close", "close", "Closes the caster's open GUI (or just their inventory outside a GUI context)."],
            ["gui_force_craft", "gui_force_craft", "Runs the full craft pipeline against the GUI's current INPUT snapshot — see GUIs & Machines."],
            ["sound", "sound <sound.key> · sound <ignored> <sound.key>", "Plays a sound to the caster at their own location. A leading target-style argument is accepted but not actually used to route the sound."],
            ["givexp", "givexp player <SKILL> <amount>", "Grants skill XP directly (SKILL is the Skill enum name, e.g. MINING)."],
            ["recalculate_stats", "recalculate_stats", "Forces an immediate stat recalculation — commonly wired into a storage GUI's on-close so accessory/backpack contents apply instantly."],
            ["open_dialog_input", "open_dialog_input <prop_key> [title] [label] [placeholder…] [return=<gui_id>]", "Opens a chat-based text-input dialog and writes the typed value into prop.<prop_key>."],
            ["open_sign_input", "open_sign_input <prop_key> [placeholder text…]", "Same idea via a sign-editing GUI instead of chat."],
            ["enchant_apply / enchant_select / enchant_remove / enchant_back", "no arguments", "Internal to the enchanting table GUI's own click actions — apply/remove the currently selected enchant, or navigate the catalog/level-list pages."],
            ["gui_alchemy_start / gui_alchemy_brew", "no arguments", "Starts, or checks progress on, the alchemy table's timed brew."],
          ],
        },
      },
      {
        heading: "NPC module",
        table: {
          headers: ["Event", "Syntax", "What it does"],
          rows: [
            ["dialogue", "dialogue start <dialogue-id>", "Starts a bound conversation for the caster."],
            ["gui", "gui open <gui-id>", "Opens a GUI from NPC interaction scripts — functionally overlaps with open_gui but is the NPC module's own event name."],
          ],
        },
      },
      {
        heading: "Quest, points, and progression modules",
        table: {
          headers: ["Event", "Syntax", "What it does"],
          rows: [
            ["quest_start", "quest_start <questId>", "Starts a quest for the caster."],
            ["quest_complete", "quest_complete <questId>", "Force-completes a quest immediately, regardless of objective progress."],
            ["quest_cancel", "quest_cancel <questId>", "Cancels a quest, resetting its status back to not-started."],
            ["quest_fail", "quest_fail <questId>", "Marks a quest failed."],
            ["objective_start", "objective_start <objectiveId>", "Manually starts a single objective by id — e.g. to kick off a delay/timer type objective."],
            ["objective_delete", "objective_delete <objectiveId>", "Clears a single objective's tracked progress state."],
            ["quest_board_assign", "quest_board_assign <boardId>", "Assigns a quest from the named board to the caster, if they don't already have one from it."],
            ["quest_board_collect", "quest_board_collect <boardId> <slot>", "Collects a completed quest board reward from a given slot."],
            ["journal", "journal [open]", "Opens the player's quest journal."],
            ["point", "point <category> <add|set|take> <amount>", "Adjusts a Quest Points category balance."],
            ["progression_levelup", "progression_levelup <treeId> <nodeId>", "Spends points to level up a node in a progression tree (e.g. Geomancy)."],
            ["progression_unlock_tier", "progression_unlock_tier <treeId>", "Unlocks the next tier of a progression tree."],
            ["progression_reset", "progression_reset <treeId>", "Resets a progression tree, refunding points per the progression.refund-percent config key."],
          ],
        },
      },
      {
        heading: "Warp, notify, and combat modules",
        table: {
          headers: ["Event", "Syntax", "What it does"],
          rows: [
            ["warp_to", "warp_to <warpId>", "Teleports the caster straight to a named warp — a simpler sibling of teleport warp:<id>."],
            ["notify", "notify <message> [category:<name>] [io:<type>] [key:value…]", "Sends a categorized, formatted notification (action bar, chat, or title depending on io:). Registers after the script module's own simpler notify and wins, since later module registration overwrites the name."],
            ["notifyall", "notifyall <message> [category:<name>] [io:<type>] [key:value…]", "Same as notify, but broadcasts to every online player."],
            ["multiply_damage", "multiply_damage <factor>", "Multiplies the current hit's running damage total — multiple calls in the same hit compound multiplicatively (two 1.5x calls stack to 2.25x, not 1.5x)."],
          ],
        },
      },
    ],
  },
  {
    slug: "conditions",
    title: "Script Conditions Reference",
    category: "Core Systems",
    summary: "Every keyword ConditionParser recognizes, exhaustively — a condition string is one of these ten forms.",
    sections: [
      {
        heading: "How a condition string is parsed",
        body: [
          "Every string in a conditions: list is parsed independently, then AND-ed together — all must hold. A leading ! negates that one entry. If a string doesn't start with one of the nine keywords below, it's parsed as a raw boolean expression — the fallback, not a special case.",
        ],
        code: { lang: "yaml", content: 'conditions:\n  - "tag quest_started"\n  - "!tag quest_complete"\n  - "$player.stat.HEALTH$ > 50"' },
      },
      {
        heading: "The ten forms",
        table: {
          headers: ["Keyword", "Syntax", "Checks"],
          rows: [
            ["(none)", "<any boolean expression>", "Full expression evaluation — arithmetic, comparisons, and/or, any $variable$ token. This is the fallback for anything not matching a keyword below."],
            ["tag", "tag <tagName>", "Whether the player's profile has the given tag, set via the tag event."],
            ["health", "health <amount>", "Whether the player has at least that much current HP."],
            ["hunger", "hunger <amount>", "Whether the player has at least that much food level."],
            ["location", "location <x>;<y>;<z>;<world> <radius>", "Whether the player is within radius blocks of the given point."],
            ["zone", "zone <zoneId>", "Whether the player is currently inside the named zone."],
            ["variable", "variable <path> <operator> <value>", "Compares a resolved variable path to a literal, e.g. variable player.stat.HEALTH > 100."],
            ["objective", "objective <objectiveId>", "Whether a specific quest objective is currently active."],
            ["quest", "quest <questId> <STATUS>", "Whether a quest is in a given status (e.g. COMPLETED, ACTIVE)."],
            ["point", "point <category> <amount>", "Whether the player's Quest Points balance in category is at least amount."],
          ],
        },
      },
      {
        heading: "Where conditions are read",
        body: [
          "The same parser backs every conditions: list in the plugin — ability gating, GUI click actions, quest objective gates, NPC hologram visibility, and skill/recipe unlock checks all go through ConditionParser.parseList(). Learning these ten forms once covers all of them.",
        ],
      },
    ],
  },
  {
    slug: "pipelines",
    title: "Pipelines: Extreme Customization",
    category: "Core Systems",
    summary:
      "HookBus lets you splice new logic into combat, mining, fishing, item abilities, boss abilities, and GUIs at named insertion points — no Java, no recompile. This is how you push Valmora past what the shipped config does.",
    sections: [
      {
        heading: "What HookBus actually is",
        body: [
          "Combat, resource mining, fishing, item abilities, boss abilities, and GUI lifecycle events each run a fixed Java sequence — hit → calculate → apply, break → gate → drops, trigger → cooldown/mana → mechanics. HookBus is one shared dispatch primitive that exposes named insertion points inside those sequences, extendable from YAML using the exact same condition/event DSL as everything else in the plugin — or from a Java hook, for addon authors.",
          "It's zero-cost when unused: every call site checks whether any stage or hook is registered at its points before it even builds an ExecutionContext. A server that never touches a *_pipeline.yml file runs the identical code path as if HookBus didn't exist. This is the mechanism that turns \"the plugin as shipped\" into \"whatever you can express in YAML plus a condition/event\" — it's the real answer to \"how far can this go.\"",
        ],
      },
      {
        heading: "Every insertion point",
        table: {
          headers: ["Point", "Fires", "interrupt means"],
          rows: [
            ["combat:pre_damage", "Before DamageCalculator runs at all.", "Cancels the hit entirely — nothing is rolled."],
            ["combat:post_calculation", "After damage/crit/type are known, before health is touched.", "The hit was \"rolled\" but doesn't land — no health change, no indicator."],
            ["combat:post_application", "After health is reduced and the indicator has spawned.", "No effect — informational only."],
            ["combat:on_dmg_dealt", "Same timing as post_application, after ON_HIT item abilities / boss triggers have already fired.", "No effect — informational only."],
            ["combat:on_death", "In MobDeathListener, after XP/gold/loot are resolved for a custom mob's death (killer required).", "No effect — informational only."],
            ["resource:pre_break", "After the Breaking Power gate passes, before drops roll — zone resource nodes only.", "Cancels the break silently — the stage should notify the player itself."],
            ["resource:post_break", "After drops are granted and the block has progressed/regenerated.", "No effect — informational only."],
            ["fishing:pre_catch", "Before the sea-creature roll / loot table roll.", "Cancels the catch — nothing is granted."],
            ["fishing:post_catch", "After the reward (item or sea creature) is resolved.", "No effect — informational only."],
            ["item:pre_ability", "After an item ability's own trigger/condition/cooldown/mana gate passes, before its mechanics run.", "Cancels just that firing — the cooldown/mana already spent stays spent."],
            ["item:post_ability", "After the ability's mechanics have run.", "No effect — informational only."],
            ["mob:pre_ability", "After a boss ability's own cooldown/interval/chance gate passes, before its mechanics run.", "Cancels just that firing (e.g. a silence debuff) — its cooldown stays consumed."],
            ["mob:post_ability", "After the boss ability's mechanics have run.", "No effect — informational only."],
            ["gui:<guiId>:on_open", "A GUI's on-open block.", "Aborts opening the GUI."],
            ["gui:<guiId>:on_close / on_slot_update / on_update", "The matching GUI lifecycle block.", "No effect — the GUI always finishes closing/re-rendering regardless."],
          ],
        },
      },
      {
        heading: "Anatomy of a stage",
        body: [
          "One file per domain — combat_pipeline.yml, resource_pipeline.yml, fishing_pipeline.yml, item_pipeline.yml, mob_pipeline.yml — all optional, all shipping with stages: []. GUI has no separate file; its four lifecycle blocks (on-open, etc.) are already GUI-authored pipeline stages under the hood.",
        ],
        code: {
          lang: "yaml",
          content: `# combat_pipeline.yml
stages:
  - id: berserker_rage                 # unique per file; shown by /valmora pipeline list <point>
    when: pre_damage                   # point name, without the domain prefix
    conditions:
      - "$player.hp$ < $player.max_hp$ * 0.25"
    on-pass:
      - "multiply_damage 1.5"
      - "notify <red>Berserker Rage! Fighting below 25% HP!</red>"
    on-fail: []`,
        },
      },
      {
        heading: "The damage/enchant ordering rule",
        body: [
          "combat:pre_damage runs before DamageCalculator computes anything, so a stage can't touch enchant modifiers directly — that's still exclusively the enchant module's own hooks. Instead, multiply_damage stacks a multiplicative pipeline_multiplier that's applied at one fixed, documented point:",
        ],
        code: {
          lang: "text",
          content: `fullDamage = baseDamage × (1 + strength/100)
if isCritical: fullDamage ×= (1 + critDamage/100)
fullDamage ×= enchantDamageMultiplier      # enchant module's own hooks
fullDamage ×= pipelineMultiplier           # <-- multiply_damage lands here
mitigated = fullDamage × (100 / (defense + 100))   # skipped if damage type ignores defense
finalDamage = floor(mitigated)`,
        },
      },
      {
        heading: "Worked example: a real execute mechanic",
        body: [
          "post_calculation is the only point where you both know the final numbers and can still stop the hit from landing — exactly what an \"execute below 15% HP\" effect needs. Interrupting here means the target visibly dodges/no-sells the hit rather than taking reduced damage.",
        ],
        code: {
          lang: "yaml",
          content: `stages:
  - id: sanctuary_ward
    when: post_calculation
    conditions:
      - "$target.health$ / $target.max_health$ < 0.15"
      - "tag has_sanctuary_ward"
    on-pass:
      - "notify <aqua>Sanctuary Ward absorbs the killing blow!</aqua>"
      - "tag remove has_sanctuary_ward"
      - "interrupt"`,
        },
      },
      {
        heading: "Worked example: server-wide lifesteal with zero Java",
        body: [
          "This is the pattern for adding an entirely new stat the engine never shipped: define it in stats/core.yml like any other stat, then read it back with $player.stat.<id>$ from a pipeline stage — no config.yml change needed, since that file only remaps the eleven built-in engine roles, not arbitrary new stats.",
          "combat:on_dmg_dealt is the right point for this: it fires after ON_HIT item abilities and boss triggers have already reacted, and dmg.final_damage (the fully mitigated number that actually landed) is still attached to the context. Healing itself has no dedicated script event, so the entity event sets the attacker's health directly via a full expression.",
        ],
        code: {
          lang: "yaml",
          content: `# stats/core.yml — a brand-new stat, no Java involved
lifesteal:
  display-name: "Lifesteal"
  default-value: 0.0
  max-value: 50.0
  color: "<dark_red>"
  icon: "REDSTONE"
  description: "Heals a percentage of damage you deal."

# combat_pipeline.yml
stages:
  - id: lifesteal_on_hit
    when: on_dmg_dealt
    conditions:
      - "$player.stat.lifesteal$ > 0"
      - "!$dmg.is_immune$"
    on-pass:
      - "entity set health $player.hp$ + ($dmg.final_damage$ * $player.stat.lifesteal$ / 100) @self"`,
        },
      },
      {
        heading: "Worked example: gated, luck-scaled mining nodes",
        body: [
          "resource:pre_break sees resource.material and resource.stage before the drop roll — enough to layer a completely custom rule (a tag-gated vein, a rare bonus roll) on top of the zone's normal Breaking Power gate without touching the zone definition at all.",
        ],
        code: {
          lang: "yaml",
          content: `# resource_pipeline.yml
stages:
  - id: ancient_debris_bonus_roll
    when: post_break
    conditions:
      - "$resource.material$ == ANCIENT_DEBRIS"
      - "$math.random$ < ($player.stat.luck$ / 200)"
    on-pass:
      - "give_coins 500"
      - "notify <gold>A vein of pure fortune! +500 coins.</gold>"`,
        },
      },
      {
        heading: "Worked example: a boss with a real phase transition",
        body: [
          "mob:pre_ability / mob:post_ability expose mob.ability_id and mob.ability_trigger — not mob.id/mob.level, which only exist at combat:on_death. Combine that with $target.health$/$target.max_health$ (the boss, if it's the ability's target/caster context) to gate an ability by phase, or to silence one ability entirely once a phase ends.",
        ],
        code: {
          lang: "yaml",
          content: `# mob_pipeline.yml
stages:
  - id: forge_titan_phase_lock
    when: pre_ability
    conditions:
      - "$mob.ability_id$ == ground-slam"
      - "$target.health$ / $target.max_health$ < 0.5"
    on-pass:
      - "interrupt"   # Ground Slam is a phase-1-only ability`,
        },
      },
      {
        heading: "Registering a Java hook (addon plugins)",
        body: [
          "No DSL required — any plugin, including a third-party addon, can react to a point directly through ValmoraAPI. Java hooks persist across /valmora reload (YAML stages don't — they're recompiled from scratch every reload), so an addon registering from its own onDisable() is responsible for calling unregisterHook itself.",
        ],
        code: {
          lang: "java",
          content: `ValmoraAPI.getInstance().getHookBus().registerHook(
    "combat:pre_damage",
    "my-plugin:dodge-check",
    context -> {
        if (Math.random() < 0.1) {
            context.getPlayerCaster().ifPresent(p -> p.sendMessage("Dodged!"));
            return StageResult.INTERRUPT;
        }
        return StageResult.CONTINUE;
    }
);`,
        },
      },
      {
        heading: "Inspecting the bus at runtime",
        body: [
          "/valmora pipeline list [point] lists every registered stage and hook, optionally filtered to one insertion point — the fastest way to confirm a YAML stage actually loaded, or to see what an installed addon has wired in.",
        ],
        code: { lang: "text", content: "/valmora pipeline list\n/valmora pipeline list combat:pre_damage" },
      },
      {
        heading: "One stage aborting doesn't stop the rest",
        body: [
          "A mid-list condition event inside on-pass throwing an abort falls back to on-fail like everywhere else in the engine — but the stage still reports CONTINUE afterward, not INTERRUPT. Each insertion point runs a list of independent stages, so one stage's internal abort shouldn't by itself stop the others at the same point. If a stage genuinely needs to stop everything after it, call the explicit interrupt event.",
        ],
      },
    ],
  },
  {
    slug: "developer-api",
    title: "Developer API",
    category: "Core Systems",
    summary:
      "Build a real addon plugin against Valmora — ValmoraAPI, registering your own script events, variables, ability mechanics, and quest objectives from Java.",
    sections: [
      {
        heading: "What you're building against",
        body: [
          "Everything Valmora exposes to other code runs through one interface: org.nakii.valmora.api.ValmoraAPI. It's a static holder — ValmoraAPI.getInstance() — set once by the plugin itself during startup and read by every module, mechanic, and script event internally. Your addon uses the exact same entry point Valmora's own code does; there's no separate, more limited \"public\" surface.",
          "It's safe to call ValmoraAPI.getInstance() anywhere in your own plugin's onEnable() or later, as long as your plugin.yml declares Valmora as a hard depend — Bukkit guarantees Valmora finishes its own onEnable() (which sets the provider and enables every internal module) before your plugin's onEnable() runs.",
        ],
        code: { lang: "yaml", content: "# your plugin's plugin.yml\ndepend: [Valmora]" },
      },
      {
        heading: "Setting up your project",
        body: [
          "There's no published Maven/Gradle artifact yet — compile against the built plugin jar directly (a compileOnly-style dependency; never shade Valmora's classes into your own jar). One relocation detail matters if your addon also uses Gson or HikariCP: Valmora relocates its own copies to org.nakii.valmora.lib.gson / .hikari internally, so bring your own unrelocated copies rather than trying to reuse Valmora's — they're not the same classes on the classpath.",
        ],
      },
      {
        heading: "The manager surface",
        body: [
          "ValmoraAPI has one getter per module/manager — around thirty in total. These are the ones an addon reaches for most; anything else a module exposes (e.g. getGuiModule(), getRecipeModule(), getReforgeModule()) follows the identical getXModule() / getXManager() pattern.",
        ],
        table: {
          headers: ["Accessor", "Gives you"],
          rows: [
            ["getScriptModule()", "registerEvent(...), registerProvider(...), the expression/condition parsers, and the shared HookBus via getHookBus()"],
            ["getItemManager() / getAbilityManager()", "Item registry lookups, and mechanicRegistry.registerMechanic(...) for custom ability mechanics"],
            ["getPlayerManager()", "getSession(uuid) → a player's live ValmoraPlayer / active ValmoraProfile — stats, variables, tags"],
            ["getStatRegistry()", "Every registered stat definition — read this to see what custom stats other plugins/configs have already added"],
            ["getEconomy()", "addCoins / removeCoins on a player's purse — the same service the give_coins/take_coins events use"],
            ["getQuestManager()", "registerObjectiveHandler(...) for a new objective type, and trigger(player, type, key, amount) to report progress on it"],
            ["getZoneManager() / getMobManager() / getTimeManager()", "Read zone membership, spawn/query custom mobs, and read the live calendar snapshot"],
            ["getHookBus()", "Register a Java PipelineHook at any insertion point — see the Pipelines page for the full point list"],
          ],
        },
      },
      {
        heading: "Registering a custom script event",
        body: [
          "Implement EventFactory, then call registerEvent(...) once in your own onEnable(). Your event immediately becomes usable anywhere any other event string is — item abilities, GUI actions, pipeline stages, quest rewards — with zero additional wiring.",
        ],
        code: {
          lang: "java",
          content: `public class RollDiceEventFactory implements EventFactory {
    @Override public String getName() { return "roll_dice"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        int sides = args.length > 0 ? Integer.parseInt(args[0]) : 6;
        return context -> context.getPlayerCaster().ifPresent(player -> {
            int roll = 1 + (int) (Math.random() * sides);
            player.sendMessage("You rolled a " + roll + "!");
        });
    }
}

// in your plugin's onEnable():
ValmoraAPI.getInstance().getScriptModule().registerEvent(new RollDiceEventFactory());
// now usable anywhere: "roll_dice 20 notify"`,
        },
      },
      {
        heading: "Registering a custom variable provider",
        body: [
          "Implement VariableProvider to add a whole new $namespace.*$ family. resolve() receives the path with the namespace already stripped, so $myplugin.balance$ arrives as path = [\"balance\"].",
        ],
        code: {
          lang: "java",
          content: `public class MyPluginVariableProvider implements VariableProvider {
    @Override public String getNamespace() { return "myplugin"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return switch (path[0]) {
            case "balance" -> context.getPlayerCaster()
                .map(p -> myPluginEconomy.getBalance(p.getUniqueId()))
                .orElse(0.0);
            default -> null;
        };
    }
}

ValmoraAPI.getInstance().getScriptModule().registerProvider(new MyPluginVariableProvider());
// now usable anywhere: "condition $myplugin.balance$ > 1000"`,
        },
      },
      {
        heading: "Registering a custom ability mechanic",
        body: [
          "Implement AbilityMechanic and register it on the ability manager's mechanicRegistry. Your mechanic's ID becomes usable as a type: in any item or boss ability's mechanics list, right alongside the built-in fourteen.",
        ],
        code: {
          lang: "java",
          content: `public class SwapPositionsMechanic implements AbilityMechanic {
    @Override public String getId() { return "swap_positions"; }

    @Override
    public void execute(ExecutionContext context) {
        context.getPlayerCaster().ifPresent(caster ->
            context.getTarget().ifPresent(target -> {
                Location a = caster.getLocation(), b = target.getLocation();
                caster.teleport(b);
                target.teleport(a);
            }));
    }
}

ValmoraAPI.getInstance().getAbilityManager().mechanicRegistry.registerMechanic(new SwapPositionsMechanic());
// now usable in any item/mob ability:
//   mechanics:
//     - type: swap_positions`,
        },
      },
      {
        heading: "Registering a custom quest objective type",
        body: [
          "Implement ObjectiveHandler for the type: value your objective should match in quest YAML. Progress itself isn't pushed by the handler — call QuestManager.trigger(player, type, key, amount) from your own Bukkit listener whenever the relevant thing happens, and the quest engine advances any matching active objective.",
        ],
        code: {
          lang: "java",
          content: `public class FishCaughtObjectiveHandler implements ObjectiveHandler {
    @Override public String getTypeId() { return "fish_caught"; }
}

ValmoraAPI.getInstance().getQuestManager().registerObjectiveHandler(new FishCaughtObjectiveHandler());

// elsewhere, in your own PlayerFishEvent listener:
ValmoraAPI.getInstance().getQuestManager().trigger(player, "fish_caught", "any", 1);`,
        },
      },
      {
        heading: "Hooking the pipeline from Java",
        body: [
          "The same HookBus that YAML *_pipeline.yml stages use is a first-class Java API — see the Pipelines page for the full insertion-point list and dispatch semantics. This is the one registration in this whole page that survives /valmora reload, since HookBus is held as a field on the script module and never cleared by it.",
        ],
        code: {
          lang: "java",
          content: `ValmoraAPI.getInstance().getHookBus().registerHook(
    "combat:pre_damage",
    "my-plugin:dodge-check",
    context -> {
        if (Math.random() < 0.1) {
            context.getPlayerCaster().ifPresent(p -> p.sendMessage("Dodged!"));
            return StageResult.INTERRUPT;
        }
        return StageResult.CONTINUE;
    }
);`,
        },
      },
      {
        heading: "The one thing to know before you ship: reload wipes most of this",
        body: [
          "This is verified against the actual module code, not a guess, because it will bite an addon developer who doesn't know it: /valmora reload calls onDisable() then onEnable() on Valmora's own modules in place. ScriptModule.onDisable() clears its variable-provider and event registries outright; onEnable() then repopulates only Valmora's own built-ins. AbilityManager does the same to mechanicRegistry. QuestModule goes further and replaces its QuestManager with a brand-new instance. None of the three registration calls on this page survive that — your custom event, variable provider, ability mechanic, and objective handler are all gone the moment an admin runs /valmora reload, with no error or warning.",
          "HookBus is the one exception, by design: it's constructed once and held as a field that neither onDisable() nor onEnable() touches, so a Java hook registered via getHookBus().registerHook(...) is genuinely permanent for the life of the server process.",
          "There is currently no addon-facing \"Valmora just reloaded\" event to listen for and re-register automatically. Until one exists, the honest options are: prefer HookBus for anything that must survive a reload, or accept that your addon's own reload (or restart) needs to run again after any /valmora reload on the same server.",
        ],
      },
      {
        heading: "Putting it together: a minimal addon",
        body: [
          "A complete onEnable() combining three of the pieces above — a new stat namespace, a scripted event, and a permanent pipeline hook — with nothing else required to make all three usable from any Valmora YAML file on the server.",
        ],
        code: {
          lang: "java",
          content: `@Override
public void onEnable() {
    ValmoraAPI api = ValmoraAPI.getInstance();

    api.getScriptModule().registerProvider(new MyPluginVariableProvider());
    api.getScriptModule().registerEvent(new RollDiceEventFactory());

    api.getHookBus().registerHook("combat:on_death", "my-plugin:death-log", context -> {
        context.getPlayerCaster().ifPresent(killer ->
            getLogger().info(killer.getName() + " scored a custom-mob kill"));
        return StageResult.CONTINUE;
    });
}`,
        },
      },
    ],
  },
];

export function getDoc(slug: string): DocEntry | undefined {
  return DOCS.find((d) => d.slug === slug);
}
