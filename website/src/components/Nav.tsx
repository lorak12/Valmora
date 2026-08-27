"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/", label: "Overview" },
  { href: "/docs", label: "Docs" },
  { href: "/generators", label: "Generators" },
  { href: "/pricing", label: "Pricing" },
];

export function Nav() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-30 border-b border-[var(--line)] bg-[var(--bg)]/85 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-6 px-6 py-4">
        <Link href="/" className="flex items-center gap-2.5 shrink-0">
          <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M12 2 L21 7 V17 L12 22 L3 17 V7 Z"
              fill="none"
              stroke="var(--amber)"
              strokeWidth="1.4"
            />
            <path d="M12 2 V22 M3 7 L21 17 M21 7 L3 17" stroke="var(--line-bright)" strokeWidth="0.8" />
            <circle cx="12" cy="12" r="2.4" fill="var(--amber)" />
          </svg>
          <span className="font-display text-lg tracking-wide">Valmora</span>
        </Link>

        <nav className="hidden sm:flex items-center gap-1 text-sm">
          {LINKS.map((l) => {
            const active = l.href === "/" ? pathname === "/" : pathname?.startsWith(l.href);
            return (
              <Link
                key={l.href}
                href={l.href}
                className={`rounded-full px-3.5 py-1.5 transition-colors ${
                  active
                    ? "bg-[var(--bg-raised)] text-[var(--ink)]"
                    : "text-[var(--ink-muted)] hover:text-[var(--ink)]"
                }`}
              >
                {l.label}
              </Link>
            );
          })}
        </nav>

        <div className="flex items-center gap-3">
          <span className="hidden md:inline-flex items-center gap-1.5 rounded-full border border-[var(--verdigris)]/40 bg-[var(--verdigris)]/10 px-3 py-1 text-xs font-medium text-[var(--verdigris)]">
            <span className="h-1.5 w-1.5 rounded-full bg-[var(--verdigris)]" />
            Free during beta
          </span>
          <a
            href="https://github.com"
            className="rounded-full border border-[var(--line-bright)] px-3.5 py-1.5 text-sm text-[var(--ink-muted)] transition-colors hover:border-[var(--amber)] hover:text-[var(--ink)]"
          >
            GitHub
          </a>
        </div>
      </div>
      <nav className="flex sm:hidden gap-1 overflow-x-auto px-6 pb-3 text-sm">
        {LINKS.map((l) => (
          <Link
            key={l.href}
            href={l.href}
            className="shrink-0 rounded-full border border-[var(--line)] px-3 py-1 text-[var(--ink-muted)]"
          >
            {l.label}
          </Link>
        ))}
      </nav>
    </header>
  );
}
