import { Badge } from "@/components/Badge";

// Single toggle for the day the plugin stops being fully free. Flip this
// (and fill in real prices) when a paid tier actually ships — everything
// else in this section is already built to render either state.
export const PRICING_ENABLED = false;

const FREE_FEATURES = [
  "The full plugin — every module, every mechanic",
  "All shipped items, mobs, GUIs, and recipes",
  "The scripting DSL, with no feature gate",
  "Community support via GitHub issues",
];

const FUTURE_TIERS = [
  {
    name: "Supporter",
    blurb: "For admins who want to fund development directly.",
    ideas: ["Priority issue triage", "A supporter badge in the community", "Early access to new modules"],
  },
  {
    name: "Managed",
    blurb: "For networks that would rather not run their own server.",
    ideas: ["Hosted Valmora instances", "Guided setup and migration", "SLA-backed support"],
  },
];

export function PricingSection({ compact = false }: { compact?: boolean }) {
  return (
    <section>
      <div className="grid gap-6 lg:grid-cols-[1.1fr_1fr]">
        <div className="rounded-2xl border border-[var(--verdigris)]/35 bg-gradient-to-br from-[var(--verdigris)]/10 to-transparent p-8">
          <Badge tone="verdigris">Current — free</Badge>
          <h3 className="font-display text-3xl mt-4 mb-1">$0, no tiers</h3>
          <p className="text-[var(--ink-muted)] mb-6 max-w-md">
            Valmora is free and open during its beta. Nothing is held back behind a
            paywall — download it, self-host it, modify it under AGPL-3.0.
          </p>
          <ul className="space-y-2.5 text-sm">
            {FREE_FEATURES.map((f) => (
              <li key={f} className="flex items-start gap-2.5">
                <svg width="16" height="16" viewBox="0 0 16 16" className="mt-0.5 shrink-0">
                  <path d="M3 8.5 6.5 12 13 4.5" stroke="var(--verdigris)" strokeWidth="1.6" fill="none" />
                </svg>
                <span className="text-[var(--ink)]/90">{f}</span>
              </li>
            ))}
          </ul>
        </div>

        {!compact && (
          <div className="rounded-2xl border border-dashed border-[var(--line-bright)] p-8">
            <Badge tone="muted">Reserved for later</Badge>
            <h3 className="font-display text-2xl mt-4 mb-1 text-[var(--ink-muted)]">
              If that ever changes
            </h3>
            <p className="text-sm text-[var(--ink-dim)] mb-6 max-w-md">
              These aren&apos;t live, priced, or promised — just the shape we&apos;d grow into
              without breaking what&apos;s already free. Nothing here is for sale today.
            </p>
            <div className="space-y-5">
              {FUTURE_TIERS.map((t) => (
                <div key={t.name} className="rounded-xl border border-[var(--line)] p-4 opacity-70">
                  <div className="flex items-baseline justify-between mb-1">
                    <span className="font-display text-lg">{t.name}</span>
                    <span className="font-mono text-xs text-[var(--ink-dim)]">not available</span>
                  </div>
                  <p className="text-xs text-[var(--ink-muted)] mb-2">{t.blurb}</p>
                  <ul className="text-xs text-[var(--ink-dim)] list-disc list-inside space-y-0.5">
                    {t.ideas.map((i) => (
                      <li key={i}>{i}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
      {!PRICING_ENABLED && (
        <p className="mt-6 text-xs text-[var(--ink-dim)] font-mono">
          pricing.enabled = false — everything above ships free until this flips.
        </p>
      )}
    </section>
  );
}
