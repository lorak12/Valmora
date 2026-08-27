import Link from "next/link";
import type { Metadata } from "next";
import ItemGeneratorClient from "./ItemGeneratorClient";

export const metadata: Metadata = {
  title: "Item Generator",
  description: "Build a valid Valmora item YAML entry from a form — stats, rarity, and a scripted ability.",
};

export default function ItemGeneratorPage() {
  return (
    <div className="mx-auto max-w-6xl px-6 py-16">
      <Link href="/generators" className="text-sm text-[var(--ink-muted)] hover:text-[var(--ink)]">
        ← All generators
      </Link>
      <span className="block font-mono text-xs uppercase tracking-widest text-[var(--ink-dim)] mt-6">
        Generator
      </span>
      <h1 className="font-display text-4xl sm:text-5xl mt-3 mb-4">Item Generator</h1>
      <p className="text-[var(--ink-muted)] max-w-2xl mb-12 leading-relaxed">
        Matches the schema in{" "}
        <Link href="/docs/items" className="text-[var(--amber-bright)]">
          Items &amp; Abilities
        </Link>
        . Everything runs client-side — nothing you type leaves your browser.
      </p>
      <ItemGeneratorClient />
    </div>
  );
}
