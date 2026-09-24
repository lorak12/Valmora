import type { MDXComponents } from "mdx/types";
import Link from "next/link";
import { isValidElement } from "react";
import { CodeBlock } from "@/components/CodeBlock";
import { Callout } from "@/components/docs/mdx/Callout";
import { Tab, Tabs } from "@/components/docs/mdx/Tabs";
import { SeeAlso } from "@/components/docs/mdx/SeeAlso";
import { RefTable } from "@/components/docs/mdx/RefTable";
import { ConfigSections } from "@/components/docs/mdx/ConfigSections";

function textOf(node: React.ReactNode): string {
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(textOf).join("");
  if (isValidElement<{ children?: React.ReactNode }>(node)) return textOf(node.props.children);
  return "";
}

const components: MDXComponents = {
  // Fenced code: ```yaml title="plugins/Valmora/items/my_items.yml" is not supported by plain MDX,
  // so a block's filename is its first line when that line is a `# plugins/...` / `# <path>.yml` comment.
  pre: ({ children }) => {
    const code = isValidElement<{ className?: string; children?: React.ReactNode }>(children) ? children : null;
    const lang = code?.props.className?.replace(/^language-/, "") ?? "text";
    let content = textOf(code?.props.children ?? children).replace(/\n$/, "");
    let title: string | undefined;
    const first = /^#\s+(\S+\.(?:yml|yaml|java|json))\s*\n/.exec(content);
    if (first) {
      title = first[1];
      content = content.slice(first[0].length);
    }
    return (
      <div className="my-5">
        <CodeBlock lang={lang} content={content} title={title} />
      </div>
    );
  },
  a: ({ href = "", children }) =>
    href.startsWith("/") || href.startsWith("#") ? (
      <Link href={href}>{children}</Link>
    ) : (
      <a href={href} target="_blank" rel="noreferrer">
        {children}
      </a>
    ),
  table: ({ children }) => (
    <div className="my-5 overflow-x-auto rounded-lg border border-[var(--line)]">
      <table>{children}</table>
    </div>
  ),
  Callout,
  Tabs,
  Tab,
  SeeAlso,
  RefTable,
  ConfigSections,
};

export function useMDXComponents(): MDXComponents {
  return components;
}
