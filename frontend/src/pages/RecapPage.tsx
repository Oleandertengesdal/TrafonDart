import { ArrowLeft, Crown, Flame, Footprints, Skull, Star, Swords, Target, Trophy, Zap } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams, useSearchParams } from "react-router";
import { useProfile, useRecap } from "../api/queries";
import type { Recap } from "../api/types";
import { ErrorNotice, Spinner } from "../components/ui";
import { AchievementIcon } from "../features/achievements/Cabinet";
import { recapYears, walkedKm, winRate } from "../features/recap/recapMath";
import { dartLabel, date, monthName, num, pct } from "../lib/format";
import { intlLocale } from "../i18n";
import { NotFound } from "./NotFoundPage";
import { isNotFound } from "../lib/errors";
import { usePageTitle } from "../lib/pageTitle";

/** "Your year in darts": a scrollable stack of cards, Wrapped-style. Works for any year, "so far" for this one. */
export function RecapPage() {
  const { username = "" } = useParams();
  const [params, setParams] = useSearchParams();
  const { t } = useTranslation();
  const profile = useProfile(username);
  const years = recapYears(profile.data?.memberSince);
  const year = Number(params.get("year")) || years[0];
  const q = useRecap(username, year);
  const name = profile.data?.player.realName;
  usePageTitle(name && t("recap.title", { name, year }));
  if (isNotFound(profile.error) || isNotFound(q.error)) return <NotFound what="player" />;

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link to={`/players/${username}`} className="flex items-center gap-2 text-sm font-semibold text-trim-dim hover:text-trim">
          <ArrowLeft className="size-4" aria-hidden />
          {t("recap.backToProfile")}
        </Link>
        {years.length > 1 && (
          <div role="group" aria-label={t("recap.year")} className="flex flex-wrap gap-1 rounded-md border border-mortar p-0.5 text-sm font-bold">
            {years.map((y) => (
              <button key={y} aria-pressed={y === year} onClick={() => setParams({ year: String(y) })} className={`rounded px-3 py-1 ${y === year ? "bg-trim text-night" : "text-trim-dim"}`}>
                {y}
              </button>
            ))}
          </div>
        )}
      </div>
      {q.isLoading ? <Spinner /> : q.error || !q.data ? <ErrorNotice error={q.error} onRetry={() => q.refetch()} resource /> : <RecapCards recap={q.data} />}
    </div>
  );
}

function Card({ icon, tone = "trafon", title, big, children }: { icon: ReactNode; tone?: "trafon" | "brass" | "board" | "salmon"; title: string; big?: ReactNode; children?: ReactNode }) {
  const tones = {
    trafon: "border-trafon/60 bg-trafon/15",
    brass: "border-brass/50 bg-brass/10",
    board: "border-board/60 bg-board/15",
    salmon: "border-salmon/50 bg-salmon/10",
  };
  return (
    <section className={`overflow-hidden rounded-xl border p-6 ${tones[tone]}`}>
      <p className="flex items-center gap-2 text-sm font-bold text-trim-dim">
        {icon}
        {title}
      </p>
      {big !== undefined && <p className="numerals mt-3 text-score-md font-extrabold leading-[0.9]">{big}</p>}
      {children && <div className="mt-3 text-lg text-trim-dim">{children}</div>}
    </section>
  );
}

function RecapCards({ recap: r }: { recap: Recap }) {
  const { t } = useTranslation();
  const name = r.player.realName;
  const nothing = r.matches.played === 0 && r.games.played === 0;
  const weekday = r.busiestWeekday ? new Intl.DateTimeFormat(intlLocale(), { weekday: "long", timeZone: "UTC" }).format(new Date(Date.UTC(2024, 0, r.busiestWeekday))) : null;

  return (
    <>
      <header className="rounded-2xl border border-mortar bg-masonry p-6">
        <p className="text-sm font-bold text-trafon-bright">{r.soFar ? t("recap.soFar") : t("recap.final")}</p>
        <h1 className="mt-2 font-display text-5xl leading-none sm:text-6xl">{t("recap.title", { name, year: r.year })}</h1>
      </header>

      {nothing ? (
        <p className="text-lg text-trim-dim">{t("recap.nothing", { year: r.year })}</p>
      ) : (
        <>
          {r.playerOfTheYear && (
            <Card icon={<Star className="size-4" aria-hidden />} tone="brass" title={t("recap.poyTitle")} big={r.year}>
              {t("recap.poyLead", { name })}
            </Card>
          )}

          <Card icon={<Footprints className="size-4" aria-hidden />} title={t("recap.dartsTitle")} big={num(r.dartsThrown)}>
            {t("recap.dartsLead", { km: num(walkedKm(r.dartsThrown), 1) })}
          </Card>

          {r.matches.played > 0 && (
            <Card icon={<Swords className="size-4" aria-hidden />} tone="board" title={t("recap.matchesTitle")} big={`${r.matches.won}–${r.matches.lost}`}>
              {t("recap.matchesLead", { played: r.matches.played, rate: pct(winRate(r.matches.won, r.matches.played) * 100, 0), avg: num(r.average, 1) })}
              {r.longestWinStreak > 1 && <> {t("recap.streak", { count: r.longestWinStreak })}</>}
            </Card>
          )}

          {r.bestMonth && (
            <Card icon={<Crown className="size-4" aria-hidden />} tone="brass" title={t("recap.bestMonthTitle")} big={monthName(r.bestMonth.period).split(" ")[0]}>
              {t("recap.bestMonthLead", { points: num(Math.round(r.bestMonth.points)), rank: r.bestMonth.rank })}
              {r.monthsWon > 0 && <> {t("recap.monthsWon", { count: r.monthsWon })}</>}
            </Card>
          )}

          {r.favouriteFinish && (
            <Card icon={<Target className="size-4" aria-hidden />} tone="salmon" title={t("recap.finishTitle")} big={dartLabel(r.favouriteFinish.label)}>
              {t("recap.finishLead", { count: r.favouriteFinish.count })}
              {r.favouriteNumber && <> {t("recap.numberLead", { number: r.favouriteNumber.label === "BULL" ? t("games.bull") : r.favouriteNumber.label })}</>}
            </Card>
          )}

          {(r.nemesis || r.favouriteVictim) && (
            <section className="grid gap-5 sm:grid-cols-2">
              {r.nemesis && (
                <Card icon={<Skull className="size-4" aria-hidden />} title={t("recap.nemesisTitle")}>
                  <span className="block font-display text-4xl leading-none text-trim">{r.nemesis.player.realName}</span>
                  <span className="mt-2 block">{t("recap.nemesisLead", { won: r.nemesis.won, lost: r.nemesis.lost })}</span>
                </Card>
              )}
              {r.favouriteVictim && (
                <Card icon={<Flame className="size-4" aria-hidden />} tone="board" title={t("recap.victimTitle")}>
                  <span className="block font-display text-4xl leading-none text-trim">{r.favouriteVictim.player.realName}</span>
                  <span className="mt-2 block">{t("recap.victimLead", { won: r.favouriteVictim.won, lost: r.favouriteVictim.lost })}</span>
                </Card>
              )}
            </section>
          )}

          {r.biggestWin && (
            <Card icon={<Trophy className="size-4" aria-hidden />} tone="brass" title={t("recap.biggestWinTitle")}>
              <Link to={`/matches/${r.biggestWin.matchId}`} className="block hover:underline">
                <span className="block font-display text-4xl leading-none text-trim">{r.biggestWin.opponent?.realName}</span>
                <span className="mt-2 block">{t("recap.biggestWinLead", { rating: r.biggestWin.opponentRating ?? "–", date: date(r.biggestWin.at) })}</span>
              </Link>
            </Card>
          )}

          {r.bestMatch && (
            <Card icon={<Zap className="size-4" aria-hidden />} title={t("recap.bestMatchTitle")} big={num(r.bestMatch.average, 1)}>
              <Link to={`/matches/${r.bestMatch.matchId}`} className="hover:underline">
                {t("recap.bestMatchLead", { name: r.bestMatch.opponent?.realName ?? "?", date: date(r.bestMatch.at) })}
              </Link>
            </Card>
          )}

          {(r.count180 > 0 || r.highestCheckout > 0) && (
            <section className="grid grid-cols-2 gap-5 sm:grid-cols-4">
              {[
                { label: "180", value: r.count180 },
                { label: "140+", value: r.count140 },
                { label: t("recap.highestCheckout"), value: r.highestCheckout },
                { label: t("recap.highestTurn"), value: r.highestTurn },
              ].map((x) => (
                <div key={x.label} className="panel p-4">
                  <p className="text-xs font-bold text-trim-faint">{x.label}</p>
                  <p className="numerals text-5xl font-extrabold">{x.value}</p>
                </div>
              ))}
            </section>
          )}

          {weekday && (
            <Card icon={<Target className="size-4" aria-hidden />} title={t("recap.weekdayTitle")}>
              <span className="block font-display text-4xl leading-none text-trim">{weekday}</span>
            </Card>
          )}

          {r.games.played > 0 && (
            <Card icon={<Swords className="size-4" aria-hidden />} tone="salmon" title={t("recap.gamesTitle")} big={r.games.played}>
              {t("recap.gamesLead", { won: r.games.won })}{" "}
              {r.games.byType.map((g) => `${t(`games.types.${g.type}`)} ${g.played}`).join(", ")}
            </Card>
          )}

          {r.yearRank && (
            <Card icon={<Crown className="size-4" aria-hidden />} tone="brass" title={t("recap.yearRankTitle")} big={`#${r.yearRank}`}>
              {t("recap.yearRankLead", { points: num(Math.round(r.yearPoints ?? 0)) })}
            </Card>
          )}

          {r.achievements.length > 0 && (
            <section className="rounded-2xl border border-mortar bg-masonry p-6">
              <p className="text-sm font-bold text-trim-dim">{t("recap.achievementsTitle", { count: r.achievements.length })}</p>
              <ul className="mt-3 flex flex-wrap gap-2">
                {r.achievements.map((code) => (
                  <li key={code} className="inline-flex items-center gap-2 rounded-full border border-brass/50 bg-brass/10 py-1 pl-2 pr-3 text-sm font-semibold">
                    <AchievementIcon code={code} unlocked size="sm" />
                    {t(`achievements.${code}.title`)}
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </>
  );
}
