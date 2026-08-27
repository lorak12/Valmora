import type { Metadata } from "next";
import { PricingSection } from "@/components/PricingSection";

export const metadata: Metadata = {
  title: "Pricing",
  description: "Valmora is free during its beta, with an honest place-holder for what comes after.",
};

export default function PricingPage() {
  return (
    <div className="mx-auto max-w-5xl px-6 py-16">
      <span className="font-mono text-xs uppercase tracking-widest text-[var(--ink-dim)]">Pricing</span>
      <h1 className="font-display text-4xl sm:text-5xl mt-3 mb-4">Free now. Plainly labeled if that changes.</h1>
      <p className="text-[var(--ink-muted)] max-w-2xl mb-14 leading-relaxed">
        Valmora is free and open-source under AGPL-3.0 while it&apos;s in beta. We&apos;d rather
        show you the shape we might grow into than pretend it&apos;s finished — nothing
        below is billed, and nothing on this page is a dark pattern waiting to switch on.
      </p>
      <PricingSection />
    </div>
  );
}
