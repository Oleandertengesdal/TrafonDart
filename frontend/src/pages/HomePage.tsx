import { useQueryClient } from "@tanstack/react-query";
import { Crown, Radio, Sparkles, Trophy, Tv, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { keys, useAwardRace, useLeaderboard, useLiveGames, useLiveMatches, useRecentMatches, useTournaments } from "../api/queries";
import type { GameSummary, MatchSummary } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { Facade } from "../components/Facade";
import { MatchRow } from "../components/MatchRow";
import { RatingBadge } from "../components/PlayerName";
import { ErrorNotice, LinkButton, SectionTitle, Spinner } from "../components/ui";
import { CrownMark } from "../features/awards/Medal";
import { monthName, num, rulesSummary } from "../lib/format";
import { useTopic } from "../realtime/useTopic";
import { ChallengesCard } from "../features/challenges/Challenges";
import { sideLabel } from "../features/games/gameLogic";
import { recapPromo } from "../features/recap/recapMath";

export function HomePage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const live = useLiveMatches();
  const liveGames = useLiveGames();
  const { me } = useCurrentUser();
  const promo = recapPromo();
  const recent = useRecentMatches(undefined, 8);
  const board = useLeaderboard();
  const tournaments = useTournaments();
  const race = useAwardRace();
  const reigning = race.data?.reigning?.player ? race.data.reigning : null;

  useTopic("/topic/live", () => {
    void qc.invalidateQueries({ queryKey: keys.liveMatches });
    void qc.invalidateQueries({ queryKey: keys.liveGames });
    void qc.invalidateQueries({ queryKey: ["matches", "recent"] });
    void qc.invalidateQueries({ queryKey: keys.leaderboard });
    void qc.invalidateQueries({ queryKey: keys.awardRace });
  });

  const liveList = live.data ?? [];
  const running = (tournaments.data ?? []).filter((x) => x.status === "IN_PROGRESS");

  const gameList = liveGames.data ?? [];
  return (
    <div className="flex flex-col gap-12">
      <ChallengesCard />
      {live.isLoading ? <Spinner /> : liveList.length > 0 ? <LiveNow matches={liveList} /> : gameList.length === 0 && <QuietHero />}
      {gameList.length > 0 && <LiveGames games={gameList} />}
      {promo && me && (
        <Link
          to={`/players/${me.username}/recap?year=${promo.year}`}
          className="flex flex-wrap items-center gap-4 rounded-2xl border border-brass/60 bg-gradient-to-r from-brass/25 via-trafon/15 to-masonry px-6 py-5 hover:border-brass"
        >
          <Sparkles className="size-8 text-brass" aria-hidden />
          <span className="flex-1">
            <span className="block font-display text-3xl font-extrabold uppercase leading-none">{t("recap.promoTitle", { year: promo.year })}</span>
            <span className="text-trim-dim">{t("recap.promoLead")}</span>
          </span>
        </Link>
      )}

      <div className="grid gap-10 lg:grid-cols-[1.3fr_1fr]">
        <section>
          <SectionTitle action={<Link to="/matches" className="text-sm font-semibold text-trim-dim hover:text-trim">{t("common.seeAll")}</Link>}>
            {t("home.recent")}
          </SectionTitle>
          {recent.error ? (
            <ErrorNotice error={recent.error} onRetry={() => recent.refetch()} />
          ) : recent.isLoading ? (
            <Spinner />
          ) : (recent.data ?? []).length === 0 ? (
            <p className="text-trim-dim">{t("home.noRecent")}</p>
          ) : (
            <ul className="panel divide-y divide-mortar/60 p-1">{recent.data!.map((m) => <MatchRow key={m.id} match={m} />)}</ul>
          )}
        </section>

        <div className="flex flex-col gap-10">
          {race.data && <MonthCard />}

          <section>
            <SectionTitle action={<Link to="/players" className="text-sm font-semibold text-trim-dim hover:text-trim">{t("common.seeAll")}</Link>}>
              {t("home.leaderboard")}
            </SectionTitle>
            {board.isLoading ? (
              <Spinner />
            ) : (
              <ol className="panel divide-y divide-mortar/60">
                {(board.data ?? []).slice(0, 8).map((row) => (
                  <li key={row.player.id}>
                    <Link to={`/players/${row.player.username}`} className="flex items-center gap-3 px-4 py-2.5 hover:bg-masonry-2">
                      <span className={`numerals w-6 text-xl font-bold ${row.rank === 1 ? "text-brass" : "text-trim-faint"}`}>{row.rank}</span>
                      <span className="flex min-w-0 flex-1 items-center gap-1.5 font-semibold">
                        <span className="truncate">{row.player.realName}</span>
                        {reigning?.player?.id === row.player.id && <CrownMark period={reigning.period} />}
                      </span>
                      <span className="tnum text-sm text-trim-faint">{num(row.average, 1)}</span>
                      <RatingBadge player={row.player} />
                    </Link>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <section>
            <SectionTitle>{t("home.tournaments")}</SectionTitle>
            {running.length === 0 ? (
              <p className="text-trim-dim">{t("home.noTournaments")}</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {running.map((x) => (
                  <li key={x.id}>
                    <Link to={`/tournaments/${x.id}`} className="panel flex items-center gap-3 px-4 py-3 hover:border-trim-faint">
                      <Trophy className="size-5 text-brass" aria-hidden />
                      <span className="flex-1 font-semibold">{x.name}</span>
                      <span className="text-sm text-trim-faint">{t(`tournament.formats.${x.format}`)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

function QuietHero() {
  const { t } = useTranslation();
  return (
    <section className="grid items-center gap-8 overflow-hidden rounded-2xl border border-mortar bg-masonry/80 p-6 sm:p-10 md:grid-cols-[1.2fr_1fr]">
      <div>
        <h1 className="font-display text-[clamp(3rem,8vw,6rem)] font-extrabold uppercase leading-[0.9] tracking-tight">{t("home.noLiveTitle")}</h1>
        <p className="mt-4 max-w-md text-lg text-trim-dim">{t("home.noLiveLead")}</p>
        <div className="mt-8 flex flex-wrap gap-3">
          <LinkButton to="/play" size="lg">
            {t("home.startMatch")}
          </LinkButton>
          <LinkButton to="/games/new" size="lg" variant="secondary">
            <Users className="size-5" aria-hidden />
            {t("games.partyGame")}
          </LinkButton>
          <LinkButton to="/tv" size="lg" variant="ghost">
            <Tv className="size-5" aria-hidden />
            {t("tv.open")}
          </LinkButton>
        </div>
      </div>
      <Facade className="mx-auto hidden w-full max-w-xs md:block" />
    </section>
  );
}

function LiveNow({ matches }: { matches: MatchSummary[] }) {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  return (
    <section>
      <div className="mb-4 flex items-center gap-3">
        <Radio className="size-6 animate-pulse text-trafon-bright" aria-hidden />
        <h1 className="font-display text-5xl font-extrabold uppercase leading-none">{t("home.liveNow")}</h1>
      </div>
      <ul className="grid gap-4 md:grid-cols-2">
        {matches.map((m) => {
          const mine = me && m.players.some((p) => p.id === me.id);
          return (
            <li key={m.id}>
              <Link
                to={`/matches/${m.id}`}
                className="arch group block border-2 border-trafon bg-gradient-to-b from-[#3a1612] to-masonry px-6 pb-5 pt-12 shadow-[0_0_40px_-12px_rgba(224,57,44,0.6)]"
              >
                <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 text-center">
                  <span className="truncate font-display text-2xl font-bold uppercase">{m.players[0].realName}</span>
                  <span className="numerals text-6xl font-extrabold leading-none">
                    {m.legsWon[0]}
                    <span className="mx-1 text-trim-faint">–</span>
                    {m.legsWon[1]}
                  </span>
                  <span className="truncate font-display text-2xl font-bold uppercase">{m.players[1].realName}</span>
                </div>
                <p className="mt-3 text-center text-sm text-trim-faint">
                  {m.tournament ? `${m.tournament.name}, ` : ""}
                  {rulesSummary(m.config)}
                </p>
                <span className="mt-4 block text-center text-sm font-bold uppercase tracking-wider text-trafon-bright group-hover:underline">
                  {mine ? t("home.score") : t("home.watch")}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function LiveGames({ games }: { games: GameSummary[] }) {
  const { t } = useTranslation();
  return (
    <section>
      <SectionTitle
        action={
          <Link to="/tv" className="flex items-center gap-1.5 text-sm font-semibold text-trim-dim hover:text-trim">
            <Tv className="size-4" aria-hidden />
            {t("tv.open")}
          </Link>
        }
      >
        {t("games.liveTitle")}
      </SectionTitle>
      <ul className="grid gap-3 md:grid-cols-2">
        {games.map((g) => (
          <li key={g.id} className="min-w-0">
            <Link to={`/games/${g.id}`} className="panel flex items-center gap-3 border-trafon/70 px-4 py-3 hover:border-trafon-bright">
              <Radio className="size-5 animate-pulse text-trafon-bright" aria-hidden />
              <span className="min-w-0 flex-1">
                <span className="block font-semibold">
                  {t(`games.types.${g.type}`)}
                  {g.teams ? `, ${t("games.doubles")}` : ""}
                </span>
                <span className="block truncate text-sm text-trim-dim">
                  {[...new Set(g.players.map((p) => p.side))].map((side) => sideLabel(g, side)).join(t("games.versus"))}
                </span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Last month's winner, and who is ahead in this month's race. */
function MonthCard() {
  const { t } = useTranslation();
  const race = useAwardRace().data!;
  const reigning = race.reigning?.player ? race.reigning : null;
  const top = race.standings.filter((s) => s.eligible).slice(0, 3);
  return (
    <section className="overflow-hidden rounded-xl border border-brass/50 bg-masonry">
      {reigning?.player && (
        <Link to={`/players/${reigning.player.username}`} className="flex items-center gap-4 border-b border-brass/30 bg-brass/10 px-4 py-4 hover:bg-brass/15">
          <span className="flex h-14 w-12 shrink-0 justify-center rounded-t-full border-2 border-brass bg-night pt-3">
            <Crown className="size-6 text-brass" aria-hidden />
          </span>
          <span className="min-w-0">
            <span className="block text-sm font-semibold text-brass">{t("awards.reigning", { month: monthName(reigning.period) })}</span>
            <span className="block truncate font-display text-3xl font-extrabold uppercase leading-none">{reigning.player.realName}</span>
          </span>
        </Link>
      )}
      <div className="px-4 py-3">
        <p className="text-sm font-semibold text-trim-dim">{t("awards.raceTitle", { month: monthName(race.period) })}</p>
        {top.length === 0 ? (
          <p className="mt-1 text-sm text-trim-faint">{t("awards.noRace")}</p>
        ) : (
          <ol className="mt-2 flex flex-col gap-1.5">
            {top.map((s) => (
              <li key={s.player.id} className="flex items-center gap-3">
                <span className={`numerals w-5 text-lg font-bold ${s.rank === 1 ? "text-brass" : "text-trim-faint"}`}>{s.rank}</span>
                <span className="min-w-0 flex-1 truncate font-semibold">{s.player.realName}</span>
                <span className="tnum text-sm text-trim-dim">{t("awards.values.PLAYER_OF_MONTH", { value: num(Math.round(s.points.total)) })}</span>
              </li>
            ))}
          </ol>
        )}
        <Link to="/awards" className="mt-3 inline-block text-sm font-semibold text-brass hover:underline">
          {t("awards.seeRace")}
        </Link>
      </div>
    </section>
  );
}
