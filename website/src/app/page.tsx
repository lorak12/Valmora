import Link from "next/link";
import { ModulePipeline } from "@/components/ModulePipeline";
import { Badge } from "@/components/Badge";
import { CodeBlock } from "@/components/CodeBlock";
import { PricingSection } from "@/components/PricingSection";
import { DOCS } from "@/lib/docsContent";
import { HERO_MODULES, MODULE_CHAIN } from "@/lib/modules";

const PILLARS = [
  {
    title: "Items & Abilities",
    body: "Stats, rarity, and scripted abilities on any weapon, tool, or armor piece — all authored in YAML.",
    href: "/docs/items",
  },
  {
    title: "Mobs & Combat",
    body: "Custom mobs share the same stat and ability engine as items — a boss fight is data, not a rewrite.",
    href: "/docs/mobs",
  },
  {
    title: "GUIs & Machines",
    body: "Data-driven inventory screens with typed slots, event blocks, and a three-tier crafting engine.",
    href: "/docs/gui-machines",
  },
  {
    title: "Scripting DSL",
    body: "A compact event language for rewards and effects, with live variables reaching into every module.",
    href: "/docs/scripting",
  },
  {
    title: "Economy & Progression",
    body: "Coins, skills, and reforging wired together so a player&apos;s build actually changes how numbers resolve.",
    href: "/docs/economy",
  },
  {
    title: "Zones, NPCs & Quests",
    body: "Named regions, dialogue-driven NPCs, and quest chains that reward through the same systems above.",
    href: "/docs/zones",
  },
];

export default function Home() {
  return (
    <div className="mx-auto max-w-6xl px-6">
      {/* Hero */}
      <section className="pt-16 pb-14 sm:pt-24 sm:pb-20">
        <Badge>Paper 1.21 · Java 21</Badge>
        <h1 className="font-display text-5xl sm:text-6xl md:text-7xl leading-[1.03] mt-6 max-w-3xl">
          A modular RPG engine, wired module by module.
        </h1>
        <p className="mt-6 max-w-xl text-lg text-[var(--ink-muted)] leading-relaxed">
          Valmora turns items, mobs, combat, economy, and GUIs into one coherent
          system for Paper servers — every piece hot-reloadable, every behaviour
          authored in YAML instead of a fork.
        </p>
        <div className="mt-8 flex flex-wrap items-center gap-4">
          <Link
            href="/docs"
            className="rounded-full bg-[var(--amber)] px-6 py-3 text-sm font-semibold text-[#14110f] transition-colors hover:bg-[var(--amber-bright)]"
          >
            Read the docs
          </Link>
          <Link
            href="/generators"
            className="rounded-full border border-[var(--line-bright)] px-6 py-3 text-sm font-semibold transition-colors hover:border-[var(--amber)]"
          >
            Open the generators
          </Link>
        </div>

        <div className="mt-16">
          <div className="mb-3 font-mono text-xs uppercase tracking-widest text-[var(--ink-dim)]">
            Valmora.onEnable() — modules light up in dependency order
          </div>
          <ModulePipeline ids={HERO_MODULES} compact />
        </div>
      </section>

      {/* Pillars */}
      <section className="py-16 border-t border-[var(--line)]">
        <h2 className="font-display text-3xl sm:text-4xl mb-3">Built as modules, not a monolith.</h2>
        <p className="text-[var(--ink-muted)] max-w-2xl mb-10">
          Every system below is its own reloadable module with its own YAML surface —
          disable, reload, or replace one without touching the rest.
        </p>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {PILLARS.map((p) => (
            <Link
              key={p.title}
              href={p.href}
              className="group rounded-xl border border-[var(--line)] bg-[var(--bg-raised)] p-6 transition-colors hover:border-[var(--amber)]/50"
            >
              <h3 className="font-display text-xl mb-2">{p.title}</h3>
              <p className="text-sm text-[var(--ink-muted)] leading-relaxed mb-3">{p.body}</p>
              <span className="text-sm text-[var(--amber-bright)] opacity-0 group-hover:opacity-100 transition-opacity">
                Read more →
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* Full pipeline */}
      <section className="py-16 border-t border-[var(--line)]">
        <h2 className="font-display text-3xl sm:text-4xl mb-3">The real load order.</h2>
        <p className="text-[var(--ink-muted)] max-w-2xl mb-8">
          This is the actual 28-module registration chain from{" "}
          <code className="font-mono text-[13px] text-[var(--ink)]">Valmora.java</code> —
          not a marketing diagram. Later modules can depend on earlier ones; never the
          reverse.
        </p>
        <div className="rounded-2xl border border-[var(--line)] bg-[var(--bg-inset)] p-6">
          <ModulePipeline ids={MODULE_CHAIN} />
        </div>
      </section>

      {/* Docs teaser */}
      <section className="py-16 border-t border-[var(--line)]">
        <div className="flex items-baseline justify-between mb-8">
          <h2 className="font-display text-3xl sm:text-4xl">Docs that match the code.</h2>
          <Link href="/docs" className="text-sm text-[var(--amber-bright)] whitespace-nowrap">
            Browse all docs →
          </Link>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          {DOCS.slice(0, 3).map((d) => (
            <Link
              key={d.slug}
              href={`/docs/${d.slug}`}
              className="rounded-xl border border-[var(--line)] p-6 hover:border-[var(--amber)]/50 transition-colors"
            >
              <span className="font-mono text-xs uppercase tracking-wider text-[var(--ink-dim)]">
                {d.category}
              </span>
              <h3 className="font-display text-xl mt-2 mb-2">{d.title}</h3>
              <p className="text-sm text-[var(--ink-muted)] leading-relaxed">{d.summary}</p>
            </Link>
          ))}
        </div>
      </section>

      {/* Generators teaser */}
      <section className="py-16 border-t border-[var(--line)]">
        <div className="grid gap-10 lg:grid-cols-2 items-center">
          <div>
            <Badge tone="gold">In the browser, no install</Badge>
            <h2 className="font-display text-3xl sm:text-4xl mt-4 mb-3">
              Stop hand-indenting YAML.
            </h2>
            <p className="text-[var(--ink-muted)] leading-relaxed mb-6 max-w-md">
              The generators turn a form into a valid config file for Valmora&apos;s real
              schema — starting with items, and growing to mobs, GUIs, and recipes.
            </p>
            <Link
              href="/generators/item"
              className="inline-flex rounded-full bg-[var(--amber)] px-6 py-3 text-sm font-semibold text-[#14110f] hover:bg-[var(--amber-bright)]"
            >
              Try the item generator
            </Link>
          </div>
          <CodeBlock
            lang="yaml — generated"
            content={`flame_wave:
  name: "Flame Wave"
  material: DIAMOND_SWORD
  rarity: EPIC
  item-type: SWORD
  stats:
    damage: 30.0
    strength: 5.0
  abilities:
    flame_wave:
      name: "Flame Wave"
      trigger: RIGHT_CLICK
      cooldown: 5.0
      mechanics:
        - type: damage
          params:
            damage: 10
            damage-type: MAGIC
            target: "@target"`}
          />
        </div>
      </section>

      {/* Pricing teaser */}
      <section className="py-16 border-t border-[var(--line)]">
        <div className="flex items-baseline justify-between mb-8">
          <h2 className="font-display text-3xl sm:text-4xl">Free today. Built to stay honest later.</h2>
          <Link href="/pricing" className="text-sm text-[var(--amber-bright)] whitespace-nowrap">
            Full pricing page →
          </Link>
        </div>
        <PricingSection compact />
      </section>
    </div>
  );
}
