export function CodeBlock({ lang, content }: { lang?: string; content: string }) {
  return (
    <div className="rounded-lg border border-[var(--line)] bg-[var(--bg-inset)] overflow-hidden">
      <div className="flex items-center justify-between border-b border-[var(--line)] px-4 py-2">
        <span className="font-mono text-[11px] uppercase tracking-wider text-[var(--ink-dim)]">
          {lang ?? "yaml"}
        </span>
        <span className="flex gap-1.5">
          <span className="h-2 w-2 rounded-full bg-[var(--line-bright)]" />
          <span className="h-2 w-2 rounded-full bg-[var(--line-bright)]" />
          <span className="h-2 w-2 rounded-full bg-[var(--amber)]/70" />
        </span>
      </div>
      <pre className="overflow-x-auto p-4 text-[13px] leading-relaxed">
        <code className="font-mono text-[var(--ink)]">{content}</code>
      </pre>
    </div>
  );
}
