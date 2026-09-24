import fs from "node:fs";
import path from "node:path";
import { parse as parseYaml } from "yaml";
import { DOC_NAV } from "@/content/docs/nav";

export type DocMeta = {
  slug: string;
  title: string;
  summary: string;
  section: string;
};

export type SearchEntry = DocMeta & { headings: string[]; text: string };

const DOCS_DIR = path.join(process.cwd(), "src", "content", "docs");

function readSource(slug: string): string {
  return fs.readFileSync(path.join(DOCS_DIR, `${slug}.mdx`), "utf8");
}

function splitFrontmatter(source: string, slug: string): { data: Record<string, unknown>; body: string } {
  const match = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(source);
  if (!match) throw new Error(`docs: ${slug}.mdx has no frontmatter block`);
  return { data: parseYaml(match[1]) ?? {}, body: source.slice(match[0].length) };
}

let cache: { metas: DocMeta[]; search: SearchEntry[] } | null = null;

function load() {
  if (cache) return cache;

  const onDisk = new Set(
    fs.readdirSync(DOCS_DIR).filter((f) => f.endsWith(".mdx")).map((f) => f.replace(/\.mdx$/, ""))
  );
  const listed = DOC_NAV.flatMap((s) => s.slugs);
  const missing = listed.filter((s) => !onDisk.has(s));
  const unlisted = [...onDisk].filter((s) => !listed.includes(s));
  const duplicated = listed.filter((s, i) => listed.indexOf(s) !== i);
  if (missing.length || unlisted.length || duplicated.length) {
    throw new Error(
      `docs nav mismatch — missing files: [${missing}], not in nav.ts: [${unlisted}], listed twice: [${duplicated}]`
    );
  }

  const metas: DocMeta[] = [];
  const search: SearchEntry[] = [];
  for (const section of DOC_NAV) {
    for (const slug of section.slugs) {
      const { data, body } = splitFrontmatter(readSource(slug), slug);
      if (typeof data.title !== "string" || typeof data.summary !== "string") {
        throw new Error(`docs: ${slug}.mdx frontmatter needs a title and a summary`);
      }
      const meta: DocMeta = { slug, title: data.title, summary: data.summary, section: section.title };
      metas.push(meta);
      search.push({
        ...meta,
        headings: [...body.matchAll(/^#{2,3}\s+(.+)$/gm)].map((m) => m[1].trim()),
        text: body
          .replace(/```[\s\S]*?```/g, " ")
          .replace(/<[^>]+>/g, " ")
          .replace(/[#*`|>_\-[\]()]/g, " ")
          .replace(/\s+/g, " ")
          .slice(0, 1500),
      });
    }
  }
  cache = { metas, search };
  return cache;
}

/** Every doc page's metadata, in sidebar order. */
export function getAllDocs(): DocMeta[] {
  return load().metas;
}

export function getDocMeta(slug: string): DocMeta | undefined {
  return load().metas.find((d) => d.slug === slug);
}

/** Sidebar sections with their pages' metadata. */
export function getDocNav(): { title: string; docs: DocMeta[] }[] {
  const metas = load().metas;
  return DOC_NAV.map((s) => ({ title: s.title, docs: s.slugs.map((slug) => metas.find((m) => m.slug === slug)!) }));
}

/** A small plain-text index of every page for the sidebar's client-side search. */
export function getSearchIndex(): SearchEntry[] {
  return load().search;
}
