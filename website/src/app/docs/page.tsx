import Link from "next/link";
import type { Metadata } from "next";
import { getDocNav } from "@/lib/docs";

export const metadata: Metadata = {
  title: "Docs",
  description: "Everything you can build with Valmora in YAML: tutorials, per-system references, scripting, and the Java API.",
};

export default function DocsIndex() {
  const nav = getDocNav();
  return (
    <div>
      <span className="font-mono text-xs uppercase tracking-widest text-[var(--ink-dim)]">Documentation</span>
      <h1 className="font-display text-4xl sm:text-5xl mt-3 mb-4">Build an RPG server without writing Java.</h1>
      <p className="text-[var(--ink-muted)] max-w-2xl mb-6">
        New here? Start with <Link href="/docs/installation" className="text-[var(--amber-bright)] underline underline-offset-4">Installation</Link>,
        then work through the tutorials in order. Every system page after that follows the same shape: what it
        does, where its files live, a working example, every field, commands, and common mistakes.
      </p>
      <p className="text-[var(--ink-muted)] max-w-2xl mb-14">
        Writing an addon plugin instead? Jump to the{" "}
        <Link href="/docs/developer-api" className="text-[var(--amber-bright)] underline underline-offset-4">
          Developer API
        </Link>
        .
      </p>

      {nav.map((section) => (
        <div key={section.title} className="mb-12">
          <h2 className="font-display text-2xl mb-5 text-[var(--ink)]">{section.title}</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {section.docs.map((d, i) => (
              <Link
                key={d.slug}
                href={`/docs/${d.slug}`}
                className="group relative overflow-hidden rounded-xl border border-[var(--line)] p-6 transition-all duration-300 hover:border-[var(--amber)]/50 hover:-translate-y-0.5 hover:shadow-[0_8px_24px_-12px_rgba(199,123,58,0.35)] animate-fade-slide-in"
                style={{ animationDelay: `${i * 50}ms` }}
              >
                <span className="pointer-events-none absolute inset-x-0 top-0 h-px origin-left scale-x-0 bg-gradient-to-r from-[var(--amber)] to-transparent transition-transform duration-300 group-hover:scale-x-100" />
                <h3 className="font-display text-lg mb-2">{d.title}</h3>
                <p className="text-sm text-[var(--ink-muted)] leading-relaxed">{d.summary}</p>
              </Link>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
