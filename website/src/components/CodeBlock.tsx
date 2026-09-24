export function CodeBlock({ lang, content, title }: { lang?: string; content: string; title?: string }) {
  return (
    <div className="rounded-lg border border-[var(--line)] bg-[var(--bg-inset)] overflow-hidden">
      <div className="flex items-center justify-between gap-4 border-b border-[var(--line)] px-4 py-2">
        <span className="min-w-0 truncate font-mono text-[11px] tracking-wider text-[var(--ink-dim)]">
          {title ?? <span className="uppercase">{lang ?? "yaml"}</span>}
        </span>
        <span className="flex shrink-0 gap-1.5">
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
