import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getAllDocs, getDocMeta } from "@/lib/docs";
import { PLUGIN_VERSION, REPO_URL } from "@/lib/project";
import { DocArticle } from "@/components/docs/DocArticle";

export const dynamicParams = false;

export function generateStaticParams() {
  return getAllDocs().map((d) => ({ slug: d.slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const doc = getDocMeta(slug);
  if (!doc) return {};
  return { title: doc.title, description: doc.summary };
}

export default async function DocPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const doc = getDocMeta(slug);
  if (!doc) notFound();

  const { default: Content } = await import(`@/content/docs/${slug}.mdx`);

  const all = getAllDocs();
  const idx = all.findIndex((d) => d.slug === slug);
  const prev = all[idx - 1];
  const next = all[idx + 1];

  return (
    <div>
      <div className="flex flex-wrap items-center gap-3">
        <span className="font-mono text-xs uppercase tracking-widest text-[var(--ink-dim)]">{doc.section}</span>
        <span className="rounded-full border border-[var(--line)] px-2 py-0.5 font-mono text-[10px] text-[var(--ink-dim)]">
          v{PLUGIN_VERSION}
        </span>
      </div>
      <h1 className="font-display text-4xl sm:text-5xl mt-3 mb-4">{doc.title}</h1>
      <p className="text-lg text-[var(--ink-muted)] leading-relaxed mb-12 max-w-2xl">{doc.summary}</p>

      <DocArticle>
        <Content />
      </DocArticle>

      <div className="mt-16 max-w-3xl border-t border-[var(--line)] pt-6 text-sm">
        <div className="flex justify-between gap-6">
          {prev ? (
            <Link href={`/docs/${prev.slug}`} className="text-[var(--ink-muted)] hover:text-[var(--ink)]">
              ← {prev.title}
            </Link>
          ) : (
            <span />
          )}
          {next ? (
            <Link href={`/docs/${next.slug}`} className="text-right text-[var(--ink-muted)] hover:text-[var(--ink)]">
              {next.title} →
            </Link>
          ) : (
            <span />
          )}
        </div>
        <a
          href={`${REPO_URL}/edit/master/website/src/content/docs/${slug}.mdx`}
          target="_blank"
          rel="noreferrer"
          className="mt-6 inline-block text-xs text-[var(--ink-dim)] hover:text-[var(--ink-muted)]"
        >
          Found a mistake? Edit this page on GitHub ↗
        </a>
      </div>
    </div>
  );
}
