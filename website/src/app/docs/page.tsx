import Link from "next/link";
import type { Metadata } from "next";
import { DOC_CATEGORIES, DOCS } from "@/lib/docsContent";

export const metadata: Metadata = {
  title: "Docs",
  description: "Every Valmora module, described for the admin configuring it.",
};

export default function DocsIndex() {
  return (
    <div>
      <span className="font-mono text-xs uppercase tracking-widest text-[var(--ink-dim)]">
        Documentation
      </span>
      <h1 className="font-display text-4xl sm:text-5xl mt-3 mb-4">
        One page per system, written for the admin configuring it.
      </h1>
      <p className="text-[var(--ink-muted)] max-w-2xl mb-14">
        This mirrors the module structure inside the plugin itself — if it&apos;s a
        reloadable module, it has a page here.
      </p>

      {DOC_CATEGORIES.map((category) => {
        const entries = DOCS.filter((d) => d.category === category);
        if (entries.length === 0) return null;
        return (
          <div key={category} className="mb-12">
            <h2 className="font-display text-2xl mb-5 text-[var(--ink)]">{category}</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {entries.map((d, i) => (
                <Link
                  key={d.slug}
                  href={`/docs/${d.slug}`}
                  className="group relative overflow-hidden rounded-xl border border-[var(--line)] p-6 transition-all duration-300 hover:border-[var(--amber)]/50 hover:-translate-y-0.5 hover:shadow-[0_8px_24px_-12px_rgba(199,123,58,0.35)] animate-fade-slide-in"
                  style={{ animationDelay: `${i * 70}ms` }}
                >
                  <span className="pointer-events-none absolute inset-x-0 top-0 h-px origin-left scale-x-0 bg-gradient-to-r from-[var(--amber)] to-transparent transition-transform duration-300 group-hover:scale-x-100" />
                  <h3 className="font-display text-lg mb-2">{d.title}</h3>
                  <p className="text-sm text-[var(--ink-muted)] leading-relaxed">{d.summary}</p>
                  <span className="mt-3 inline-block text-xs text-[var(--amber-bright)] opacity-0 transition-all duration-300 group-hover:opacity-100 group-hover:translate-x-0.5">
                    Read more →
                  </span>
                </Link>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
}
