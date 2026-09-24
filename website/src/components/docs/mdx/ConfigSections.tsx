import fs from "node:fs";
import path from "node:path";
import reference from "@/generated/reference.json";
import { CodeBlock } from "@/components/CodeBlock";

/**
 * Renders the plugin's real shipped config.yml, one top-level section at a time, each with its
 * own comments and a short introduction from src/content/reference/config-sections.yml — so the
 * config reference is the file itself and can't drift from it. (scripts/extract-reference.mjs
 * fails the build if a section has no introduction, or an introduction has no section.)
 */
function sections(): { key: string; text: string }[] {
  const file = fs.readFileSync(path.join(process.cwd(), "..", "src", "main", "resources", "config.yml"), "utf8");
  const lines = file.split(/\r?\n/);
  const keyLines = lines.flatMap((l, i) => (/^[a-z][a-z0-9_-]*:/.test(l) ? [i] : []));

  // A section starts at its key, plus the comment block directly above it (its banner/notes).
  const starts = keyLines.map((k) => {
    let s = k;
    while (s > 0 && lines[s - 1].startsWith("#")) s--;
    return s;
  });

  return keyLines.map((k, i) => ({
    key: lines[k].split(":")[0],
    text: lines
      .slice(starts[i], i + 1 < starts.length ? starts[i + 1] : lines.length)
      .join("\n")
      .replace(/^(# =+ #\n#.*\n# =+ #\n)/, "") // drop the decorative banner; the heading replaces it
      .trim(),
  }));
}

export function ConfigSections() {
  const intros = (reference.descriptions as Record<string, Record<string, { intro?: string }>>).configSections ?? {};
  return (
    <>
      {sections().map(({ key, text }) => (
        <section key={key}>
          <h2 id={key}>
            <code>{key}:</code>
          </h2>
          <p>{intros[key]?.intro}</p>
          <div className="my-5">
            <CodeBlock lang="yaml" content={text} title={`config.yml → ${key}`} />
          </div>
        </section>
      ))}
    </>
  );
}
