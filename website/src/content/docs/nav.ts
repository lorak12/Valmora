// Sidebar order for the docs. Every .mdx file in this folder must appear here exactly once
// (checked by src/lib/docs.ts at build time), and every slug here must have a file.
export const DOC_NAV: { title: string; slugs: string[] }[] = [
  {
    title: "Getting Started",
    slugs: ["introduction", "installation", "file-layout", "core-concepts", "commands"],
  },
  {
    title: "Tutorials",
    slugs: [
      "tutorial-first-item",
      "tutorial-first-mob",
      "tutorial-boss-fight",
      "tutorial-first-quest",
      "tutorial-first-recipe",
      "tutorial-first-zone",
      "tutorial-first-menu",
      "tutorial-first-reforge",
    ],
  },
  {
    title: "Items & Combat",
    slugs: [
      "items",
      "abilities",
      "target-selectors",
      "stats",
      "rarities-item-types",
      "set-bonuses",
      "backpacks",
      "enchants",
      "modifiers",
      "combat",
    ],
  },
  { title: "Crafting", slugs: ["recipes", "machines", "anvil", "alchemy"] },
  {
    title: "World",
    slugs: [
      "mobs",
      "zones",
      "resource-nodes",
      "block-loot",
      "fishing",
      "warps",
      "npcs",
      "time-calendar",
      "world-rules",
      "death",
    ],
  },
  {
    title: "Quests",
    slugs: ["quests", "quest-packages", "conversations", "objective-types", "quest-boards-points"],
  },
  {
    title: "Progression & Economy",
    slugs: ["skills", "collections", "progression-trees", "pets", "economy", "profiles"],
  },
  { title: "Interface", slugs: ["guis", "ui", "hud-items", "notifications"] },
  {
    title: "Scripting",
    slugs: ["scripting", "events", "conditions", "variables", "expressions", "pipelines"],
  },
  { title: "Reference", slugs: ["config-yml", "default-files", "content-packs", "troubleshooting"] },
  {
    title: "Developer API",
    slugs: ["developer-api", "developer-extending", "developer-hookbus", "developer-events"],
  },
];
