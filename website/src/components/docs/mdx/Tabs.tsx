"use client";

import { Children, isValidElement, useState } from "react";

/** One tab of a <Tabs> group; only its `label` matters to the parent. */
export function Tab({ children }: { label: string; children: React.ReactNode }) {
  return <>{children}</>;
}

/** Switchable panels, e.g. SHAPED vs SHAPELESS recipe examples. Children must be <Tab label="…">. */
export function Tabs({ children }: { children: React.ReactNode }) {
  const tabs = Children.toArray(children).filter(isValidElement) as React.ReactElement<{
    label: string;
    children: React.ReactNode;
  }>[];
  const [active, setActive] = useState(0);

  return (
    <div className="my-6">
      <div role="tablist" className="flex flex-wrap gap-1 border-b border-[var(--line)]">
        {tabs.map((t, i) => (
          <button
            key={t.props.label}
            role="tab"
            aria-selected={active === i}
            onClick={() => setActive(i)}
            className={`-mb-px border-b-2 px-3 py-1.5 font-mono text-xs transition-colors ${
              active === i
                ? "border-[var(--amber)] text-[var(--ink)]"
                : "border-transparent text-[var(--ink-dim)] hover:text-[var(--ink-muted)]"
            }`}
          >
            {t.props.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" className="pt-4">
        {tabs[active]}
      </div>
    </div>
  );
}
