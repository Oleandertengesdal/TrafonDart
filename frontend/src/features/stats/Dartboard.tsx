import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { HeatCell } from "../../api/types";
import { num, pct } from "../../lib/format";

/** Board order, clockwise from the top. */
const ORDER = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
// Radii in mm on a regulation board.
const R = { bull: 6.35, outerBull: 15.9, trebleIn: 99, trebleOut: 107, doubleIn: 162, doubleOut: 170 };

function polar(r: number, deg: number) {
  const rad = ((deg - 90) * Math.PI) / 180;
  return [r * Math.cos(rad), r * Math.sin(rad)];
}

function wedge(r1: number, r2: number, a1: number, a2: number) {
  const [x1, y1] = polar(r2, a1);
  const [x2, y2] = polar(r2, a2);
  const [x3, y3] = polar(r1, a2);
  const [x4, y4] = polar(r1, a1);
  return `M${x1} ${y1}A${r2} ${r2} 0 0 1 ${x2} ${y2}L${x3} ${y3}A${r1} ${r1} 0 0 0 ${x4} ${y4}Z`;
}

/**
 * A regulation dartboard drawn as a heatmap: each bed is tinted Trafon red by how often it was hit
 * (one hue, light → strong, square-root scaled so a few hot beds don't wash out the rest).
 * Hover or tap a bed for its exact count.
 */
export function DartboardHeatmap({ cells, size = 320 }: { cells: HeatCell[]; size?: number }) {
  const { t } = useTranslation();
  const [focus, setFocus] = useState<string | null>(null);

  const { counts, max, total, misses } = useMemo(() => {
    const counts = new Map<string, number>();
    let max = 0;
    let total = 0;
    let misses = 0;
    for (const c of cells) {
      total += c.count;
      if (c.segment === 0) {
        misses += c.count;
        continue;
      }
      counts.set(`${c.segment}:${c.multiplier}`, c.count);
      max = Math.max(max, c.count);
    }
    return { counts, max, total, misses };
  }, [cells]);

  const label = (segment: number, mult: number) =>
    segment === 25 ? (mult === 2 ? t("match.bull") : "25") : `${mult === 1 ? "" : mult === 2 ? "D" : "T"}${segment}`;

  const bed = (key: string, d: string, segment: number, mult: number, dark: boolean) => {
    const n = counts.get(`${segment}:${mult}`) ?? 0;
    const heat = max === 0 ? 0 : Math.sqrt(n / max);
    const text = `${label(segment, mult)}: ${num(n)}${total ? ` (${pct((n * 100) / total)})` : ""}`;
    return (
      <path
        key={key}
        d={d}
        fill={n === 0 ? (dark ? "#1d1110" : "#2a1915") : `rgba(224, 57, 44, ${0.18 + 0.82 * heat})`}
        stroke="#4a2c25"
        strokeWidth={0.6}
        onMouseEnter={() => setFocus(text)}
        onMouseLeave={() => setFocus(null)}
        onClick={() => setFocus(text)}
        className="cursor-pointer transition-opacity hover:opacity-80"
      >
        <title>{text}</title>
      </path>
    );
  };

  const beds = ORDER.flatMap((segment, i) => {
    const a1 = i * 18 - 9;
    const a2 = a1 + 18;
    const dark = i % 2 === 0;
    return [
      bed(`${segment}-si`, wedge(R.outerBull, R.trebleIn, a1, a2), segment, 1, dark),
      bed(`${segment}-t`, wedge(R.trebleIn, R.trebleOut, a1, a2), segment, 3, !dark),
      bed(`${segment}-so`, wedge(R.trebleOut, R.doubleIn, a1, a2), segment, 1, dark),
      bed(`${segment}-d`, wedge(R.doubleIn, R.doubleOut, a1, a2), segment, 2, !dark),
    ];
  });

  // Single beds share one count; show the split inner/outer beds with the same tint.
  return (
    <figure className="flex flex-col items-center gap-3">
      <svg viewBox="-200 -200 400 400" width={size} height={size} role="img" aria-label={t("analysis.heatmap")} className="max-w-full">
        <circle r={198} fill="#170d0c" />
        {beds}
        {bed("ob", `M${R.outerBull} 0A${R.outerBull} ${R.outerBull} 0 1 1 ${-R.outerBull} 0A${R.outerBull} ${R.outerBull} 0 1 1 ${R.outerBull} 0Z`, 25, 1, false)}
        {bed("b", `M${R.bull} 0A${R.bull} ${R.bull} 0 1 1 ${-R.bull} 0A${R.bull} ${R.bull} 0 1 1 ${R.bull} 0Z`, 25, 2, true)}
        {ORDER.map((segment, i) => {
          const [x, y] = polar(185, i * 18);
          return (
            <text key={segment} x={x} y={y} textAnchor="middle" dominantBaseline="central" fill="#bfaea3" fontSize="15" fontFamily="var(--font-display)" fontWeight="700">
              {segment}
            </text>
          );
        })}
      </svg>
      <figcaption className="flex w-full flex-col items-center gap-1 text-xs text-trim-faint">
        <span className="min-h-5 text-sm font-semibold text-trim" aria-live="polite">
          {focus ?? t("analysis.misses", { count: misses })}
        </span>
        <span className="flex items-center gap-2">
          {t("analysis.few")}
          <span className="h-2 w-28 rounded-full" style={{ background: "linear-gradient(to right, rgba(224,57,44,0.18), rgba(224,57,44,1))" }} />
          {t("analysis.many")}
        </span>
      </figcaption>
    </figure>
  );
}
