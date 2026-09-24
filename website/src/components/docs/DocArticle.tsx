"use client";

import { useEffect, useRef, useState } from "react";

type Heading = { id: string; text: string; level: 2 | 3 };

/**
 * Wraps a rendered MDX page: collects its h2/h3 headings (ids come from rehype-slug) into the
 * "On this page" rail, and highlights whichever one the reader is currently in.
 */
export function DocArticle({ children }: { children: React.ReactNode }) {
  const ref = useRef<HTMLElement>(null);
  const [headings, setHeadings] = useState<Heading[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);

  useEffect(() => {
    const els = Array.from(ref.current?.querySelectorAll<HTMLHeadingElement>("h2[id], h3[id]") ?? []);
    setHeadings(els.map((el) => ({ id: el.id, text: el.textContent ?? "", level: el.tagName === "H2" ? 2 : 3 })));

    let raf = 0;
    function update() {
      raf = 0;
      const line = window.innerHeight * 0.3;
      let current: string | null = els[0]?.id ?? null;
      for (const el of els) if (el.getBoundingClientRect().top <= line) current = el.id;
      setActiveId(current);
    }
    function onScroll() {
      if (!raf) raf = requestAnimationFrame(update);
    }
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      if (raf) cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <div className="grid gap-10 lg:grid-cols-[1fr_200px] xl:grid-cols-[1fr_230px]">
      <article ref={ref} className="doc-prose min-w-0 max-w-3xl">
        {children}
      </article>

      {headings.length > 1 && (
        <nav aria-label="On this page" className="hidden lg:block">
          <div className="sticky top-24 max-h-[calc(100vh-8rem)] overflow-y-auto space-y-1">
            <span className="block font-mono text-[11px] uppercase tracking-wider text-[var(--ink-dim)] mb-2">
              On this page
            </span>
            {headings.map((h) => (
              <a
                key={h.id}
                href={`#${h.id}`}
                className={`block border-l-2 py-1 text-sm transition-colors ${h.level === 3 ? "pl-6" : "pl-3"} ${
                  activeId === h.id
                    ? "border-[var(--amber)] text-[var(--ink)]"
                    : "border-[var(--line)] text-[var(--ink-dim)] hover:text-[var(--ink-muted)]"
                }`}
              >
                {h.text}
              </a>
            ))}
          </div>
        </nav>
      )}
    </div>
  );
}
