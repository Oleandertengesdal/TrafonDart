import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { BarChart3, Sparkles } from "lucide-react";
import { useBadges, useGameStats, useHeadToHead, useLeaderboard, useProfile, useRecentGames, useRecentMatches } from "../api/queries";
import type { AwardType, AwardView, ProfileStats } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { MatchRow } from "../components/MatchRow";
import { FormDots } from "../components/PlayerName";
import { EmptyState, ErrorNotice, LinkButton, SectionTitle, Spinner } from "../components/ui";
import { AchievementCabinet } from "../features/achievements/Cabinet";
import { ChallengeButton } from "../features/challenges/Challenges";
import { sideLabel } from "../features/games/gameLogic";
import { AverageChart, RatingChart } from "../features/stats/Charts";
import { DartNerd } from "../features/stats/DartNerd";
import { BotRecordSection } from "../features/bot/BotRecordSection";
import { AWARD_ORDER, Medal, ReigningChip, useAwardValue, useReigning } from "../features/awards/Medal";
import { date, monthName, num, pct, relative } from "../lib/format";
import { NotFound } from "./NotFoundPage";
import { isNotFound } from "../lib/errors";
import { usePageTitle } from "../lib/pageTitle";

export function ProfilePage() {
  const { username = "" } = useParams();
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const profile = useProfile(username);
  const h2h = useHeadToHead(username);
  const recent = useRecentMatches(username, 10);
  const board = useLeaderboard();
  const badges = useBadges(username);
  const reigning = useReigning();
  usePageTitle(profile.data?.player.realName);

  if (profile.isLoading) return <Spinner />;
  if (profile.error || !profile.data) {
    return isNotFound(profile.error) ? <NotFound what="player" /> : <ErrorNotice error={profile.error} onRetry={() => profile.refetch()} resource />;
  }
  const s = profile.data;
  const rank = board.data?.find((r) => r.player.id === s.player.id)?.rank;

  return (
    <div className="flex flex-col gap-10">
      <Hero s={s} rank={rank} reigningPeriod={reigning?.id === s.player.id ? reigning.period : undefined} isMe={me?.id === s.player.id} />

      {s.matchesPlayed === 0 ? (
        <EmptyState title={t("stats.matches")}>{t("stats.noMatchesYet")}</EmptyState>
      ) : (
        <>
          <StatGrid s={s} />
          <div className="grid gap-6 lg:grid-cols-2">
            <section className="panel p-5">
              <SectionTitle>{t("stats.ratingHistory")}</SectionTitle>
              <RatingChart points={s.ratingHistory} />
            </section>
            <section className="panel p-5">
              <SectionTitle>{t("stats.averageTrend", { count: s.averageTrend.length })}</SectionTitle>
              <AverageChart points={s.averageTrend} />
            </section>
          </div>
          <DartNerd username={s.player.username} />
        </>
      )}

      {(badges.data ?? []).length > 0 ? (
        <TrophyCabinet badges={badges.data!} />
      ) : (
        me?.id === s.player.id &&
        badges.isSuccess && (
          <section>
            <SectionTitle>{t("awards.cabinet")}</SectionTitle>
            <p className="text-trim-dim">
              {t("awards.cabinetEmpty")}{" "}
              <Link to="/awards" className="font-semibold text-brass hover:underline">
                {t("awards.seeRace")}
              </Link>
            </p>
          </section>
        )
      )}

      <AchievementCabinet username={s.player.username} />

      <PartyGames username={s.player.username} playerId={s.player.id} />

      <BotRecordSection username={s.player.username} isMe={me?.id === s.player.id} />

      <div className="grid gap-10 lg:grid-cols-[1.2fr_1fr] [&>*]:min-w-0">
        <section>
          <SectionTitle>{t("stats.headToHead")}</SectionTitle>
          {h2h.isLoading ? (
            <Spinner />
          ) : (h2h.data ?? []).length === 0 ? (
            <p className="text-trim-dim">–</p>
          ) : (
            <div className="panel overflow-x-auto">
              <table className="w-full min-w-[30rem] text-sm">
                <thead className="text-left text-xs text-trim-faint">
                  <tr>
                    <th className="px-4 py-3 font-semibold">{t("stats.opponent")}</th>
                    <th className="px-2 py-3 text-right font-semibold">{t("stats.played")}</th>
                    <th className="px-2 py-3 text-right font-semibold">{t("stats.won")}</th>
                    <th className="px-2 py-3 text-right font-semibold">{t("stats.legsShort")}</th>
                    <th className="px-2 py-3 text-right font-semibold">{t("stats.myAvg")}</th>
                    <th className="px-4 py-3 text-right font-semibold">{t("stats.lastPlayed")}</th>
                  </tr>
                </thead>
                <tbody>
                  {h2h.data!.map((r) => {
                    const ahead = r.won > r.lost;
                    return (
                      <tr key={r.opponent.id} className="border-t border-mortar/60">
                        <td className="px-4 py-3">
                          <Link to={`/players/${r.opponent.username}`} className="font-semibold hover:underline">
                            {r.opponent.realName}
                          </Link>
                        </td>
                        <td className="tnum px-2 text-right">{r.played}</td>
                        <td className={`tnum px-2 text-right font-bold ${ahead ? "text-board-bright" : r.won < r.lost ? "text-salmon" : "text-trim"}`}>
                          {r.won}–{r.lost}
                        </td>
                        <td className="tnum px-2 text-right text-trim-dim">
                          {r.legsWon}–{r.legsLost}
                        </td>
                        <td className="tnum whitespace-nowrap px-2 text-right">
                          {num(r.myAverage, 1)} <span className="text-trim-faint">/ {num(r.theirAverage, 1)}</span>
                        </td>
                        <td className="whitespace-nowrap px-4 text-right text-trim-faint">{relative(r.lastPlayed)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
        <section>
          <SectionTitle>{t("stats.recentMatches")}</SectionTitle>
          {recent.isLoading ? <Spinner /> : (
            <ul className="panel divide-y divide-mortar/60 p-1">
              {(recent.data ?? []).map((m) => (
                <MatchRow key={m.id} match={m} perspective={s.player.id} />
              ))}
            </ul>
          )}
        </section>
      </div>
      {me?.username.toLowerCase() === username.toLowerCase() && (
        <p className="text-sm text-trim-faint">
          <Link to="/settings" className="underline">
            {t("nav.settings")}
          </Link>
        </p>
      )}
    </div>
  );
}

function Hero({ s, rank, reigningPeriod, isMe }: { s: ProfileStats; rank?: number; reigningPeriod?: string; isMe: boolean }) {
  const { t } = useTranslation();
  return (
    <header className="grid items-end gap-6 border-b border-mortar pb-8 md:grid-cols-[1fr_auto]">
      <div>
        <p className="text-sm text-trim-faint">
          @{s.player.username}, {t("stats.memberSince", { date: date(s.memberSince) })}
        </p>
        <h1 className="mt-1 font-display text-score-sm leading-[0.9] tracking-tight">{s.player.realName}</h1>
        {reigningPeriod && (
          <div className="mt-3">
            <ReigningChip period={reigningPeriod} />
          </div>
        )}
        <div className="mt-4 flex flex-wrap gap-2">
          <LinkButton to={`/players/${s.player.username}/recap`} variant="brass">
            <Sparkles className="size-4" aria-hidden />
            {t("recap.link", { year: new Date().getFullYear() })}
          </LinkButton>
          {!isMe && <ChallengeButton opponent={s.player} />}
        </div>
        {s.recentForm.length > 0 && (
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <FormDots form={s.recentForm} />
            {s.currentStreak !== 0 && (
              <span className={`text-sm font-semibold ${s.currentStreak > 0 ? "text-board-bright" : "text-salmon"}`}>
                {s.currentStreak > 0 ? t("stats.streakWin", { count: s.currentStreak }) : t("stats.streakLoss", { count: -s.currentStreak })}
              </span>
            )}
          </div>
        )}
      </div>
      <div className="arch flex min-w-52 flex-col items-center border-2 border-trafon bg-masonry px-8 pb-4 pt-10 text-center">
        <span className="text-xs font-bold text-trim-faint">{t("stats.rating")}</span>
        <span className="numerals text-7xl font-extrabold leading-none">{s.player.rating}</span>
        <span className="mt-1 text-xs text-trim-faint">
          ±{s.player.rd * 2}
          {s.player.provisional && `, ${t("common.provisional").toLowerCase()}`}
        </span>
        <span className="mt-2 text-sm text-trim-dim">
          {rank ? `${t("stats.rank", { rank })}, ` : ""}
          {t("stats.peakRating")} {s.peakRating}
        </span>
      </div>
    </header>
  );
}

function StatGrid({ s }: { s: ProfileStats }) {
  const { t } = useTranslation();
  const tiles: { label: string; value: string; sub?: string; accent?: boolean }[] = [
    { label: t("stats.matches"), value: t("stats.record", { won: s.matchesWon, lost: s.matchesLost }), sub: `${t("stats.winRate")} ${pct(s.winRate)}` },
    { label: t("stats.legs"), value: t("stats.record", { won: s.legsWon, lost: s.legsLost }), sub: `${t("stats.legWinRate")} ${pct(s.legWinRate)}` },
    { label: t("stats.average"), value: num(s.average, 2), sub: `${t("stats.bestAverage")} ${num(s.bestMatchAverage, 2)}` },
    { label: t("stats.first9"), value: num(s.first9Average, 2) },
    { label: t("stats.checkoutPct"), value: pct(s.checkoutPercentage), sub: t("stats.checkoutDetail", { hits: s.checkoutsHit, attempts: s.checkoutAttempts }) },
    { label: t("stats.highestCheckout"), value: s.highestCheckout ? num(s.highestCheckout) : "–", accent: s.highestCheckout >= 100 },
    { label: t("stats.s180"), value: num(s.count180), accent: s.count180 > 0 },
    { label: t("stats.s140"), value: num(s.count140) },
    { label: t("stats.s100"), value: num(s.count100) },
    { label: t("stats.highestTurn"), value: num(s.highestTurn) },
  ];
  return (
    <section className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {tiles.map((tile) => (
        <div key={tile.label} className={`panel flex flex-col gap-1 p-4 ${tile.accent ? "border-brass/70" : ""}`}>
          <span className="text-xs font-semibold text-trim-faint">{tile.label}</span>
          <span className={`numerals text-4xl font-extrabold leading-none ${tile.accent ? "text-brass" : "text-trim"}`}>{tile.value}</span>
          {tile.sub && <span className="text-xs text-trim-dim">{tile.sub}</span>}
        </div>
      ))}
    </section>
  );
}

/** Every monthly award the player has won, grouped by kind, champion first. */
function TrophyCabinet({ badges }: { badges: AwardView[] }) {
  const { t } = useTranslation();
  const value = useAwardValue();
  const order: AwardType[] = ["PLAYER_OF_YEAR", ...AWARD_ORDER];
  const groups = order.map((award) => ({
    award,
    wins: badges.filter((b) => b.award === award).sort((a, b) => b.period.localeCompare(a.period)),
  })).filter((g) => g.wins.length > 0);
  return (
    <section>
      <SectionTitle>{t("awards.cabinet")}</SectionTitle>
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {groups.map((g) => (
          <li key={g.award} className={`panel flex gap-4 p-4 ${g.award === "PLAYER_OF_MONTH" || g.award === "PLAYER_OF_YEAR" ? "border-brass/60" : ""}`}>
            <Medal award={g.award} size="lg" />
            <div className="min-w-0">
              <p className="font-display text-xl leading-tight">
                {t(`awards.types.${g.award}`)}
                {g.wins.length > 1 && <span className="numerals ml-2 text-brass">×{g.wins.length}</span>}
              </p>
              <ul className="mt-2 flex flex-col gap-0.5 text-sm">
                {g.wins.map((w) => (
                  <li key={w.period} className="flex flex-wrap gap-x-2">
                    <span className="capitalize text-trim">{monthName(w.period)}</span>
                    <span className="tnum text-trim-faint">{value(w.award, w.value)}</span>
                  </li>
                ))}
              </ul>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Party games (never rated): played and won per type, and the latest few. */
function PartyGames({ username, playerId }: { username: string; playerId: number }) {
  const { t } = useTranslation();
  const stats = useGameStats(username);
  const recent = useRecentGames(username, 6);
  const types = (stats.data?.types ?? []).filter((x) => x.played > 0);
  if (types.length === 0) return null;
  return (
    <section>
      <SectionTitle>{t("games.profileTitle")}</SectionTitle>
      <div className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
        <ul className="grid grid-cols-2 gap-3 [&>*]:min-w-0">
          {types.map((x) => (
            <li key={x.type} className="panel flex flex-col gap-1 p-4">
              <span className="flex items-center gap-2 text-xs font-semibold text-trim-faint">
                <BarChart3 className="size-3.5" aria-hidden />
                {t(`games.types.${x.type}`)}
              </span>
              <span className="numerals text-4xl font-extrabold leading-none">{t("stats.record", { won: x.won, lost: x.played - x.won })}</span>
              {x.best !== null && <span className="text-xs text-trim-dim">{t(`games.best.${x.type}`, { value: num(x.best, x.type === "X01" || x.type === "CRICKET" ? 1 : 0) })}</span>}
            </li>
          ))}
        </ul>
        <ul className="panel divide-y divide-mortar/60 p-1">
          {(recent.data ?? []).map((g) => {
            const mine = g.players.find((p) => p.player.id === playerId);
            const won = g.status === "COMPLETED" && mine && g.winnerSide === mine.side;
            return (
              <li key={g.id}>
                <Link to={`/games/${g.id}`} className="flex items-center gap-3 rounded px-3 py-3 hover:bg-masonry-2">
                  <span className={`w-6 text-center text-xs font-bold ${won ? "text-board-bright" : "text-trim-faint"}`}>{g.status === "COMPLETED" ? (won ? t("stats.win") : t("stats.loss")) : "·"}</span>
                  <span className="flex-1 truncate text-sm">
                    <span className="font-semibold">{t(`games.types.${g.type}`)}</span>{" "}
                    <span className="text-trim-dim">{[...new Set(g.players.map((p) => p.side))].map((side) => sideLabel(g, side)).join(t("games.versus"))}</span>
                  </span>
                  <span className="text-xs text-trim-faint">{relative(g.completedAt ?? g.createdAt)}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
