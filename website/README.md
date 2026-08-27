# Valmora website

Promotional + docs + config-generator site for the Valmora plugin. Next.js 16
(App Router), TypeScript, Tailwind CSS v4. Fully static — no backend, no
database, no analytics.

## Structure

- `src/app/page.tsx` — landing page (hero, module pipeline schematic, pillars, teasers)
- `src/app/docs/` — documentation index + per-module pages, content in `src/lib/docsContent.ts`
- `src/app/generators/` — config generators; `item/` is a working generator, others are stubbed as "coming soon"
- `src/app/pricing/` — the free-now / reserved-for-later pricing page
- `src/components/` — shared UI (`Nav`, `Footer`, `ModulePipeline` signature diagram, `Badge`, `CodeBlock`, `PricingSection`)
- `src/lib/modules.ts` — the real module registration order from `Valmora.java` / root `CLAUDE.md` §5, used to draw the pipeline diagram
- `src/lib/itemSchema.ts` — rarities/types/stats/mechanics/triggers mirrored from `docs/modules/user/item.md`, used by the item generator

## Keeping content in sync

`src/lib/docsContent.ts` and `src/lib/itemSchema.ts` are hand-written summaries
of the real per-module docs in `../docs/modules/user/*.md` and the module
chain in `../src/main/java/org/nakii/valmora/Valmora.java`. When those change,
update these files too — nothing here parses the Java/YAML source directly.

## Pricing

The plugin is free during its beta. `src/components/PricingSection.tsx`
exports a `PRICING_ENABLED` flag and a `FUTURE_TIERS` list that render as
grayed-out, clearly unavailable placeholders — flip the flag and fill in real
pricing there if that ever changes; nothing else needs touching.

## Commands

```bash
npm run dev     # local dev server
npm run build   # production build (also type-checks)
npm run lint    # eslint
```
