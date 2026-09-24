// Drift guard for the docs (runs as `prebuild` / `predev`).
//
// Scans the plugin's own Java source and shipped YAML for every id an admin can type — script
// events, ability mechanics, triggers, quest objective types, condition keywords, variable
// namespaces, commands, stats, rarities, item types, shipped files — and writes them to
// src/generated/reference.json, which the docs' <RefTable> component renders.
//
// Human-written syntax/descriptions live in src/content/reference/<kind>.yml, keyed by id. The
// build fails if the plugin has an id with no description there (someone added a feature without
// documenting it) or the docs describe an id the plugin no longer has (someone removed one).

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parse as parseYaml } from "yaml";

const WEBSITE = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const REPO = path.resolve(WEBSITE, "..");
const JAVA = path.join(REPO, "src/main/java/org/nakii/valmora");
const RESOURCES = path.join(REPO, "src/main/resources");
const REFERENCE_DIR = path.join(WEBSITE, "src/content/reference");
const OUT = path.join(WEBSITE, "src/generated/reference.json");

function walk(dir, filter) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full, filter));
    else if (filter(full)) out.push(full);
  }
  return out;
}

const javaFiles = walk(JAVA, (f) => f.endsWith(".java"));
const read = (f) => fs.readFileSync(f, "utf8");
const rel = (f) => path.relative(REPO, f).replaceAll("\\", "/");
const uniqSorted = (xs) => [...new Set(xs)].sort();

const stripComments = (src) => src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");

function enumConstants(file) {
  const body = /enum\s+\w+\s*\{([\s\S]*?)(?:;|\})/.exec(stripComments(read(path.join(JAVA, file))))?.[1] ?? "";
  return body
    .split(",")
    .map((s) => s.trim().replace(/\(.*$/s, ""))
    .filter((s) => /^[A-Z_]+$/.test(s));
}

/** Registry-backed "enum" classes (ItemType, MobCategory, ...) declare their built-ins as `define("X")`. */
function definedConstants(file) {
  return [...stripComments(read(path.join(JAVA, file))).matchAll(/=\s*define\("([A-Z_]+)"/g)].map((m) => m[1]);
}

// --- script events -----------------------------------------------------------------------
// Any class implementing EventFactory: literal names returned from getName() (including ternaries),
// plus names handed to a local `factory("name", ...)` helper (QuestEventFactory).
function extractEvents() {
  const events = [];
  for (const f of javaFiles) {
    const src = read(f);
    if (!/\bEventFactory\b/.test(src) || /implements\s+\w*IO\b/.test(src)) continue;
    for (const m of src.matchAll(/String\s+getName\(\)\s*\{([^}]*)\}/g)) {
      for (const lit of m[1].matchAll(/"([a-z_]+)"/g)) events.push({ id: lit[1], source: rel(f) });
    }
    for (const m of src.matchAll(/\bfactory\(\s*"([a-z_]+)"/g)) events.push({ id: m[1], source: rel(f) });
  }
  const byId = new Map();
  for (const e of events) if (!byId.has(e.id)) byId.set(e.id, e);
  return [...byId.values()].sort((a, b) => a.id.localeCompare(b.id));
}

// --- ability mechanics ---------------------------------------------------------------------
function extractMechanics() {
  const manager = read(path.join(JAVA, "module/item/AbilityManager.java"));
  const classes = [...manager.matchAll(/registerMechanic\(new\s+([\w.]+)\(/g)].map((m) => m[1].split(".").pop());
  return classes.map((cls) => {
    const file = javaFiles.find((f) => path.basename(f) === `${cls}.java`);
    if (!file) throw new Error(`extract-reference: mechanic class ${cls} not found`);
    const id = /getId\(\)\s*\{\s*return\s+"([^"]+)"/.exec(read(file))?.[1];
    if (!id) throw new Error(`extract-reference: couldn't read getId() of ${cls}`);
    return id.toUpperCase();
  });
}

// --- quest objective types --------------------------------------------------------------------
function extractObjectives() {
  const src = read(path.join(JAVA, "module/quest/QuestObjectiveTypes.java"));
  return [...src.matchAll(/static final String \w+\s*=\s*"([a-z_]+)"/g)].map((m) => m[1]);
}

// --- condition keywords -----------------------------------------------------------------------
function extractConditions() {
  const src = read(path.join(JAVA, "module/script/condition/ConditionParser.java"));
  return uniqSorted([...src.matchAll(/clean\.startsWith\("([a-z]+) "\)/g)].map((m) => m[1]));
}

// --- variable namespaces ----------------------------------------------------------------------
function extractNamespaces() {
  const out = [];
  for (const f of javaFiles) {
    const src = read(f);
    if (!/implements\s+VariableProvider/.test(src)) continue;
    const m = /getNamespace\(\)\s*\{\s*return\s+"([a-z_]+)"/.exec(src);
    if (m) out.push(m[1]);
  }
  return uniqSorted(out);
}

// --- commands ---------------------------------------------------------------------------------
function extractCommands() {
  const plugin = parseYaml(read(path.join(RESOURCES, "plugin.yml")));
  return Object.entries(plugin.commands ?? {}).map(([name, c]) => ({
    id: name,
    usage: c.usage ?? `/${name}`,
    description: c.description ?? "",
    permission: c.permission ?? null,
    aliases: c.aliases ?? [],
  }));
}

// --- stats, rarities, item types ---------------------------------------------------------------
function extractStats() {
  const stats = [];
  for (const f of walk(path.join(RESOURCES, "stats"), (x) => x.endsWith(".yml"))) {
    for (const [id, def] of Object.entries(parseYaml(read(f)) ?? {})) {
      stats.push({
        id,
        name: def["display-name"] ?? id,
        default: def["default-value"] ?? 0,
        max: def["max-value"] ?? null,
        description: def.description ?? "",
        file: rel(f),
      });
    }
  }
  return stats;
}

function extractRarities() {
  const yml = parseYaml(read(path.join(RESOURCES, "rarities.yml"))) ?? {};
  const root = yml.rarities ?? yml;
  return Object.entries(root).map(([id, def]) => ({ id, ...(typeof def === "object" ? def : {}) }));
}

function extractItemTypes() {
  const builtIn = definedConstants("module/item/ItemType.java");
  const extra = parseYaml(read(path.join(RESOURCES, "item_types.yml")))?.item_types ?? [];
  return { builtIn, extra };
}

function extractConfigSections() {
  return read(path.join(RESOURCES, "config.yml"))
    .split(/\r?\n/)
    .filter((l) => /^[a-z][a-z0-9_-]*:/.test(l))
    .map((l) => l.split(":")[0]);
}

function extractShippedFiles() {
  return walk(RESOURCES, (f) => f.endsWith(".yml"))
    .map((f) => path.relative(RESOURCES, f).replaceAll("\\", "/"))
    .sort();
}

// --- module load order (Valmora.java registerModule calls) ---------------------------------
function extractModuleChain() {
  const src = read(path.join(JAVA, "Valmora.java"));
  const rename = { hud_item: "hud", calendar_event: "calendar" };
  return [...src.matchAll(/moduleManager\.registerModule\((\w+)\)/g)].map((m) => {
    const id = m[1]
      .replace(/(Module|Manager)$/, "")
      .replace(/[A-Z]/g, (c) => "_" + c.toLowerCase());
    return rename[id] ?? id;
  });
}

// --- assemble + check -------------------------------------------------------------------------
const reference = {
  events: extractEvents(),
  mechanics: extractMechanics(),
  itemTriggers: enumConstants("module/item/AbilityTrigger.java"),
  mobTriggers: enumConstants("module/mob/ability/MobAbilityTrigger.java"),
  enchantTriggers: enumConstants("module/enchant/EnchantTrigger.java"),
  objectives: extractObjectives(),
  conditions: extractConditions(),
  namespaces: extractNamespaces(),
  commands: extractCommands(),
  stats: extractStats(),
  rarities: extractRarities(),
  itemTypes: extractItemTypes(),
  mobCategories: definedConstants("module/mob/MobCategory.java"),
  damageTypes: Object.entries(parseYaml(read(path.join(RESOURCES, "damage_types/core.yml"))) ?? {}).map(([id, d]) => ({
    id,
    ...(typeof d === "object" ? d : {}),
  })),
  shippedFiles: extractShippedFiles(),
  moduleChain: extractModuleChain(),
  configSections: extractConfigSections(),
};

// kind in reference.json -> description file in src/content/reference/
const DOCUMENTED = {
  events: "events.yml",
  mechanics: "mechanics.yml",
  itemTriggers: "item-triggers.yml",
  mobTriggers: "mob-triggers.yml",
  enchantTriggers: "enchant-triggers.yml",
  objectives: "objectives.yml",
  conditions: "conditions.yml",
  namespaces: "variables.yml",
  commands: "commands.yml",
  configSections: "config-sections.yml",
};

const idOf = (x) => (typeof x === "string" ? x : x.id);
const problems = [];
const descriptions = {};
for (const [kind, file] of Object.entries(DOCUMENTED)) {
  const full = path.join(REFERENCE_DIR, file);
  const docs = fs.existsSync(full) ? parseYaml(read(full)) ?? {} : {};
  descriptions[kind] = docs;
  const actual = new Set(reference[kind].map(idOf));
  const documented = new Set(Object.keys(docs));
  for (const id of actual) if (!documented.has(id)) problems.push(`${file}: "${id}" exists in the plugin but isn't documented`);
  for (const id of documented) if (!actual.has(id)) problems.push(`${file}: "${id}" is documented but the plugin has no such id`);
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify({ ...reference, descriptions }, null, 2) + "\n");

if (problems.length) {
  console.error(`\nextract-reference: ${problems.length} docs/plugin mismatch(es):\n  - ${problems.join("\n  - ")}\n`);
  process.exit(1);
}
console.log(
  `extract-reference: ${reference.events.length} events, ${reference.mechanics.length} mechanics, ` +
    `${reference.objectives.length} objective types, ${reference.namespaces.length} variable namespaces, ` +
    `${reference.commands.length} commands — all documented.`
);
