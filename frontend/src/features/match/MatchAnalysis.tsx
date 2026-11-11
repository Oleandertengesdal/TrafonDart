import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { api } from "../../api/client";
import { useBreakdown } from "../../api/queries";
import type { HeadToHeadRow, LiveMatch, MatchBreakdown } from "../../api/types";
import { ErrorNotice, Spinner } from "../../components/ui";
import { dartLabel, num, pct } from "../../lib/format";
import { BandChart, RaceChart } from "../stats/Charts";
import { DartboardHeatmap } from "../stats/Dartboard";

export type AnalysisTab = "overview" | "turns" | "analysis" | "h2h";

export function AnalysisTabs({ value, onChange }: { value: AnalysisTab; onChange: (t: AnalysisTab) => void }) {
  const { t } = useTranslation();
  const tabs: AnalysisTab[] = ["overview", "turns", "analysis", "h2h"];
  return (
    <div role="tablist" className="mt-8 flex gap-1 overflow-x-auto border-b border-mortar">
      {tabs.map((x) => (
        <button
          key={x}
          role="tab"
          aria-selected={value === x}
          onClick={() => onChange(x)}
          className={`-mb-px shrink-0 border-b-2 px-4 py-2.5 text-sm font-semibold ${value === x ? "border-trafon text-trim" : "border-transparent text-trim-dim hover:text-trim"}`}
        >
          {t(`analysis.tabs.${x}`)}
        </button>
      ))}
    </div>
  );
}

function useMatchBreakdown(match: LiveMatch) {
  return useBreakdown(match.id, match.dartCount);
}

/** Every turn of every leg, both players side by side. */
export function TurnByTurn({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  const q = useMatchBreakdown(match);
  if (q.isLoading) return <Spinner />;
  if (!q.data) return <ErrorNotice error={q.error} />;
  const legs = [...q.data.legs].reverse(); // newest leg first
  if (legs.length === 0) return <p className="mt-6 text-trim-faint">{t("analysis.turnsEmpty")}</p>;
  return (
    <div className="mt-6 flex flex-col gap-6">
      {legs.map((leg) => {
        const rounds = Math.max(...leg.turns.map((x) => x.turnInLeg), 0);
        return (
          <section key={leg.legNo} className="panel overflow-hidden">
            <header className="flex flex-wrap items-baseline justify-between gap-2 border-b border-mortar px-4 py-3">
              <h3 className="font-display text-xl font-bold uppercase">
                {t("match.legN", { n: leg.legNo })}
                {leg.winnerIndex !== null && <span className="ml-3 text-base text-brass">{match.players[leg.winnerIndex].realName}</span>}
              </h3>
              <span className="text-xs text-trim-faint">
                {match.players[0].realName}: {t("analysis.legSummary", { darts: leg.dartsUsed[0], avg: num(leg.averages[0], 1) })}
                {"  "}|{"  "}
                {match.players[1].realName}: {t("analysis.legSummary", { darts: leg.dartsUsed[1], avg: num(leg.averages[1], 1) })}
              </span>
            </header>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[34rem] text-sm">
                <thead className="text-xs uppercase tracking-wider text-trim-faint">
                  <tr>
                    <th className="px-3 py-2 text-left font-semibold">{t("analysis.darts")}</th>
                    <th className="px-2 py-2 text-right font-semibold">{t("analysis.scored")}</th>
                    <th className="px-2 py-2 text-right font-semibold">{t("analysis.left")}</th>
                    <th className="px-2 py-2 text-center font-semibold">#</th>
                    <th className="px-2 py-2 text-left font-semibold">{t("analysis.left")}</th>
                    <th className="px-2 py-2 text-left font-semibold">{t("analysis.scored")}</th>
                    <th className="px-3 py-2 text-right font-semibold">{t("analysis.darts")}</th>
                  </tr>
                </thead>
                <tbody>
                  {Array.from({ length: rounds }, (_, i) => i + 1).map((r) => {
                    const a = leg.turns.find((x) => x.playerIndex === 0 && x.turnInLeg === r);
                    const b = leg.turns.find((x) => x.playerIndex === 1 && x.turnInLeg === r);
                    const scoreCls = (x?: typeof a) =>
                      !x ? "" : x.bust ? "text-salmon line-through" : x.checkout ? "font-bold text-board-bright" : x.scored === 180 ? "font-bold text-brass" : x.scored >= 100 ? "font-semibold text-trim" : "text-trim-dim";
                    return (
                      <tr key={r} className="border-t border-mortar/50">
                        <td className="px-3 py-1.5 text-trim-faint">{a?.darts.map(dartLabel).join("  ")}</td>
                        <td className={`tnum px-2 text-right ${scoreCls(a)}`}>{a ? a.scored : ""}</td>
                        <td className="tnum px-2 text-right text-trim">{a?.remainingAfter ?? ""}</td>
                        <td className="tnum px-2 text-center text-xs text-trim-faint">{r}</td>
                        <td className="tnum px-2 text-trim">{b?.remainingAfter ?? ""}</td>
                        <td className={`tnum px-2 ${scoreCls(b)}`}>{b ? b.scored : ""}</td>
                        <td className="px-3 text-right text-trim-faint">{b?.darts.map(dartLabel).join("  ")}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
        );
      })}
    </div>
  );
}

/** Score distribution, heatmaps, race chart and finishing doubles. */
export function MatchAnalysisPanel({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  const q = useMatchBreakdown(match);
  const [leg, setLeg] = useState<number | null>(null);
  if (q.isLoading) return <Spinner />;
  if (!q.data) return <ErrorNotice error={q.error} />;
  const b: MatchBreakdown = q.data;
  const names: [string, string] = [match.players[0].realName, match.players[1].realName];
  const raceLeg = b.legs.find((l) => l.legNo === (leg ?? b.legs[b.legs.length - 1]?.legNo));

  return (
    <div className="mt-6 flex flex-col gap-6">
      <section className="panel p-5">
        <h3 className="font-display text-xl font-bold uppercase">{t("analysis.scoreBands")}</h3>
        <p className="mb-3 text-sm text-trim-faint">{t("analysis.scoreBandsHint")}</p>
        <BandChart series={b.players.map((p, i) => ({ name: names[i], bands: p.bands }))} />
      </section>

      {raceLeg && (
        <section className="panel p-5">
          <div className="flex flex-wrap items-baseline justify-between gap-3">
            <h3 className="font-display text-xl font-bold uppercase">{t("analysis.race")}</h3>
            {b.legs.length > 1 && (
              <div className="flex gap-1">
                {b.legs.map((l) => (
                  <button
                    key={l.legNo}
                    onClick={() => setLeg(l.legNo)}
                    aria-pressed={raceLeg.legNo === l.legNo}
                    className={`rounded px-2.5 py-1 text-xs font-bold ${raceLeg.legNo === l.legNo ? "bg-trafon text-trim" : "bg-masonry-2 text-trim-dim"}`}
                  >
                    {l.legNo}
                  </button>
                ))}
              </div>
            )}
          </div>
          <p className="mb-3 text-sm text-trim-faint">{t("analysis.raceHint", { n: raceLeg.legNo })}</p>
          <RaceChart turns={raceLeg.turns} names={names} start={match.config.startScore} />
        </section>
      )}

      <section>
        <h3 className="font-display text-xl font-bold uppercase">{t("analysis.heatmap")}</h3>
        <p className="mb-3 text-sm text-trim-faint">{t("analysis.heatmapHint")}</p>
        <div className="grid gap-4 md:grid-cols-2">
          {b.players.map((p, i) => (
            <div key={i} className="panel flex flex-col items-center gap-4 p-5">
              <h4 className="font-display text-lg font-bold uppercase">{names[i]}</h4>
              <DartboardHeatmap cells={p.heatmap} size={280} />
              <dl className="grid w-full grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                <Mini label={t("analysis.trebles")} value={pct(p.trebleRate)} />
                <Mini label={t("analysis.doubles")} value={pct(p.doubleRate)} />
                <Mini label={t("analysis.missRate")} value={pct(p.missRate)} />
                <Mini label={t("analysis.busts")} value={num(p.busts)} />
              </dl>
              {(p.bestLeg || p.finishes.length > 0) && (
                <p className="w-full text-sm text-trim-dim">
                  {p.bestLeg && (
                    <>
                      {t("analysis.bestLeg")}: <b className="text-trim">{t("analysis.bestLegValue", { count: p.bestLeg })}</b>.{" "}
                    </>
                  )}
                  {p.finishes.length > 0 && (
                    <>
                      {t("analysis.finishes")}: <b className="text-trim">{p.finishes.map((f) => dartLabel(f.label)).join(", ")}</b>
                    </>
                  )}
                </p>
              )}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function Mini({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-night/60 px-3 py-2">
      <dt className="text-xs text-trim-faint">{label}</dt>
      <dd className="font-display text-2xl font-bold">{value}</dd>
    </div>
  );
}

/** Win probability before the match and the full record between the two players. */
export function MatchHeadToHead({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  const b = useMatchBreakdown(match);
  const [p1, p2] = match.players;
  const h2h = useQuery({
    queryKey: ["h2h-pair", p1.username, p2.username],
    queryFn: () => api.get<HeadToHeadRow>(`/api/users/${encodeURIComponent(p1.username)}/head-to-head/${encodeURIComponent(p2.username)}`),
  });
  const prob = b.data?.winProbability;

  return (
    <div className="mt-6 grid gap-6 lg:grid-cols-2">
      {prob && (
        <section className="panel p-5">
          <h3 className="font-display text-xl font-bold uppercase">{t("analysis.winProbability")}</h3>
          <p className="mb-4 text-sm text-trim-faint">{t("analysis.winProbabilityHint")}</p>
          <div className="mb-2 flex justify-between font-display text-3xl font-extrabold">
            <span>{pct(prob[0], 0)}</span>
            <span className="text-trim-dim">{pct(prob[1], 0)}</span>
          </div>
          <div className="flex h-3 overflow-hidden rounded-full bg-night" role="img" aria-label={`${p1.realName} ${pct(prob[0], 0)}, ${p2.realName} ${pct(prob[1], 0)}`}>
            <span className="h-full bg-[#d9735c]" style={{ width: `${prob[0]}%` }} />
            <span className="h-full w-0.5 bg-night" />
            <span className="h-full flex-1 bg-[#4c94cc]" />
          </div>
          <div className="mt-2 flex justify-between text-sm text-trim-dim">
            <span>{p1.realName}</span>
            <span>{p2.realName}</span>
          </div>
        </section>
      )}
      <section className="panel p-5">
        <h3 className="mb-4 font-display text-xl font-bold uppercase">{t("analysis.h2hRecord")}</h3>
        {h2h.isLoading ? (
          <Spinner />
        ) : !h2h.data || h2h.data.played === 0 ? (
          <p className="text-trim-dim">{t("analysis.h2hNone")}</p>
        ) : (
          <>
            <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 text-center">
              <span className="truncate font-semibold">{p1.realName}</span>
              <span className="font-display text-5xl font-extrabold">
                {h2h.data.won}
                <span className="mx-1 text-trim-faint">–</span>
                {h2h.data.lost}
              </span>
              <span className="truncate font-semibold">{p2.realName}</span>
            </div>
            <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
              <Mini label={t("analysis.h2hLegs")} value={`${h2h.data.legsWon}–${h2h.data.legsLost}`} />
              <Mini label={t("analysis.h2hAverages")} value={`${num(h2h.data.myAverage, 1)} / ${num(h2h.data.theirAverage, 1)}`} />
            </dl>
          </>
        )}
        <Link to={`/players/${p1.username}`} className="mt-4 inline-block text-sm font-semibold text-trim-dim underline hover:text-trim">
          {t("analysis.openProfiles")}
        </Link>
      </section>
    </div>
  );
}
