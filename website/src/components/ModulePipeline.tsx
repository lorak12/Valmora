import { MODULE_LABELS } from "@/lib/modules";

/**
 * The signature element: a schematic of the real module registration chain
 * (Valmora.java / CLAUDE.md §5). `compact` renders the curated hero subset;
 * otherwise the full 28-module chain is drawn, wrapping across rows.
 */
export function ModulePipeline({
  ids,
  compact = false,
}: {
  ids: readonly string[];
  compact?: boolean;
}) {
  const nodeW = compact ? 108 : 92;
  const gap = compact ? 34 : 26;
  const h = compact ? 56 : 44;

  return (
    <div className="overflow-x-auto pb-2 -mx-1 px-1">
      <svg
        role="img"
        aria-label={`Module pipeline: ${ids.map((i) => MODULE_LABELS[i] ?? i).join(" then ")}`}
        width={ids.length * (nodeW + gap) - gap}
        height={h + 8}
        className="block"
      >
        {ids.map((id, i) => {
          const x = i * (nodeW + gap);
          const label = MODULE_LABELS[id] ?? id;
          return (
            <g key={id}>
              {i < ids.length - 1 && (
                <line
                  x1={x + nodeW}
                  y1={h / 2 + 4}
                  x2={x + nodeW + gap}
                  y2={h / 2 + 4}
                  stroke="var(--amber)"
                  strokeWidth={compact ? 1.6 : 1.2}
                  strokeDasharray="240"
                  style={{
                    animation: `wire-pulse 3.2s ${(i * 0.18).toFixed(2)}s infinite linear`,
                  }}
                />
              )}
              {i < ids.length - 1 && (
                <line
                  x1={x + nodeW}
                  y1={h / 2 + 4}
                  x2={x + nodeW + gap}
                  y2={h / 2 + 4}
                  stroke="var(--line-bright)"
                  strokeWidth={compact ? 1.2 : 1}
                />
              )}
              <rect
                x={x}
                y={0}
                width={nodeW}
                height={h}
                rx={7}
                fill="var(--bg-raised)"
                stroke="var(--line-bright)"
                strokeWidth="1"
                style={{ animation: `node-glow 3.2s ${(i * 0.18).toFixed(2)}s infinite ease-in-out` }}
              />
              <text
                x={x + nodeW / 2}
                y={h / 2 + 4}
                textAnchor="middle"
                fontFamily="var(--font-mono)"
                fontSize={compact ? 12 : 10.5}
                fill="var(--ink)"
              >
                {label}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
