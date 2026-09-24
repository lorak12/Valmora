import reference from "@/generated/reference.json";

// src/generated/reference.json is written by scripts/extract-reference.mjs (prebuild/predev) from
// the plugin's own source, merged with the descriptions in src/content/reference/*.yml.

type Kind =
  | "events"
  | "mechanics"
  | "itemTriggers"
  | "mobTriggers"
  | "enchantTriggers"
  | "objectives"
  | "conditions"
  | "variables"
  | "commands"
  | "stats"
  | "rarities"
  | "itemTypes"
  | "mobCategories"
  | "damageTypes"
  | "shippedFiles";

type Desc = Record<string, Record<string, unknown>>;
const descriptions = reference.descriptions as unknown as Record<string, Desc>;

/** Renders `code` and **bold** inside a reference cell. */
function Inline({ text }: { text: string }) {
  const parts = text.split(/(`[^`]+`|\*\*[^*]+\*\*)/g);
  return (
    <>
      {parts.map((p, i) =>
        p.startsWith("`") ? (
          <code key={i}>{p.slice(1, -1)}</code>
        ) : p.startsWith("**") ? (
          <strong key={i}>{p.slice(2, -2)}</strong>
        ) : (
          <span key={i}>{p}</span>
        )
      )}
    </>
  );
}

function Table({ headers, rows }: { headers: string[]; rows: React.ReactNode[][] }) {
  return (
    <div className="my-5 overflow-x-auto rounded-lg border border-[var(--line)]">
      <table>
        <thead>
          <tr>
            {headers.map((h) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {r.map((c, j) => (
                <td key={j}>{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const str = (v: unknown) => (typeof v === "string" ? v : "");

/**
 * A reference table generated from the plugin's source. `group` filters events by their group
 * (core, economy, combat, gui, world); `prefix` filters shipped files by folder.
 */
export function RefTable({ kind, group, prefix }: { kind: Kind; group?: string; prefix?: string }) {
  switch (kind) {
    case "events": {
      const d = descriptions.events;
      const rows = reference.events
        .filter((e) => !group || d[e.id]?.group === group)
        .map((e) => [
          <code key="n">{e.id}</code>,
          <code key="s">{str(d[e.id]?.syntax)}</code>,
          <Inline key="d" text={str(d[e.id]?.description)} />,
        ]);
      return <Table headers={["Event", "Syntax", "What it does"]} rows={rows} />;
    }
    case "mechanics": {
      const d = descriptions.mechanics;
      return (
        <Table
          headers={["type", "What it does", "params"]}
          rows={reference.mechanics.map((id) => [
            <code key="n">{id}</code>,
            <Inline key="d" text={str(d[id]?.description)} />,
            <Inline key="p" text={str(d[id]?.params)} />,
          ])}
        />
      );
    }
    case "itemTriggers":
    case "mobTriggers":
    case "enchantTriggers": {
      const d = descriptions[kind];
      return (
        <Table
          headers={["Trigger", "Fires when"]}
          rows={(reference[kind] as string[]).map((id) => [<code key="n">{id}</code>, <Inline key="d" text={str(d[id]?.description)} />])}
        />
      );
    }
    case "objectives": {
      const d = descriptions.objectives;
      return (
        <Table
          headers={["type", "target", "amount"]}
          rows={reference.objectives.map((id) => [
            <code key="n">{id}</code>,
            <Inline key="t" text={str(d[id]?.target)} />,
            <Inline key="a" text={str(d[id]?.amount)} />,
          ])}
        />
      );
    }
    case "conditions": {
      const d = descriptions.conditions;
      return (
        <Table
          headers={["Keyword", "Syntax", "True when"]}
          rows={reference.conditions.map((id) => [
            <code key="n">{id}</code>,
            <code key="s">{str(d[id]?.syntax)}</code>,
            <Inline key="d" text={str(d[id]?.description)} />,
          ])}
        />
      );
    }
    case "variables": {
      const d = descriptions.namespaces;
      return (
        <Table
          headers={["Namespace", "Available", "Paths"]}
          rows={reference.namespaces.map((id) => [
            <code key="n">{id}</code>,
            <Inline key="w" text={str(d[id]?.where)} />,
            <ul key="p" className="space-y-1">
              {((d[id]?.paths as string[]) ?? []).map((p) => (
                <li key={p}>
                  <Inline text={p} />
                </li>
              ))}
            </ul>,
          ])}
        />
      );
    }
    case "commands": {
      const d = descriptions.commands;
      return (
        <Table
          headers={["Command", "Who", "Subcommands"]}
          rows={reference.commands.map((c) => [
            <span key="n">
              <code>/{c.id}</code>
              {c.aliases.length > 0 && <span className="block text-xs text-[var(--ink-dim)]">alias /{c.aliases.join(", /")}</span>}
            </span>,
            <span key="w">
              {str(d[c.id]?.who)}
              {d[c.id]?.key ? (
                <span className="block text-xs text-[var(--ink-dim)]">
                  config key: <code>{str(d[c.id]?.key)}</code>
                </span>
              ) : null}
            </span>,
            <ul key="s" className="space-y-1">
              {((d[c.id]?.subcommands as string[]) ?? []).map((s) => (
                <li key={s}>
                  <Inline text={s.includes(" — ") ? `\`${s.split(" — ")[0]}\` — ${s.split(" — ").slice(1).join(" — ")}` : `\`${s}\``} />
                </li>
              ))}
            </ul>,
          ])}
        />
      );
    }
    case "stats":
      return (
        <Table
          headers={["Stat id", "Name", "Base value", "Cap", "Description"]}
          rows={reference.stats.map((s) => [
            <code key="i">{s.id}</code>,
            s.name,
            String(s.default),
            s.max == null ? "—" : String(s.max),
            s.description,
          ])}
        />
      );
    case "rarities":
      return (
        <Table
          headers={["Rarity", "Rank", "Color", "forge_cost"]}
          rows={(reference.rarities as Record<string, unknown>[]).map((r) => [
            <code key="i">{String(r.id).toUpperCase()}</code>,
            String(r.rank ?? ""),
            <code key="c">{String(r.color ?? "")}</code>,
            String(r.forge_cost ?? "—"),
          ])}
        />
      );
    case "itemTypes":
      return (
        <p className="flex flex-wrap gap-2">
          {[...reference.itemTypes.builtIn, ...reference.itemTypes.extra].map((t) => (
            <code key={t}>{t}</code>
          ))}
        </p>
      );
    case "mobCategories":
      return (
        <p className="flex flex-wrap gap-2">
          {reference.mobCategories.map((t) => (
            <code key={t}>{t}</code>
          ))}
        </p>
      );
    case "damageTypes":
      return (
        <Table
          headers={["Damage type", "Ignores defense", "Color"]}
          rows={(reference.damageTypes as Record<string, unknown>[]).map((t) => [
            <code key="i">{String(t.id).toUpperCase()}</code>,
            t["ignores-defense"] ? "yes" : "no",
            <code key="c">{String(t.color ?? "")}</code>,
          ])}
        />
      );
    case "shippedFiles": {
      const files = reference.shippedFiles.filter((f) => (prefix ? f.startsWith(prefix) : !f.includes("/")));
      return (
        <ul className="columns-1 sm:columns-2 gap-8">
          {files.map((f) => (
            <li key={f}>
              <code>{f}</code>
            </li>
          ))}
        </ul>
      );
    }
  }
}
