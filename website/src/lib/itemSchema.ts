import reference from "@/generated/reference.json";

// Lists for the item generator. Everything except the parameter presets and the material
// shortlist comes from the plugin's own source (scripts/extract-reference.mjs), so the generator
// can never offer a stat, trigger, mechanic, rarity or item type the plugin doesn't have.

// MiniMessage named colors used by rarities.yml -> the hex Minecraft renders them as.
const NAMED_COLORS: Record<string, string> = {
  black: "#000000",
  dark_blue: "#0000aa",
  dark_green: "#00aa00",
  dark_aqua: "#00aaaa",
  dark_red: "#aa0000",
  dark_purple: "#aa00aa",
  gold: "#ffaa00",
  gray: "#aaaaaa",
  dark_gray: "#555555",
  blue: "#5555ff",
  green: "#55ff55",
  aqua: "#55ffff",
  red: "#ff5555",
  light_purple: "#ff55ff",
  yellow: "#ffff55",
  white: "#ffffff",
};

function toHex(miniMessage: string): string {
  const tag = miniMessage.replace(/[<>]/g, "");
  return tag.startsWith("#") ? tag : NAMED_COLORS[tag] ?? "#ffffff";
}

export const RARITIES = (reference.rarities as { id: string; color?: string; rank?: number }[])
  .slice()
  .sort((a, b) => (a.rank ?? 0) - (b.rank ?? 0))
  .map((r) => ({ id: r.id.toUpperCase(), color: toHex(r.color ?? "<white>") }));

export const ITEM_TYPES: string[] = [
  "NONE",
  ...reference.itemTypes.builtIn.filter((t) => t !== "NONE" && t !== "ALL"),
  ...reference.itemTypes.extra,
];

export const STATS: string[] = reference.stats.map((s) => s.id);

export const TRIGGERS: string[] = reference.itemTriggers;

// Starting `params:` for each mechanic when picked in the generator. Mechanics without a preset
// (e.g. CANCEL_TRAMPLE, which takes no params) start empty.
const PARAM_PRESETS: Record<string, string> = {
  DAMAGE: 'damage: 10\ndamage-type: MAGIC\ntarget: "@target"',
  HEAL: 'heal: 10\ntarget: "@player"',
  APPLY_EFFECT: 'effect: slowness\nduration: 5.0\namplifier: 2\ntarget: "@target"',
  SCRIPT: 'events:\n  - "notify <gold>It works!"',
  MODIFY_STAT: "stat: strength\namount: 10\nduration: -1",
  TELEPORT: "distance: 8",
  PUSH_ENTITIES: 'force: 1.5\ntarget: "@enemies_in_radius{r=5}"',
  PULL_ENTITIES: 'strength: 1.5\ntarget: "@enemies_in_radius{r=10}"',
  GIVE_COINS: "amount: 50",
  TAKE_COINS: "amount: 50",
  IGNITE: 'duration: 3\ntarget: "@target"',
  LAUNCH_PLAYER: "y-force: 1.0\nforward-force: 1.5\nno-fall-damage: true",
  LAUNCH_PROJECTILE: "projectile: arrow\nvelocity: 2.0\ndamage: 10",
  AOE_MINE: "radius: 1",
  CHARGE_JUMP: "max-charge-ms: 2000\nmin-y-force: 0.4\nmax-y-force: 2.2",
};

export const MECHANICS = reference.mechanics.map((id) => ({ id, params: PARAM_PRESETS[id] ?? "" }));

export const COMMON_MATERIALS = [
  "WOODEN_SWORD",
  "STONE_SWORD",
  "IRON_SWORD",
  "GOLDEN_SWORD",
  "DIAMOND_SWORD",
  "NETHERITE_SWORD",
  "BOW",
  "CROSSBOW",
  "TRIDENT",
  "STICK",
  "BLAZE_ROD",
  "DIAMOND_PICKAXE",
  "NETHERITE_PICKAXE",
  "LEATHER_HELMET",
  "IRON_HELMET",
  "DIAMOND_HELMET",
  "NETHERITE_HELMET",
  "DIAMOND_CHESTPLATE",
  "DIAMOND_LEGGINGS",
  "DIAMOND_BOOTS",
] as const;
