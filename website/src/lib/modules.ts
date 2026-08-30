// The real module registration order from Valmora.java (see CLAUDE.md §5).
// Order matters at runtime — later modules may depend on earlier ones.
// Corrected 2026-08-30: this previously predated the rarity/machine/modifier modules and still
// listed the removed reforge module (reforges are now shipped content over the generic modifier
// framework, not a dedicated module — see docs/modules/design/modifier.md).
export const MODULE_CHAIN = [
  "script",
  "time",
  "rarity",
  "stat",
  "player",
  "economy",
  "ui",
  "ability",
  "item",
  "mob",
  "skill",
  "combat",
  "gui",
  "recipe",
  "machine",
  "modifier",
  "alchemy",
  "enchant",
  "zone",
  "resource",
  "fishing",
  "npc",
  "warp",
  "points",
  "notify",
  "quest",
  "collection",
  "hud",
  "calendar",
  "pet",
  "progression",
] as const;

// A curated subset for the compact hero schematic — the modules a new admin
// meets first, in real chain order.
export const HERO_MODULES = [
  "script",
  "stat",
  "item",
  "mob",
  "combat",
  "gui",
  "recipe",
  "economy",
  "skill",
] as const;

export type ModuleId = (typeof MODULE_CHAIN)[number];

export const MODULE_LABELS: Record<string, string> = {
  script: "Script",
  time: "Time",
  rarity: "Rarity",
  stat: "Stat",
  player: "Player",
  economy: "Economy",
  ui: "UI",
  ability: "Ability",
  item: "Item",
  mob: "Mob",
  skill: "Skill",
  combat: "Combat",
  gui: "GUI",
  recipe: "Recipe",
  machine: "Machine",
  modifier: "Modifier",
  alchemy: "Alchemy",
  enchant: "Enchant",
  zone: "Zone",
  resource: "Resource",
  fishing: "Fishing",
  npc: "NPC",
  warp: "Warp",
  points: "Points",
  notify: "Notify",
  quest: "Quest",
  collection: "Collection",
  hud: "HUD",
  calendar: "Calendar",
  pet: "Pet",
  progression: "Progression",
};
