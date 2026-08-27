import Link from "next/link";

export function Footer() {
  return (
    <footer className="border-t border-[var(--line)] mt-24">
      <div className="mx-auto max-w-6xl px-6 py-12 grid gap-10 sm:grid-cols-3">
        <div>
          <div className="font-display text-lg mb-2">Valmora</div>
          <p className="text-sm text-[var(--ink-muted)] leading-relaxed max-w-xs">
            A modular RPG engine for Paper 1.21. Open-sourced under AGPL-3.0, with a
            commercial license available from the copyright holder.
          </p>
        </div>
        <div className="text-sm">
          <div className="text-[var(--ink-dim)] uppercase tracking-wider text-xs mb-3">Site</div>
          <ul className="space-y-2 text-[var(--ink-muted)]">
            <li><Link href="/docs" className="hover:text-[var(--ink)]">Documentation</Link></li>
            <li><Link href="/generators" className="hover:text-[var(--ink)]">Config generators</Link></li>
            <li><Link href="/pricing" className="hover:text-[var(--ink)]">Pricing</Link></li>
          </ul>
        </div>
        <div className="text-sm">
          <div className="text-[var(--ink-dim)] uppercase tracking-wider text-xs mb-3">Project</div>
          <ul className="space-y-2 text-[var(--ink-muted)]">
            <li><a href="https://github.com" className="hover:text-[var(--ink)]">Source on GitHub</a></li>
            <li><a href="#" className="hover:text-[var(--ink)]">Changelog</a></li>
            <li><a href="#" className="hover:text-[var(--ink)]">License (AGPL-3.0)</a></li>
          </ul>
        </div>
      </div>
      <div className="border-t border-[var(--line)] py-5 text-center text-xs text-[var(--ink-dim)]">
        Built for Paper 1.21 · Not affiliated with Mojang or Microsoft.
      </div>
    </footer>
  );
}
