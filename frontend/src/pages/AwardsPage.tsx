import { Crown } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { useAwardRace, useHallOfFame, useYearRace, useYears } from "../api/queries";
import type { AwardRace, AwardType, AwardView, MonthResult, Standing } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { EmptyState, ErrorNotice, PageHeader, SectionTitle, Spinner } from "../components/ui";
import { AWARD_ORDER, Medal, useAwardValue } from "../features/awards/Medal";
import { monthName, num, signed } from "../lib/format";
import { usePageTitle } from "../lib/pageTitle";

export function AwardsPage() {
  const { t } = useTranslation();
  usePageTitle(t("awards.title"));
  const race = useAwardRace();
  const hall = useHallOfFame();

  if (race.isLoading) return <Spinner />;
  if (race.error || !race.data) return <ErrorNotice error={race.error} onRetry={() => race.refetch()} />;
  const r = race.data;
  const months = hall.data ?? [];
  const latest = months.find((m) => m.awards.length > 0);

  return (
    <div className="flex flex-col gap-12">
      <PageHeader title={t("awards.title")} lead={t("awards.lead")} />

      {latest && <Champions month={latest} />}

      <section>
        <SectionTitle action={<span className="text-sm font-semibold text-trim-dim">{t("awards.daysLeft", { count: daysLeft(r.endsAt) })}</span>}>
          {t("awards.raceTitle", { month: monthName(r.period) })}
        </SectionTitle>
        {r.standings.length === 0 ? <EmptyState title={t("awards.raceTitle", { month: monthName(r.period) })}>{t("awards.noRace")}</EmptyState> : <RaceTable race={r} />}
      </section>

      <Leaders race={r} />

      <YearSection />

      <div className="grid gap-10 lg:grid-cols-[1.2fr_1fr] [&>*]:min-w-0">
        <section>
          <SectionTitle>{t("awards.hallTitle")}</SectionTitle>
          {hall.error ? <ErrorNotice error={hall.error} onRetry={() => hall.refetch()} /> : hall.isLoading ? <Spinner /> : <HallOfFame months={months} />}
        </section>
        <HowItWorks minMatches={r.minMatches} />
      </div>
    </div>
  );
}

function daysLeft(endsAt: string): number {
  return Math.max(0, Math.ceil((new Date(endsAt).getTime() - Date.now()) / 86_400_000));
}

/** Last decided month: the champion in a brass arch, the side awards beside it. */
function Champions({ month }: { month: MonthResult }) {
  const { t } = useTranslation();
  const value = useAwardValue();
  const potm = month.awards.find((a) => a.award === "PLAYER_OF_MONTH");
  const rest = month.awards.filter((a) => a.award !== "PLAYER_OF_MONTH");
  return (
    <section className="grid items-end gap-8 md:grid-cols-[auto_1fr]">
      <div className="arch mx-auto flex w-72 flex-col items-center border-2 border-t-[6px] border-brass bg-masonry px-6 pb-6 pt-12 text-center md:mx-0">
        <Crown className="size-10 text-brass" aria-hidden />
        <p className="mt-3 text-sm font-semibold text-brass">{t("awards.reigning", { month: monthName(month.period) })}</p>
        {potm?.player ? (
          <>
            <Link to={`/players/${potm.player.username}`} className="mt-2 font-display text-4xl leading-[0.95] hover:underline">
              {potm.player.realName}
            </Link>
            <p className="mt-2 text-sm text-trim-dim">{t("awards.reigningPoints", { points: num(Math.round(potm.value)) })}</p>
          </>
        ) : (
          <p className="mt-2 text-trim-dim">{t("awards.nobody")}</p>
        )}
      </div>
      {rest.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2">
          {rest.map((a) => (
            <li key={a.award} className="panel flex items-center gap-3 p-3">
              <Medal award={a.award} />
              <div className="min-w-0 flex-1">
                <p className="text-xs font-semibold text-trim-faint">{t(`awards.types.${a.award}`)}</p>
                {a.player ? (
                  <Link to={`/players/${a.player.username}`} className="block truncate font-semibold hover:underline">
                    {a.player.realName}
                  </Link>
                ) : (
                  <span className="text-trim-faint">–</span>
                )}
              </div>
              <span className="tnum text-sm text-trim-dim">{value(a.award, a.value)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

const PARTS = ["rating", "activity", "wins", "improvement", "highlights", "kudos"] as const;

function RaceTable({ race }: { race: AwardRace }) {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const top = Math.max(1, ...race.standings.map((s) => s.points.total));
  return (
    <ol className="panel divide-y divide-mortar/60">
      {race.standings.map((s) => {
        const mine = me?.id === s.player.id;
        const missing = Math.max(0, race.minMatches - s.stats.matches);
        return (
          <li key={s.player.id} className={`grid grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2 px-4 py-3 ${mine ? "bg-masonry-2/60" : ""}`}>
            <span className={`numerals text-2xl font-bold ${s.eligible && s.rank === 1 ? "text-brass" : "text-trim-faint"}`}>{s.eligible ? s.rank : "–"}</span>
            <div className="min-w-0">
              <Link to={`/players/${s.player.username}`} className="block truncate font-semibold hover:underline">
                {s.player.realName}
              </Link>
              <p className="truncate text-xs text-trim-faint">
                {s.eligible ? <Breakdown s={s} /> : t("awards.needsMore", { count: missing })}
              </p>
            </div>
            <span className={`numerals text-right text-3xl font-bold leading-none ${s.eligible ? "" : "text-trim-dim"}`}>
              {num(Math.round(s.points.total))}
              <span className="ml-1 font-sans text-xs font-semibold text-trim-faint">{t("awards.points").toLowerCase()}</span>
            </span>
            <div className="col-span-3 col-start-1 sm:col-span-2 sm:col-start-2">
              <div className="h-1.5 overflow-hidden rounded-full bg-night">
                <div
                  className={`h-full rounded-full ${s.eligible ? (s.rank === 1 ? "bg-brass" : "bg-trafon") : "bg-mortar"}`}
                  style={{ width: `${(Math.max(0, s.points.total) / top) * 100}%` }}
                />
              </div>
              <p className="mt-2 text-xs text-trim-dim">
                {t("awards.matches")} {s.stats.matches}, {t("awards.wins").toLowerCase()} {s.stats.wins}
                {s.stats.ratingChange !== null && `, ${t("awards.ratingChange").toLowerCase()} ${signed(s.stats.ratingChange)}`}
                {s.stats.priorAverage !== null && s.stats.monthAverage > 0 && `, ${t("awards.avgChange").toLowerCase()} ${signed(s.stats.monthAverage - s.stats.priorAverage, 1)}`}
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}

/** "aktivitet 20, seire 12, fremgang 8" – the biggest contributors first, zeros left out. */
function Breakdown({ s }: { s: Standing }) {
  const { t } = useTranslation();
  const parts = PARTS.map((k) => ({ k, v: Math.round(s.points[k]) }))
    .filter((p) => p.v !== 0)
    .sort((a, b) => Math.abs(b.v) - Math.abs(a.v));
  return <>{parts.map((p) => `${t(`awards.breakdown.${p.k}`)} ${signed(p.v)}`).join(", ")}</>;
}

function Leaders({ race }: { race: AwardRace }) {
  const { t } = useTranslation();
  const value = useAwardValue();
  const byAward = new Map(race.leaders.map((l) => [l.award, l]));
  return (
    <section>
      <SectionTitle>{t("awards.leadersTitle")}</SectionTitle>
      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {AWARD_ORDER.map((award) => {
          const l = byAward.get(award);
          return (
            <li key={award} className={`panel flex flex-col items-center gap-2 px-3 pb-4 pt-5 text-center ${award === "PLAYER_OF_MONTH" ? "col-span-2 sm:col-span-1" : ""}`}>
              <Medal award={award} size="lg" />
              <p className="mt-1 font-display text-lg leading-tight">{t(`awards.types.${award}`)}</p>
              <p className="text-xs text-trim-faint">{t(`awards.typeHints.${award}`)}</p>
              {l ? (
                <>
                  <Link to={`/players/${l.player.username}`} className="mt-1 font-semibold hover:underline">
                    {l.player.realName}
                  </Link>
                  <span className="tnum text-sm text-trim-dim">{value(award, l.value)}</span>
                </>
              ) : (
                <span className="mt-1 text-trim-faint">–</span>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function HallOfFame({ months }: { months: MonthResult[] }) {
  const { t } = useTranslation();
  if (months.length === 0) return <p className="text-trim-dim">{t("awards.noHall")}</p>;
  return (
    <ol className="flex flex-col gap-3">
      {months.map((m) => (
        <li key={m.period} className="panel p-4">
          <h3 className="font-display text-xl">{monthName(m.period)}</h3>
          {m.awards.length === 0 ? (
            <p className="mt-1 text-sm text-trim-faint">{t("awards.nobody")}</p>
          ) : (
            <ul className="mt-3 flex flex-col gap-2">
              {AWARD_ORDER.map((award) => m.awards.find((a) => a.award === award))
                .filter((a): a is AwardView => !!a)
                .map((a) => (
                  <HallRow key={a.award} a={a} />
                ))}
            </ul>
          )}
        </li>
      ))}
    </ol>
  );
}

function HallRow({ a }: { a: AwardView }) {
  const { t } = useTranslation();
  const value = useAwardValue();
  return (
    <li className="flex items-center gap-3">
      <Medal award={a.award} size="sm" />
      <span className="w-36 shrink-0 text-sm text-trim-dim">{t(`awards.types.${a.award}`)}</span>
      {a.player ? (
        <Link to={`/players/${a.player.username}`} className={`min-w-0 flex-1 truncate hover:underline ${a.award === "PLAYER_OF_MONTH" ? "font-bold text-brass" : "font-semibold"}`}>
          {a.player.realName}
        </Link>
      ) : (
        <span className="flex-1 text-trim-faint">–</span>
      )}
      <span className="tnum hidden text-sm text-trim-faint sm:inline">{value(a.award as AwardType, a.value)}</span>
    </li>
  );
}

function HowItWorks({ minMatches }: { minMatches: number }) {
  const { t } = useTranslation();
  return (
    <section>
      <SectionTitle>{t("awards.howTitle")}</SectionTitle>
      <div className="panel p-5">
        <ul className="flex flex-col gap-3 text-sm text-trim-dim">
          {PARTS.map((k) => (
            <li key={k} className="grid grid-cols-[6.5rem_1fr] gap-3">
              <span className="font-semibold capitalize text-trim">{t(`awards.breakdown.${k}`)}</span>
              <span>{t(`awards.how.${k}`)}</span>
            </li>
          ))}
        </ul>
        <p className="mt-5 border-t border-mortar pt-4 text-sm text-trim-dim">{t("awards.how.eligible", { count: minMatches })}</p>
      </div>
    </section>
  );
}

/** Player of the Year: the sum of the year's month points (rated matches), crowned on 1 January. */
function YearSection() {
  const { t } = useTranslation();
  const race = useYearRace();
  const years = useYears();
  const { me } = useCurrentUser();
  if (!race.data) return null;
  const r = race.data;
  const top = r.standings.slice(0, 8);
  const past = (years.data ?? []).filter((y) => y.award?.player);
  return (
    <section className="grid gap-8 rounded-xl border border-brass/40 bg-masonry p-5 lg:grid-cols-[1.4fr_1fr] [&>*]:min-w-0">
      <div>
        <SectionTitle>{t("awards.yearRaceTitle", { year: r.year })}</SectionTitle>
        <p className="mb-3 text-sm text-trim-dim">{t("awards.yearRaceLead", { count: r.minMatches })}</p>
        {top.length === 0 ? (
          <p className="text-trim-faint">{t("awards.noRace")}</p>
        ) : (
          <ol className="flex flex-col gap-1">
            {top.map((s) => (
              <li key={s.player.id} className={`flex items-center gap-3 rounded-md px-3 py-2 ${me?.id === s.player.id ? "bg-masonry-2" : ""} ${s.eligible ? "" : "opacity-60"}`}>
                <span className={`numerals w-6 text-xl font-bold ${s.rank === 1 && s.eligible ? "text-brass" : "text-trim-faint"}`}>{s.eligible ? s.rank : "–"}</span>
                <Link to={`/players/${s.player.username}`} className="min-w-0 flex-1 truncate font-semibold hover:underline">
                  {s.player.realName}
                </Link>
                <span className="hidden text-xs text-trim-faint sm:inline">{t("awards.yearMatches", { count: s.matches })}</span>
                {s.monthsWon > 0 && (
                  <span className="flex items-center gap-0.5 text-xs font-bold text-brass" title={t("awards.monthsWon", { count: s.monthsWon })}>
                    <Crown className="size-3.5" aria-hidden />×{s.monthsWon}
                  </span>
                )}
                <span className="tnum w-16 text-right font-semibold">{num(Math.round(s.points))}</span>
              </li>
            ))}
          </ol>
        )}
      </div>
      <div>
        <SectionTitle>{t("awards.yearHallTitle")}</SectionTitle>
        {past.length === 0 ? (
          <p className="text-trim-dim">{t("awards.yearHallEmpty")}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {past.map((y) => (
              <li key={y.year} className="panel flex items-center gap-3 px-4 py-3">
                <Medal award="PLAYER_OF_YEAR" size="md" />
                <span className="numerals text-3xl font-bold text-brass">{y.year}</span>
                <Link to={`/players/${y.award!.player!.username}`} className="flex-1 truncate font-display text-2xl hover:underline">
                  {y.award!.player!.realName}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
