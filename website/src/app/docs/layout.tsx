import { DocsSidebar } from "@/components/docs/DocsSidebar";

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-7xl px-6 py-16">
      <div className="grid gap-10 lg:grid-cols-[220px_1fr]">
        <DocsSidebar />
        <div className="min-w-0">{children}</div>
      </div>
    </div>
  );
}
