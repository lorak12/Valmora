"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { DOC_CATEGORIES, DOCS } from "@/lib/docsContent";

export function DocsSidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const currentSlug = pathname?.startsWith("/docs/") ? pathname.split("/")[2] : undefined;
  const isOverview = pathname === "/docs";

  return (
    <>
      {/* Small screens: a jump-to select instead of the full tree */}
      <div className="lg:hidden mb-6">
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
          {DOCS.map((d) => (
            <option key={d.slug} value={`/docs/${d.slug}`}>
              {d.title}
            </option>
          ))}
        </select>
      </div>

      {/* Large screens: the persistent doc tree */}
      <nav
        aria-label="Documentation"
        className="hidden lg:block lg:sticky lg:top-24 lg:self-start max-h-[calc(100vh-7rem)] overflow-y-auto pr-3"
      >
        <Link
          href="/docs"
          className={`mb-4 block rounded-md px-2.5 py-1.5 text-sm font-medium transition-colors ${
            isOverview
              ? "bg-[var(--bg-raised)] text-[var(--ink)]"
              : "text-[var(--ink-muted)] hover:text-[var(--ink)]"
          }`}
        >
          ← Overview
        </Link>
        {DOC_CATEGORIES.map((category) => {
          const entries = DOCS.filter((d) => d.category === category);
          if (entries.length === 0) return null;
          return (
            <div key={category} className="mb-6">
              <span className="block px-2.5 mb-1.5 font-mono text-[10px] uppercase tracking-wider text-[var(--ink-dim)]">
                {category}
              </span>
              <div className="space-y-0.5">
                {entries.map((d) => {
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
          );
        })}
      </nav>
    </>
  );
}
