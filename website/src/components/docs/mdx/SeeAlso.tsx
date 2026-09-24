import Link from "next/link";
import { getDocMeta } from "@/lib/docs";

/** A "See also" row of links to other doc pages, by slug. Titles come from each page's frontmatter. */
export function SeeAlso({ slugs }: { slugs: string[] }) {
  return (
    <div className="mt-12 border-t border-[var(--line)] pt-6">
      <span className="block font-mono text-[11px] uppercase tracking-wider text-[var(--ink-dim)] mb-3">
        See also
      </span>
      <div className="flex flex-wrap gap-2">
        {slugs.map((slug) => {
          const meta = getDocMeta(slug);
          if (!meta) throw new Error(`<SeeAlso>: unknown doc slug "${slug}"`);
          return (
            <Link
              key={slug}
              href={`/docs/${slug}`}
              className="rounded-full border border-[var(--line)] px-3 py-1 text-sm text-[var(--ink-muted)] hover:border-[var(--amber)]/60 hover:text-[var(--ink)]"
            >
              {meta.title}
            </Link>
          );
        })}
      </div>
    </div>
  );
}
