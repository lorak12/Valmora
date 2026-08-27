import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DOCS, getDoc } from "@/lib/docsContent";
import { DocBody } from "@/components/docs/DocBody";

export function generateStaticParams() {
  return DOCS.map((d) => ({ slug: d.slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const doc = getDoc(slug);
  if (!doc) return {};
  return { title: doc.title, description: doc.summary };
}

export default async function DocPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const doc = getDoc(slug);
  if (!doc) notFound();

  const idx = DOCS.findIndex((d) => d.slug === slug);
  const prev = DOCS[idx - 1];
  const next = DOCS[idx + 1];

  return (
    <div>
      <span className="block font-mono text-xs uppercase tracking-widest text-[var(--ink-dim)]">
        {doc.category}
      </span>
      <h1 className="font-display text-4xl sm:text-5xl mt-3 mb-4">{doc.title}</h1>
      <p className="text-lg text-[var(--ink-muted)] leading-relaxed mb-12 max-w-2xl">{doc.summary}</p>

      <DocBody doc={doc} />

      <div className="mt-16 flex justify-between border-t border-[var(--line)] pt-6 text-sm max-w-2xl">
        {prev ? (
          <Link href={`/docs/${prev.slug}`} className="text-[var(--ink-muted)] hover:text-[var(--ink)]">
            ← {prev.title}
          </Link>
        ) : <span />}
        {next ? (
          <Link href={`/docs/${next.slug}`} className="text-[var(--ink-muted)] hover:text-[var(--ink)]">
            {next.title} →
          </Link>
        ) : <span />}
      </div>
    </div>
  );
}
