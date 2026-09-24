import { DocsSidebar } from "@/components/docs/DocsSidebar";
import { getDocNav, getSearchIndex } from "@/lib/docs";

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-7xl px-4 sm:px-6 py-16">
      <div className="grid gap-10 lg:grid-cols-[230px_1fr]">
        <DocsSidebar nav={getDocNav()} index={getSearchIndex()} />
        <div className="min-w-0">{children}</div>
      </div>
    </div>
  );
}
