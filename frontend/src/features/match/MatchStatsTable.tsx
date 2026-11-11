import { useTranslation } from "react-i18next";
import type { LiveMatch } from "../../api/types";
import { num, pct } from "../../lib/format";

/** Side-by-side match statistics, better value highlighted. */
export function MatchStatsTable({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  const [a, b] = match.stats;
  const rows: { label: string; values: [number, number]; format: (n: number) => string; higherIsBetter?: boolean }[] = [
    { label: t("match.average"), values: [a.average, b.average], format: (n) => num(n, 2) },
    { label: t("match.first9"), values: [a.first9Average, b.first9Average], format: (n) => num(n, 2) },
    { label: t("stats.s180"), values: [a.count180, b.count180], format: (n) => num(n) },
    { label: t("stats.s140"), values: [a.count140, b.count140], format: (n) => num(n) },
    { label: t("stats.s100"), values: [a.count100, b.count100], format: (n) => num(n) },
    { label: t("stats.highestTurn"), values: [a.highestTurn, b.highestTurn], format: (n) => num(n) },
    { label: t("stats.highestCheckout"), values: [a.highestCheckout, b.highestCheckout], format: (n) => (n ? num(n) : "–") },
    { label: t("stats.checkoutPct"), values: [a.checkoutPercentage, b.checkoutPercentage], format: (n) => pct(n, 0) },
    { label: t("match.dartsAt"), values: [a.checkoutAttempts, b.checkoutAttempts], format: (n) => num(n), higherIsBetter: false },
    { label: t("match.darts"), values: [a.dartsThrown, b.dartsThrown], format: (n) => num(n), higherIsBetter: false },
  ];
  return (
    <table className="w-full text-sm">
      <caption className="sr-only">{t("match.statsTitle")}</caption>
      <thead>
        <tr className="text-trim-faint">
          <th className="w-1/3 py-2 text-left font-semibold">{match.players[0].realName}</th>
          <th className="w-1/3" />
          <th className="w-1/3 py-2 text-right font-semibold">{match.players[1].realName}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => {
          const better = r.higherIsBetter === false ? null : r.values[0] === r.values[1] ? null : r.values[0] > r.values[1] ? 0 : 1;
          return (
            <tr key={r.label} className="border-t border-mortar/60">
              <td className={`numerals py-2 text-left text-lg ${better === 0 ? "font-bold text-trim" : "text-trim-dim"}`}>{r.format(r.values[0])}</td>
              <th scope="row" className="py-2 text-center text-xs font-semibold uppercase tracking-wider text-trim-faint">
                {r.label}
              </th>
              <td className={`numerals py-2 text-right text-lg ${better === 1 ? "font-bold text-trim" : "text-trim-dim"}`}>{r.format(r.values[1])}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
