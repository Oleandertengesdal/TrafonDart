import { useQueryClient } from "@tanstack/react-query";
import { Radio, Trophy } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { keys, useLeaderboard, useLiveMatches, useRecentMatches, useTournaments } from "../api/queries";
import type { MatchSummary } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { Facade } from "../components/Facade";
import { MatchRow } from "../components/MatchRow";
import { RatingBadge } from "../components/PlayerName";
import { ErrorNotice, LinkButton, SectionTitle, Spinner } from "../components/ui";
import { num, rulesSummary } from "../lib/format";
import { useTopic } from "../realtime/useTopic";

export function HomePage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const live = useLiveMatches();
  const recent = useRecentMatches(undefined, 8);
  const board = useLeaderboard();
  const tournaments = useTournaments();

  useTopic("/topic/live", () => {
    void qc.invalidateQueries({ queryKey: keys.liveMatches });
    void qc.invalidateQueries({ queryKey: ["matches", "recent"] });
    void qc.invalidateQueries({ queryKey: keys.leaderboard });
  });

  const liveList = live.data ?? [];
  const running = (tournaments.data ?? []).filter((x) => x.status === "IN_PROGRESS");

  return (
    <div className="flex flex-col gap-12">
      {live.isLoading ? <Spinner /> : liveList.length > 0 ? <LiveNow matches={liveList} /> : <QuietHero />}

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
                      <span className="min-w-0 flex-1 truncate font-semibold">{row.player.realName}</span>
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
        <LinkButton to="/play" size="lg" className="mt-8">
          {t("home.startMatch")}
        </LinkButton>
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
