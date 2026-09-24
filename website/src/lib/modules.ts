import reference from "@/generated/reference.json";

// The real module registration order, extracted from Valmora.java at build time by
// scripts/extract-reference.mjs — order matters at runtime (later modules may depend on earlier ones).
export const MODULE_CHAIN: readonly string[] = reference.moduleChain;

// A curated subset for the compact hero schematic — the modules a new admin
// meets first, in real chain order.
export const HERO_MODULES = ["script", "stat", "item", "mob", "combat", "gui", "recipe", "economy", "skill"].filter(
  (id) => MODULE_CHAIN.includes(id)
);

const LABEL_OVERRIDES: Record<string, string> = {
  ui: "UI",
  gui: "GUI",
  npc: "NPC",
  hud: "HUD",
};

export const MODULE_LABELS: Record<string, string> = Object.fromEntries(
  MODULE_CHAIN.map((id) => [
    id,
    LABEL_OVERRIDES[id] ?? id.split("_").map((w) => w[0].toUpperCase() + w.slice(1)).join(" "),
  ])
);
