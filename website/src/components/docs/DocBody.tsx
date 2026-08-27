"use client";

import { useEffect, useRef, useState } from "react";
import { CodeBlock } from "@/components/CodeBlock";
import type { DocEntry } from "@/lib/docsContent";

export function DocBody({ doc }: { doc: DocEntry }) {
  const sectionRefs = useRef<(HTMLElement | null)[]>([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [visible, setVisible] = useState<boolean[]>(() => doc.sections.map(() => false));

  // Scrollspy: which section is nearest the "reading line" drives both the
  // on-page TOC highlight and the live-state panel on the right.
  useEffect(() => {
    let raf = 0;
    function update() {
      raf = 0;
      const line = window.innerHeight * 0.32;
      let idx = 0;
      sectionRefs.current.forEach((el, i) => {
        if (el && el.getBoundingClientRect().top <= line) idx = i;
      });
      setActiveIndex(idx);
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

  // Reveal-on-scroll: each section fades/slides in once, the first time it
  // enters the viewport.
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const i = sectionRefs.current.indexOf(entry.target as HTMLElement);
          if (i === -1) return;
          setVisible((prev) => (prev[i] ? prev : prev.map((v, j) => (j === i ? true : v))));
          observer.unobserve(entry.target);
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -10% 0px" }
    );
    sectionRefs.current.forEach((el) => el && observer.observe(el));
    return () => observer.disconnect();
  }, [doc.slug]);

  function jumpTo(i: number) {
    sectionRefs.current[i]?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  return (
    <div className="grid gap-10 lg:grid-cols-[1fr_220px] xl:grid-cols-[1fr_240px]">
      {/* Content */}
      <div className="min-w-0">
        {/* Mobile-only jump row — the vertical version lives in the right rail on large screens */}
        {doc.sections.length > 1 && (
          <nav aria-label="On this page" className="mb-10 flex flex-wrap gap-2 lg:hidden">
            {doc.sections.map((s, i) => (
              <button
                key={s.heading}
                onClick={() => jumpTo(i)}
                className={`rounded-full border px-3 py-1 font-mono text-xs transition-colors ${
                  activeIndex === i
                    ? "border-[var(--amber)] bg-[var(--amber)]/10 text-[var(--amber-bright)]"
                    : "border-[var(--line)] text-[var(--ink-dim)] hover:border-[var(--line-bright)] hover:text-[var(--ink-muted)]"
                }`}
              >
                {i + 1}. {s.heading}
              </button>
            ))}
          </nav>
        )}
        <div className="space-y-12">
          {doc.sections.map((s, i) => (
          <section
            key={s.heading}
            ref={(el) => {
              sectionRefs.current[i] = el;
            }}
            className={`reveal ${visible[i] ? "is-visible" : ""} scroll-mt-24`}
          >
            <div className="flex items-center gap-3 mb-3">
              <span className="font-mono text-xs text-[var(--ink-dim)]">§{i + 1}</span>
              <h2 className="font-display text-2xl">{s.heading}</h2>
            </div>
            <div className="space-y-3 text-[var(--ink)]/90 leading-relaxed">
              {(s.body ?? []).map((p, j) => (
                <p key={j}>{p}</p>
              ))}
            </div>
            {s.list && (
              <ul className="mt-4 flex flex-wrap gap-2">
                {s.list.map((item) => (
                  <li
                    key={item}
                    className="rounded-full border border-[var(--line)] px-3 py-1 font-mono text-xs text-[var(--ink-muted)]"
                  >
                    {item}
                  </li>
                ))}
              </ul>
            )}
            {s.table && (
              <div className="mt-4 overflow-x-auto rounded-lg border border-[var(--line)]">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-[var(--line)] bg-[var(--bg-raised)]">
                      {s.table.headers.map((h) => (
                        <th
                          key={h}
                          className="px-3 py-2 text-left font-mono text-[11px] uppercase tracking-wider text-[var(--ink-dim)]"
                        >
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {s.table.rows.map((row, ri) => (
                      <tr key={ri} className="border-b border-[var(--line)] last:border-0">
                        {row.map((cell, ci) => (
                          <td key={ci} className="px-3 py-2 text-[var(--ink)]/90 align-top">
                            {cell}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {s.code && (
              <div className="mt-4">
                <CodeBlock lang={s.code.lang} content={s.code.content} />
              </div>
            )}
          </section>
          ))}
        </div>
      </div>

      {/* On-page nav — reserved right rail; free for other things later */}
      {doc.sections.length > 1 && (
        <nav aria-label="On this page" className="hidden lg:block">
          <div className="sticky top-24 space-y-1">
            <span className="block font-mono text-[11px] uppercase tracking-wider text-[var(--ink-dim)] mb-2">
              On this page
            </span>
            {doc.sections.map((s, i) => (
              <button
                key={s.heading}
                onClick={() => jumpTo(i)}
                className={`block w-full text-left border-l-2 pl-3 py-1 text-sm transition-colors ${
                  activeIndex === i
                    ? "border-[var(--amber)] text-[var(--ink)]"
                    : "border-[var(--line)] text-[var(--ink-dim)] hover:text-[var(--ink-muted)]"
                }`}
              >
                {s.heading}
              </button>
            ))}
          </div>
        </nav>
      )}
    </div>
  );
}
