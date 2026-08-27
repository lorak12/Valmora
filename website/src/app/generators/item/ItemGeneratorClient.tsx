"use client";

import { useMemo, useState } from "react";
import { CodeBlock } from "@/components/CodeBlock";
import { COMMON_MATERIALS, ITEM_TYPES, MECHANICS, RARITIES, STATS, TRIGGERS } from "@/lib/itemSchema";

type StatRow = { id: string; key: string; value: string };
type MechanicRow = { id: string; type: string; params: string };

let uid = 0;
const nextId = () => `r${(uid++).toString(36)}`;

function slugify(input: string) {
  return input
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "") || "my_item";
}

function indent(text: string, spaces: number) {
  const pad = " ".repeat(spaces);
  return text
    .split("\n")
    .map((l) => (l.trim().length ? pad + l : l))
    .join("\n");
}

function buildYaml(opts: {
  id: string;
  name: string;
  material: string;
  rarity: string;
  itemType: string;
  customModelData: string;
  lore: string[];
  stats: StatRow[];
  hasAbility: boolean;
  abilityName: string;
  trigger: string;
  cooldown: string;
  manaCost: string;
  description: string[];
  mechanics: MechanicRow[];
}) {
  const id = slugify(opts.id || opts.name || "my_item");
  const lines: string[] = [];
  lines.push(`${id}:`);
  lines.push(`  name: "${opts.name || "New Item"}"`);
  lines.push(`  material: ${opts.material}`);
  lines.push(`  rarity: ${opts.rarity}`);
  if (opts.itemType !== "NONE") lines.push(`  item-type: ${opts.itemType}`);
  if (opts.customModelData.trim()) lines.push(`  custom-model-data: ${opts.customModelData.trim()}`);

  const lore = opts.lore.filter((l) => l.trim().length);
  if (lore.length) {
    lines.push(`  lore:`);
    lore.forEach((l) => lines.push(`    - "${l}"`));
  }

  const stats = opts.stats.filter((s) => s.key && s.value.trim().length);
  if (stats.length) {
    lines.push(`  stats:`);
    stats.forEach((s) => lines.push(`    ${s.key.toUpperCase()}: ${s.value.trim()}`));
  }

  if (opts.hasAbility) {
    const abilityId = slugify(opts.abilityName || "ability");
    lines.push(`  abilities:`);
    lines.push(`    ${abilityId}:`);
    lines.push(`      name: "${opts.abilityName || "New Ability"}"`);
    lines.push(`      trigger: ${opts.trigger}`);
    if (opts.trigger === "RIGHT_CLICK") lines.push(`      target-range: 12.0`);
    if (opts.cooldown.trim()) lines.push(`      cooldown: ${opts.cooldown.trim()}`);
    if (opts.manaCost.trim()) lines.push(`      mana-cost: ${opts.manaCost.trim()}`);
    const desc = opts.description.filter((d) => d.trim().length);
    if (desc.length) {
      lines.push(`      description:`);
      desc.forEach((d) => lines.push(`        - "${d}"`));
    }
    if (opts.mechanics.length) {
      lines.push(`      mechanics:`);
      opts.mechanics.forEach((m) => {
        lines.push(`        - type: ${m.type}`);
        lines.push(`          params:`);
        lines.push(indent(m.params, 12));
      });
    }
  }

  return lines.join("\n") + "\n";
}

export default function ItemGeneratorClient() {
  const [id, setId] = useState("flame_wave");
  const [name, setName] = useState("Flame Wave");
  const [material, setMaterial] = useState("DIAMOND_SWORD");
  const [rarity, setRarity] = useState("EPIC");
  const [itemType, setItemType] = useState("SWORD");
  const [customModelData, setCustomModelData] = useState("");
  const [lore, setLore] = useState<string[]>(["A blazing blade."]);
  const [stats, setStats] = useState<StatRow[]>([
    { id: nextId(), key: "damage", value: "30" },
    { id: nextId(), key: "strength", value: "5" },
  ]);

  const [hasAbility, setHasAbility] = useState(true);
  const [abilityName, setAbilityName] = useState("Flame Wave");
  const [trigger, setTrigger] = useState("RIGHT_CLICK");
  const [cooldown, setCooldown] = useState("5.0");
  const [manaCost, setManaCost] = useState("20.0");
  const [description, setDescription] = useState<string[]>(["Deals damage in a radius."]);
  const [mechanics, setMechanics] = useState<MechanicRow[]>([
    { id: nextId(), type: "damage", params: 'damage: 10\ndamage-type: MAGIC\ntarget: "@target"' },
  ]);

  const yaml = useMemo(
    () =>
      buildYaml({
        id,
        name,
        material,
        rarity,
        itemType,
        customModelData,
        lore,
        stats,
        hasAbility,
        abilityName,
        trigger,
        cooldown,
        manaCost,
        description,
        mechanics,
      }),
    [id, name, material, rarity, itemType, customModelData, lore, stats, hasAbility, abilityName, trigger, cooldown, manaCost, description, mechanics]
  );

  const [copied, setCopied] = useState(false);
  const rarityColor = RARITIES.find((r) => r.id === rarity)?.color ?? "#fff";

  function copy() {
    navigator.clipboard.writeText(yaml).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }

  function download() {
    const blob = new Blob([yaml], { type: "text/yaml" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${slugify(id || name)}.yml`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_1fr] items-start">
      <div className="space-y-8">
        <fieldset className="space-y-4">
          <legend className="font-display text-xl mb-1">Identity</legend>
          <Field label="Item ID (used as the YAML key)">
            <input className={inputCls} value={id} onChange={(e) => setId(e.target.value)} placeholder="flame_wave" />
          </Field>
          <Field label="Display name">
            <input className={inputCls} value={name} onChange={(e) => setName(e.target.value)} placeholder="Flame Wave" />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Material">
              <input
                className={inputCls}
                list="materials"
                value={material}
                onChange={(e) => setMaterial(e.target.value.toUpperCase())}
              />
              <datalist id="materials">
                {COMMON_MATERIALS.map((m) => (
                  <option key={m} value={m} />
                ))}
              </datalist>
            </Field>
            <Field label="Item type">
              <select className={inputCls} value={itemType} onChange={(e) => setItemType(e.target.value)}>
                {ITEM_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <Field label="Rarity">
            <div className="flex flex-wrap gap-2">
              {RARITIES.map((r) => (
                <button
                  type="button"
                  key={r.id}
                  onClick={() => setRarity(r.id)}
                  className="flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-mono transition-colors"
                  style={{
                    borderColor: rarity === r.id ? r.color : "var(--line-bright)",
                    background: rarity === r.id ? `${r.color}22` : "transparent",
                  }}
                >
                  <span className="h-2 w-2 rounded-full" style={{ background: r.color }} />
                  {r.id}
                </button>
              ))}
            </div>
          </Field>
          <Field label="Custom model data (optional)">
            <input className={inputCls} value={customModelData} onChange={(e) => setCustomModelData(e.target.value)} placeholder="1001" />
          </Field>
        </fieldset>

        <fieldset className="space-y-3">
          <legend className="font-display text-xl mb-1">Lore</legend>
          <ListEditor values={lore} onChange={setLore} placeholder="A blazing blade." />
        </fieldset>

        <fieldset className="space-y-3">
          <legend className="font-display text-xl mb-1">Stats</legend>
          <div className="space-y-2">
            {stats.map((s) => (
              <div key={s.id} className="flex gap-2">
                <select
                  className={`${inputCls} flex-[1.3]`}
                  value={s.key}
                  onChange={(e) => setStats(stats.map((r) => (r.id === s.id ? { ...r, key: e.target.value } : r)))}
                >
                  {STATS.map((k) => (
                    <option key={k} value={k}>
                      {k}
                    </option>
                  ))}
                </select>
                <input
                  className={`${inputCls} flex-1`}
                  value={s.value}
                  onChange={(e) => setStats(stats.map((r) => (r.id === s.id ? { ...r, value: e.target.value } : r)))}
                  placeholder="10"
                />
                <button type="button" onClick={() => setStats(stats.filter((r) => r.id !== s.id))} className={removeBtn}>
                  ✕
                </button>
              </div>
            ))}
          </div>
          <button
            type="button"
            onClick={() => setStats([...stats, { id: nextId(), key: STATS[0], value: "" }])}
            className={addBtn}
          >
            + Add stat
          </button>
        </fieldset>

        <fieldset className="space-y-4">
          <legend className="font-display text-xl mb-1 flex items-center gap-3">
            Ability
            <label className="flex items-center gap-2 text-xs font-mono font-normal text-[var(--ink-muted)]">
              <input type="checkbox" checked={hasAbility} onChange={(e) => setHasAbility(e.target.checked)} />
              enabled
            </label>
          </legend>
          {hasAbility && (
            <div className="space-y-4 rounded-xl border border-[var(--line)] p-4">
              <Field label="Ability name">
                <input className={inputCls} value={abilityName} onChange={(e) => setAbilityName(e.target.value)} />
              </Field>
              <div className="grid grid-cols-3 gap-4">
                <Field label="Trigger">
                  <select className={inputCls} value={trigger} onChange={(e) => setTrigger(e.target.value)}>
                    {TRIGGERS.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Cooldown (s)">
                  <input className={inputCls} value={cooldown} onChange={(e) => setCooldown(e.target.value)} />
                </Field>
                <Field label="Mana cost">
                  <input className={inputCls} value={manaCost} onChange={(e) => setManaCost(e.target.value)} />
                </Field>
              </div>
              <Field label="Description (lore lines)">
                <ListEditor values={description} onChange={setDescription} placeholder="Deals damage in a radius." />
              </Field>

              <div className="space-y-3">
                <span className="text-xs uppercase tracking-wider text-[var(--ink-dim)]">Mechanics</span>
                {mechanics.map((m) => (
                  <div key={m.id} className="rounded-lg border border-[var(--line)] p-3 space-y-2">
                    <div className="flex items-center gap-2">
                      <select
                        className={`${inputCls} flex-1`}
                        value={m.type}
                        onChange={(e) => {
                          const preset = MECHANICS.find((x) => x.id === e.target.value);
                          setMechanics(
                            mechanics.map((r) =>
                              r.id === m.id ? { ...r, type: e.target.value, params: preset?.params ?? r.params } : r
                            )
                          );
                        }}
                      >
                        {MECHANICS.map((mech) => (
                          <option key={mech.id} value={mech.id}>
                            {mech.id}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => setMechanics(mechanics.filter((r) => r.id !== m.id))}
                        className={removeBtn}
                      >
                        ✕
                      </button>
                    </div>
                    <textarea
                      className={`${inputCls} font-mono text-[12px]`}
                      rows={3}
                      value={m.params}
                      onChange={(e) =>
                        setMechanics(mechanics.map((r) => (r.id === m.id ? { ...r, params: e.target.value } : r)))
                      }
                    />
                  </div>
                ))}
                <button
                  type="button"
                  onClick={() =>
                    setMechanics([...mechanics, { id: nextId(), type: MECHANICS[0].id, params: MECHANICS[0].params }])
                  }
                  className={addBtn}
                >
                  + Add mechanic
                </button>
              </div>
            </div>
          )}
        </fieldset>
      </div>

      <div className="lg:sticky lg:top-24 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full" style={{ background: rarityColor }} />
            <span className="font-mono text-sm" style={{ color: rarityColor }}>
              {name || "New Item"}
            </span>
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={copy} className={addBtn}>
              {copied ? "Copied!" : "Copy YAML"}
            </button>
            <button type="button" onClick={download} className={addBtn}>
              Download .yml
            </button>
          </div>
        </div>
        <CodeBlock lang="items/generated.yml" content={yaml} />
        <p className="text-xs text-[var(--ink-dim)] leading-relaxed">
          Drop this file into <code className="font-mono">plugins/Valmora/items/</code> and run{" "}
          <code className="font-mono">/valmora reload</code>. Unknown stat keys or mechanic types fail the whole
          item&apos;s load — stick to the pickers on the left and it&apos;ll be valid.
        </p>
      </div>
    </div>
  );
}

const inputCls =
  "w-full rounded-md border border-[var(--line-bright)] bg-[var(--bg-inset)] px-3 py-2 text-sm text-[var(--ink)] outline-none focus:border-[var(--amber)]";
const addBtn =
  "rounded-full border border-[var(--line-bright)] px-3 py-1.5 text-xs font-medium text-[var(--ink-muted)] hover:border-[var(--amber)] hover:text-[var(--ink)] transition-colors";
const removeBtn =
  "rounded-md border border-[var(--line-bright)] px-2.5 text-sm text-[var(--ink-dim)] hover:border-red-400/60 hover:text-red-300 transition-colors";

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-xs uppercase tracking-wider text-[var(--ink-dim)]">{label}</span>
      {children}
    </label>
  );
}

function ListEditor({
  values,
  onChange,
  placeholder,
}: {
  values: string[];
  onChange: (v: string[]) => void;
  placeholder?: string;
}) {
  return (
    <div className="space-y-2">
      {values.map((v, i) => (
        <div key={i} className="flex gap-2">
          <input
            className={inputCls}
            value={v}
            placeholder={placeholder}
            onChange={(e) => onChange(values.map((x, j) => (j === i ? e.target.value : x)))}
          />
          <button type="button" onClick={() => onChange(values.filter((_, j) => j !== i))} className={removeBtn}>
            ✕
          </button>
        </div>
      ))}
      <button type="button" onClick={() => onChange([...values, ""])} className={addBtn}>
        + Add line
      </button>
    </div>
  );
}
