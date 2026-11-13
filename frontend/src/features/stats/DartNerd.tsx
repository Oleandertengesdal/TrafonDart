import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { useDartStats } from "../../api/queries";
import { SectionTitle, Spinner } from "../../components/ui";
import { dartLabel, date, num, pct } from "../../lib/format";
import { BandChart } from "./Charts";
import { DartboardHeatmap } from "./Dartboard";

/** The "darts nerd corner" on a profile: heatmap, hit rates, hold/break, best leg, favourite finishes. */
export function DartNerd({ username }: { username: string }) {
  const { t } = useTranslation();
  const q = useDartStats(username);
  if (q.isLoading) return <Spinner />;
  const d = q.data;
  if (!d || d.dartsThrown === 0) return null;

  const rate = (r: { won: number; of: number }) => (r.of === 0 ? "–" : pct((r.won * 100) / r.of, 0));
  const tiles: { label: string; value: string; sub?: string; accent?: boolean }[] = [
    { label: t("nerd.dartsThrown"), value: num(d.dartsThrown), sub: `${t("nerd.pointsScored")} ${num(d.pointsScored)}` },
    { label: t("nerd.trebleRate"), value: pct(d.trebleRate) },
    { label: t("nerd.doubleRate"), value: pct(d.doubleRate) },
    { label: t("nerd.bullRate"), value: pct(d.bullRate) },
    { label: t("nerd.missRate"), value: pct(d.missRate) },
    {
      label: t("nerd.bestLeg"),
      value: d.bestLeg ? t("nerd.bestLegValue", { count: d.bestLeg }) : "–",
      sub: d.bestLeg === 9 ? t("nerd.nineDarter") : undefined,
      accent: d.bestLeg !== null && d.bestLeg <= 12,
    },
    { label: t("nerd.avgDarts"), value: d.avgDartsPerLegWon ? num(d.avgDartsPerLegWon, 1) : "–" },
    { label: t("nerd.hold"), value: rate(d.hold), sub: `${t("nerd.holdHint")} (${d.hold.won}/${d.hold.of})` },
    { label: t("nerd.break"), value: rate(d.breaks), sub: `${t("nerd.breakHint")} (${d.breaks.won}/${d.breaks.of})` },
    { label: t("nerd.bustRate"), value: pct(d.bustRate), sub: `${num(d.busts)}` },
    { label: t("nerd.commonTurn"), value: d.mostCommonTurn !== null ? num(d.mostCommonTurn) : "–" },
    { label: t("nerd.favourite"), value: d.favouriteSegment !== null ? (d.favouriteSegment === 25 ? t("match.bull") : String(d.favouriteSegment)) : "–" },
    { label: t("nerd.kudosReceived"), value: num(d.kudosReceived), accent: d.kudosReceived > 0 },
  ];

  return (
    <section>
      <SectionTitle>{t("nerd.title")}</SectionTitle>
      <p className="-mt-2 mb-5 text-sm text-trim-faint">{t("nerd.lead")}</p>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,22rem)_1fr]">
        <div className="panel flex flex-col items-center p-5">
          <DartboardHeatmap cells={d.heatmap} size={320} />
        </div>
        <div className="grid grid-cols-2 content-start gap-3 sm:grid-cols-3 xl:grid-cols-4">
          {tiles.map((tile) => (
            <div key={tile.label} className={`panel flex flex-col gap-1 p-4 ${tile.accent ? "border-brass/70" : ""}`}>
              <span className="text-xs font-semibold text-trim-faint">{tile.label}</span>
              <span className={`numerals text-3xl font-extrabold leading-none ${tile.accent ? "text-brass" : "text-trim"}`}>{tile.value}</span>
              {tile.sub && <span className="text-xs text-trim-dim">{tile.sub}</span>}
            </div>
          ))}
        </div>
      </div>
      <div className="mt-6 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <div className="panel p-5">
          <h3 className="font-display text-xl">{t("analysis.scoreBands")}</h3>
          <p className="mb-3 text-sm text-trim-faint">{t("analysis.scoreBandsHint")}</p>
          <BandChart series={[{ name: t("analysis.scoreBands"), bands: d.bands }]} />
        </div>
        <div className="flex flex-col gap-6">
          <div className="panel p-5">
            <h3 className="mb-3 font-display text-xl">{t("nerd.topCheckouts")}</h3>
            <ol className="flex flex-col gap-2">
              {d.topCheckouts.map((c, i) => (
                <li key={`${c.matchId}-${i}`}>
                  <Link to={`/matches/${c.matchId}`} className="flex items-baseline gap-3 rounded px-1 hover:bg-masonry-2">
                    <span className={`numerals w-14 text-2xl font-extrabold ${c.score >= 100 ? "text-brass" : "text-trim"}`}>{c.score}</span>
                    <span className="text-sm text-trim-dim">{dartLabel(c.finisher)}</span>
                    <span className="ml-auto text-xs text-trim-faint">{date(c.at)}</span>
                  </Link>
                </li>
              ))}
            </ol>
          </div>
          {d.finishes.length > 0 && (
            <div className="panel p-5">
              <h3 className="mb-3 font-display text-xl">{t("nerd.finishes")}</h3>
              <ul className="flex flex-wrap gap-2">
                {d.finishes.map((f) => (
                  <li key={f.label} className="rounded-md border border-board/60 bg-board/15 px-3 py-2">
                    <span className="font-display text-xl">{dartLabel(f.label)}</span>
                    <span className="ml-2 text-sm text-trim-dim">×{f.count}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
