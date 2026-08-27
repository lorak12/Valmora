export function Badge({
  children,
  tone = "amber",
}: {
  children: React.ReactNode;
  tone?: "amber" | "verdigris" | "gold" | "muted";
}) {
  const tones: Record<string, string> = {
    amber: "border-[var(--amber)]/40 bg-[var(--amber)]/10 text-[var(--amber-bright)]",
    verdigris: "border-[var(--verdigris)]/40 bg-[var(--verdigris)]/10 text-[var(--verdigris)]",
    gold: "border-[var(--gold)]/40 bg-[var(--gold)]/10 text-[var(--gold)]",
    muted: "border-[var(--line-bright)] bg-[var(--bg-raised)] text-[var(--ink-muted)]",
  };
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium uppercase tracking-wider ${tones[tone]}`}
    >
      {children}
    </span>
  );
}
