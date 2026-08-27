import Link from "next/link";
import type { Metadata } from "next";
import { Badge } from "@/components/Badge";

export const metadata: Metadata = {
  title: "Generators",
  description: "Build valid Valmora config files from a form, in the browser.",
};

const GENERATORS = [
  {
    slug: "item",
    title: "Item Generator",
    body: "Compose a weapon, tool, or armor piece — stats, rarity, and a scripted ability — and get back items/*.yml.",
    status: "available" as const,
  },
  {
    slug: "mob",
    title: "Mob Generator",
    body: "Base a custom mob on a vanilla entity type, set its stat pool and loot table.",
    status: "soon" as const,
  },
  {
    slug: "gui",
    title: "GUI Generator",
    body: "Lay out INPUT/OUTPUT/DISPLAY components and wire up on-open / on-slot-update actions.",
    status: "soon" as const,
  },
  {
    slug: "recipe",
    title: "Recipe Generator",
    body: "Shaped, shapeless, or exact-slot recipes for any machine, with live ingredient validation.",
    status: "soon" as const,
  },
];

export default function GeneratorsIndex() {
  return (
    <div className="mx-auto max-w-6xl px-6 py-16">
      <span className="font-mono text-xs uppercase tracking-widest text-[var(--ink-dim)]">Generators</span>
      <h1 className="font-display text-4xl sm:text-5xl mt-3 mb-4">
        A form in, a valid config out.
      </h1>
      <p className="text-[var(--ink-muted)] max-w-2xl mb-14 leading-relaxed">
        Every generator runs entirely in your browser — nothing you type is sent
        anywhere. Fill in a form, watch the YAML build itself, copy it into your
        server&apos;s <code className="font-mono text-[13px] text-[var(--ink)]">plugins/Valmora/</code> folder.
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        {GENERATORS.map((g, i) =>
          g.status === "available" ? (
            <Link
              key={g.slug}
              href={`/generators/${g.slug}`}
              className="group animate-fade-slide-in rounded-xl border border-[var(--amber)]/40 bg-[var(--amber)]/5 p-6 transition-all duration-300 hover:border-[var(--amber)] hover:-translate-y-0.5 hover:shadow-[0_8px_24px_-12px_rgba(199,123,58,0.35)]"
              style={{ animationDelay: `${i * 70}ms` }}
            >
              <div className="flex items-center justify-between mb-2">
                <h3 className="font-display text-xl">{g.title}</h3>
                <Badge>Available</Badge>
              </div>
              <p className="text-sm text-[var(--ink-muted)] leading-relaxed">{g.body}</p>
              <span className="mt-3 inline-block text-xs text-[var(--amber-bright)] opacity-0 transition-all duration-300 group-hover:opacity-100 group-hover:translate-x-0.5">
                Open generator →
              </span>
            </Link>
          ) : (
            <div
              key={g.slug}
              className="animate-fade-slide-in rounded-xl border border-dashed border-[var(--line-bright)] p-6 opacity-60"
              style={{ animationDelay: `${i * 70}ms` }}
            >
              <div className="flex items-center justify-between mb-2">
                <h3 className="font-display text-xl">{g.title}</h3>
                <Badge tone="muted">Coming soon</Badge>
              </div>
              <p className="text-sm text-[var(--ink-muted)] leading-relaxed">{g.body}</p>
            </div>
          )
        )}
      </div>
    </div>
  );
}
