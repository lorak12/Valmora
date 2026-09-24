"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import type { DocMeta, SearchEntry } from "@/lib/docs";

type NavSection = { title: string; docs: DocMeta[] };

function score(entry: SearchEntry, terms: string[]): number {
  let total = 0;
  for (const term of terms) {
    const t = term.toLowerCase();
    let s = 0;
    if (entry.title.toLowerCase().includes(t)) s += 10;
    if (entry.headings.some((h) => h.toLowerCase().includes(t))) s += 5;
    if (entry.summary.toLowerCase().includes(t)) s += 3;
    if (entry.text.toLowerCase().includes(t)) s += 1;
    if (s === 0) return 0; // every term must match somewhere
    total += s;
  }
  return total;
}

export function DocsSidebar({ nav, index }: { nav: NavSection[]; index: SearchEntry[] }) {
  const pathname = usePathname();
  const router = useRouter();
  const [query, setQuery] = useState("");
  const currentSlug = pathname?.startsWith("/docs/") ? pathname.split("/")[2] : undefined;
  const isOverview = pathname === "/docs";

  const results = useMemo(() => {
    const terms = query.trim().split(/\s+/).filter(Boolean);
    if (terms.length === 0) return null;
    return index
      .map((e) => ({ e, s: score(e, terms) }))
      .filter((r) => r.s > 0)
      .sort((a, b) => b.s - a.s)
      .slice(0, 12)
      .map((r) => r.e);
  }, [query, index]);

  const search = (
    <div className="mb-5">
      <input
        type="search"
        aria-label="Search the docs"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && results?.[0]) {
            router.push(`/docs/${results[0].slug}`);
            setQuery("");
          }
        }}
        placeholder="Search docs…"
        className="w-full rounded-md border border-[var(--line-bright)] bg-[var(--bg-inset)] px-3 py-2 text-sm text-[var(--ink)] placeholder:text-[var(--ink-dim)]"
      />
      {results && (
        <div className="mt-2 space-y-0.5">
          {results.length === 0 && <p className="px-2.5 py-1.5 text-sm text-[var(--ink-dim)]">No matches.</p>}
          {results.map((r) => (
            <Link
              key={r.slug}
              href={`/docs/${r.slug}`}
              onClick={() => setQuery("")}
              className="block rounded-md px-2.5 py-1.5 hover:bg-[var(--bg-raised)]"
            >
              <span className="block text-sm text-[var(--ink)]">{r.title}</span>
              <span className="block text-xs text-[var(--ink-dim)]">{r.section}</span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );

  return (
    <>
      {/* Small screens: search + a jump-to select instead of the full tree */}
      <div className="lg:hidden mb-6">
        {search}
        <label className="sr-only" htmlFor="docs-jump">
          Jump to a doc page
        </label>
        <select
          id="docs-jump"
          value={pathname ?? "/docs"}
          onChange={(e) => router.push(e.target.value)}
          className="w-full rounded-md border border-[var(--line-bright)] bg-[var(--bg-inset)] px-3 py-2 text-sm text-[var(--ink)]"
        >
          <option value="/docs">Overview</option>
          {nav.map((section) => (
            <optgroup key={section.title} label={section.title}>
              {section.docs.map((d) => (
                <option key={d.slug} value={`/docs/${d.slug}`}>
                  {d.title}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
      </div>

      {/* Large screens: the persistent doc tree */}
      <nav
        aria-label="Documentation"
        className="hidden lg:block lg:sticky lg:top-24 lg:self-start max-h-[calc(100vh-7rem)] overflow-y-auto pr-3"
      >
        {search}
        <Link
          href="/docs"
          className={`mb-4 block rounded-md px-2.5 py-1.5 text-sm font-medium transition-colors ${
            isOverview ? "bg-[var(--bg-raised)] text-[var(--ink)]" : "text-[var(--ink-muted)] hover:text-[var(--ink)]"
          }`}
        >
          ← Overview
        </Link>
        {nav.map((section) => (
          <div key={section.title} className="mb-6">
            <span className="block px-2.5 mb-1.5 font-mono text-[10px] uppercase tracking-wider text-[var(--ink-dim)]">
              {section.title}
            </span>
            <div className="space-y-0.5">
              {section.docs.map((d) => {
                const active = d.slug === currentSlug;
                return (
                  <Link
                    key={d.slug}
                    href={`/docs/${d.slug}`}
                    className={`block rounded-md border-l-2 px-2.5 py-1.5 text-sm transition-colors ${
                      active
                        ? "border-[var(--amber)] bg-[var(--amber)]/10 text-[var(--ink)]"
                        : "border-transparent text-[var(--ink-muted)] hover:border-[var(--line-bright)] hover:text-[var(--ink)]"
                    }`}
                  >
                    {d.title}
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>
    </>
  );
}
