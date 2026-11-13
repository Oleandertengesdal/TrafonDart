import { useTranslation } from "react-i18next";
import { Bar, BarChart, CartesianGrid, ComposedChart, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { AveragePoint, Band, RatingPoint, TurnRow } from "../../api/types";
import { num, shortDate, date } from "../../lib/format";
import { chart as C } from "../../lib/palette";

// Chart colours live in lib/palette.ts (validated for colour-vision deficiency and contrast).

const axisProps = {
  stroke: C.axis,
  tick: { fill: C.axis, fontSize: 12 },
  tickLine: false,
  axisLine: { stroke: C.grid },
} as const;

function TooltipBox({ children }: { children: React.ReactNode }) {
  return <div className="rounded-md border border-mortar bg-night px-3 py-2 text-sm shadow-xl">{children}</div>;
}

export function RatingChart({ points }: { points: RatingPoint[] }) {
  const { t } = useTranslation();
  const data = points.map((p, i) => ({ ...p, i }));
  return (
    <div className="h-64 w-full">
      <ResponsiveContainer>
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: -8 }}>
          <CartesianGrid stroke={C.grid} strokeDasharray="2 4" vertical={false} />
          <XAxis dataKey="i" {...axisProps} tickFormatter={(i: number) => shortDate(data[i]?.at)} minTickGap={32} />
          <YAxis {...axisProps} domain={["dataMin - 30", "dataMax + 30"]} tickFormatter={(v: number) => num(v)} width={48} />
          <Tooltip
            cursor={{ stroke: C.axis, strokeDasharray: "3 3" }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const p = payload[0].payload as RatingPoint;
              return (
                <TooltipBox>
                  <p className="numerals text-xl font-bold text-trim">{num(p.rating)}</p>
                  <p className="text-trim-dim">
                    {p.won ? t("stats.win") : t("stats.loss")} {t("common.vs")} @{p.opponent}
                  </p>
                  <p className="text-xs text-trim-faint">{date(p.at)}</p>
                </TooltipBox>
              );
            }}
          />
          <Line
            type="monotone"
            dataKey="rating"
            stroke={C.rating}
            strokeWidth={2}
            dot={data.length <= 30 ? { r: 4, fill: C.rating, stroke: C.surface, strokeWidth: 2 } : false}
            activeDot={{ r: 6, fill: C.rating, stroke: C.surface, strokeWidth: 2 }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function AverageChart({ points }: { points: AveragePoint[] }) {
  const { t } = useTranslation();
  const data = points.map((p, i) => ({ ...p, i }));
  return (
    <div>
      <ul className="mb-2 flex gap-4 text-sm text-trim-dim" aria-hidden>
        <li className="flex items-center gap-2">
          <span className="h-3 w-2 rounded-sm" style={{ background: C.avg }} />
          {t("stats.average")}
        </li>
        <li className="flex items-center gap-2">
          <span className="h-0.5 w-4 rounded" style={{ background: C.first9 }} />
          {t("stats.first9")}
        </li>
      </ul>
      <div className="h-64 w-full">
        <ResponsiveContainer>
          <ComposedChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: -8 }} barCategoryGap="30%">
            <CartesianGrid stroke={C.grid} strokeDasharray="2 4" vertical={false} />
            <XAxis dataKey="i" {...axisProps} tickFormatter={(i: number) => shortDate(data[i]?.at)} minTickGap={32} />
            <YAxis {...axisProps} width={40} tickFormatter={(v: number) => num(v)} />
            <Tooltip
              cursor={{ fill: C.cursor }}
              content={({ active, payload }) => {
                if (!active || !payload?.length) return null;
                const p = payload[0].payload as AveragePoint;
                return (
                  <TooltipBox>
                    <p className="mb-1 text-xs text-trim-faint">
                      {date(p.at)}, {p.won ? t("stats.win") : t("stats.loss")}
                    </p>
                    <p className="flex justify-between gap-4">
                      <span className="text-trim-dim">{t("stats.average")}</span>
                      <span className="numerals font-bold text-trim">{num(p.average, 2)}</span>
                    </p>
                    <p className="flex justify-between gap-4">
                      <span className="text-trim-dim">{t("stats.first9")}</span>
                      <span className="numerals font-bold text-trim">{num(p.first9Average, 2)}</span>
                    </p>
                  </TooltipBox>
                );
              }}
            />
            <Bar dataKey="average" name={t("stats.average")} fill={C.avg} radius={[4, 4, 0, 0]} maxBarSize={18} isAnimationActive={false} />
            <Line
              type="monotone"
              dataKey="first9Average"
              name={t("stats.first9")}
              stroke={C.first9}
              strokeWidth={2}
              dot={{ r: 4, fill: C.first9, stroke: C.surface, strokeWidth: 2 }}
              isAnimationActive={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function Legend({ items }: { items: { label: string; color: string; line?: boolean }[] }) {
  if (items.length < 2) return null;
  return (
    <ul className="mb-2 flex flex-wrap gap-4 text-sm text-trim-dim" aria-hidden>
      {items.map((it) => (
        <li key={it.label} className="flex items-center gap-2">
          <span className={it.line ? "h-0.5 w-4 rounded" : "h-3 w-2 rounded-sm"} style={{ background: it.color }} />
          {it.label}
        </li>
      ))}
    </ul>
  );
}

/** Turn-score distribution. One series (profile) or two (match: player 1 vs player 2). */
export function BandChart({ series }: { series: { name: string; bands: Band[] }[] }) {
  const colors = [C.avg, C.first9];
  const data = series[0].bands.map((b, i) => {
    const row: Record<string, string | number> = { label: b.label };
    series.forEach((s, j) => (row[`s${j}`] = s.bands[i]?.count ?? 0));
    return row;
  });
  return (
    <div>
      <Legend items={series.map((s, j) => ({ label: s.name, color: colors[j] }))} />
      <div className="h-56 w-full">
        <ResponsiveContainer>
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }} barGap={2} barCategoryGap="22%">
            <CartesianGrid stroke={C.grid} strokeDasharray="2 4" vertical={false} />
            <XAxis dataKey="label" {...axisProps} interval={0} tick={{ fill: C.axis, fontSize: 11 }} />
            <YAxis {...axisProps} allowDecimals={false} width={40} />
            <Tooltip
              cursor={{ fill: C.cursor }}
              content={({ active, payload, label }) => {
                if (!active || !payload?.length) return null;
                return (
                  <TooltipBox>
                    <p className="mb-1 text-xs text-trim-faint">{label}</p>
                    {payload.map((p, j) => (
                      <p key={j} className="flex justify-between gap-4">
                        <span className="text-trim-dim">{series[j]?.name}</span>
                        <span className="font-bold text-trim">{num(Number(p.value))}</span>
                      </p>
                    ))}
                  </TooltipBox>
                );
              }}
            />
            {series.map((s, j) => (
              <Bar key={s.name} dataKey={`s${j}`} name={s.name} fill={colors[j]} radius={[4, 4, 0, 0]} maxBarSize={22} isAnimationActive={false} />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

/** Remaining score after each turn in one leg, both players. */
export function RaceChart({ turns, names, start }: { turns: TurnRow[]; names: [string, string]; start: number }) {
  const { t } = useTranslation();
  const rows: { n: number; p0?: number; p1?: number }[] = [{ n: 0, p0: start, p1: start }];
  const counters = [0, 0];
  for (const turn of turns) {
    counters[turn.playerIndex]++;
    const n = counters[turn.playerIndex];
    let row = rows.find((r) => r.n === n);
    if (!row) {
      row = { n };
      rows.push(row);
    }
    row[turn.playerIndex === 0 ? "p0" : "p1"] = turn.remainingAfter;
  }
  return (
    <div>
      <Legend
        items={[
          { label: names[0], color: C.avg, line: true },
          { label: names[1], color: C.first9, line: true },
        ]}
      />
      <div className="h-56 w-full">
        <ResponsiveContainer>
          <LineChart data={rows} margin={{ top: 8, right: 12, bottom: 0, left: -12 }}>
            <CartesianGrid stroke={C.grid} strokeDasharray="2 4" vertical={false} />
            <XAxis dataKey="n" {...axisProps} allowDecimals={false} />
            <YAxis {...axisProps} domain={[0, start]} width={44} />
            <Tooltip
              cursor={{ stroke: C.axis, strokeDasharray: "3 3" }}
              content={({ active, payload, label }) => {
                if (!active || !payload?.length) return null;
                return (
                  <TooltipBox>
                    <p className="mb-1 text-xs text-trim-faint">
                      {t("analysis.turn")} {label}
                    </p>
                    {payload.map((p) => (
                      <p key={String(p.dataKey)} className="flex justify-between gap-4">
                        <span className="text-trim-dim">{p.dataKey === "p0" ? names[0] : names[1]}</span>
                        <span className="font-bold text-trim">{num(Number(p.value))}</span>
                      </p>
                    ))}
                  </TooltipBox>
                );
              }}
            />
            <Line type="stepAfter" dataKey="p0" stroke={C.avg} strokeWidth={2} dot={{ r: 3, fill: C.avg, strokeWidth: 0 }} connectNulls isAnimationActive={false} />
            <Line type="stepAfter" dataKey="p1" stroke={C.first9} strokeWidth={2} dot={{ r: 3, fill: C.first9, strokeWidth: 0 }} connectNulls isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
