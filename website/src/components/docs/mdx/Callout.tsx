const TONES = {
  note: { label: "Note", cls: "border-[var(--line-bright)] bg-[var(--bg-raised)]", accent: "text-[var(--ink-muted)]" },
  tip: { label: "Tip", cls: "border-[var(--verdigris)]/50 bg-[var(--verdigris)]/10", accent: "text-[var(--verdigris)]" },
  warning: { label: "Watch out", cls: "border-[var(--amber)]/60 bg-[var(--amber)]/10", accent: "text-[var(--amber-bright)]" },
} as const;

/** A highlighted aside. `type` picks the tone; `title` overrides the default label. */
export function Callout({
  type = "note",
  title,
  children,
}: {
  type?: keyof typeof TONES;
  title?: string;
  children: React.ReactNode;
}) {
  const tone = TONES[type];
  return (
    <aside className={`callout my-6 rounded-lg border-l-4 border px-4 py-3 ${tone.cls}`}>
      <span className={`block font-mono text-[11px] uppercase tracking-wider mb-1 ${tone.accent}`}>
        {title ?? tone.label}
      </span>
      <div className="callout-body">{children}</div>
    </aside>
  );
}
