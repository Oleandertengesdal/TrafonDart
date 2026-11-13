import { useQueryClient } from "@tanstack/react-query";
import { Maximize, Minimize, Radio, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import { keys, useAwardRace, useGame, useLeaderboard, useLiveGames, useLiveMatches, useMatch, useRecentMatches } from "../api/queries";
import type { GameView, LiveMatch } from "../api/types";
import { Logo } from "../components/Logo";
import { RatingBadge } from "../components/PlayerName";
import { GameBoard, GameTurn } from "../features/games/Boards";
import { sideLabel } from "../features/games/gameLogic";
import { ArchScore } from "../features/match/ArchScore";
import { CelebrationOverlay, useMoments } from "../features/match/Celebration";
import { CheckoutHint, TurnStrip } from "../features/match/TurnStrip";
import { monthName, num, rulesSummary } from "../lib/format";
import { useWakeLock } from "../lib/wakeLock";
import { useLiveStatus, useTopic } from "../realtime/useTopic";
import { usePageTitle } from "../lib/pageTitle";

const ROTATE_MS = 20_000;

type Live = { kind: "match"; id: string } | { kind: "game"; id: string };

/**
 * TV mode: a read-only, full-screen scoreboard for a big screen at the venue. Shows the live match or game (taking
 * turns every 20 s when there are several, or pinned with ?match=… / ?game=…); between games it shows the
 * leaderboard, the month's race and the latest results. Keeps the screen awake. Nothing here can change anything.
 */
export function TvPage() {
  const { t } = useTranslation();
  usePageTitle(t("tv.open"));
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const matches = useLiveMatches();
  const games = useLiveGames();
  const connected = useLiveStatus();
  const [index, setIndex] = useState(0);
  useWakeLock(true);

  useTopic("/topic/live", () => {
    void qc.invalidateQueries({ queryKey: keys.liveMatches });
    void qc.invalidateQueries({ queryKey: keys.liveGames });
    void qc.invalidateQueries({ queryKey: keys.leaderboard });
    void qc.invalidateQueries({ queryKey: ["matches", "recent"] });
    void qc.invalidateQueries({ queryKey: keys.awardRace });
  });

  const pinned: Live | null = params.get("match") ? { kind: "match", id: params.get("match")! } : params.get("game") ? { kind: "game", id: params.get("game")! } : null;
  const live: Live[] = [
    ...(matches.data ?? []).map((m) => ({ kind: "match" as const, id: m.id })),
    ...(games.data ?? []).map((g) => ({ kind: "game" as const, id: g.id })),
  ];
  const isPinned = pinned !== null;
  useEffect(() => {
    if (isPinned || live.length < 2) return;
    const timer = setInterval(() => setIndex((i) => i + 1), ROTATE_MS);
    return () => clearInterval(timer);
  }, [isPinned, live.length]);
  const featured = pinned ?? (live.length > 0 ? live[index % live.length] : null);

  return (
    <div className="flex min-h-dvh flex-col bg-night px-[3vw] py-[2vh]">
      <TvHeader connected={connected} count={live.length} />
      <main className="flex flex-1 flex-col justify-center py-[2vh]">
        {featured?.kind === "match" ? <TvMatch id={featured.id} /> : featured?.kind === "game" ? <TvGame id={featured.id} /> : <TvIdle />}
      </main>
      {!pinned && live.length > 1 && (
        <p className="text-center text-lg text-trim-faint">{t("tv.rotating", { n: (index % live.length) + 1, of: live.length })}</p>
      )}
    </div>
  );
}

function TvHeader({ connected, count }: { connected: boolean; count: number }) {
  const { t } = useTranslation();
  const [now, setNow] = useState(() => new Date());
  const [full, setFull] = useState(false);
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 15_000);
    const onFs = () => setFull(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", onFs);
    return () => {
      clearInterval(id);
      document.removeEventListener("fullscreenchange", onFs);
    };
  }, []);
  const toggleFull = () => {
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => undefined);
    else void document.documentElement.requestFullscreen?.().catch(() => undefined);
  };
  return (
    <header className="flex items-center gap-2 sm:gap-4">
      <Logo />
      {count > 0 && (
        <span className={`hidden items-center gap-2 text-lg font-bold   sm:flex ${connected ? "text-trafon-bright" : "text-trim-faint"}`}>
          <Radio className={`size-5 ${connected ? "animate-pulse" : ""}`} aria-hidden />
          {connected ? t("match.connection.live") : t("match.connection.reconnecting")}
        </span>
      )}
      <span className="numerals ml-auto text-2xl font-bold text-trim-dim sm:text-4xl">{now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
      <button onClick={toggleFull} className="rounded-md p-2 text-trim-faint hover:text-trim" aria-label={full ? t("tv.exitFullscreen") : t("tv.fullscreen")}>
        {full ? <Minimize className="size-6" /> : <Maximize className="size-6" />}
      </button>
      <Link to="/" className="rounded-md p-2 text-trim-faint hover:text-trim" aria-label={t("tv.leave")}>
        <X className="size-6" />
      </Link>
    </header>
  );
}

function TvMatch({ id }: { id: string }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const q = useMatch(id);
  useTopic<LiveMatch>(`/topic/matches/${id}`, (state) => qc.setQueryData(keys.match(id), state));
  const match = q.data;
  const moment = useMoments(match);
  if (!match) return null;
  const finished = match.status !== "IN_PROGRESS";
  const bust = match.turnJustEnded && match.lastCompletedTurn?.bust;
  return (
    <div className="flex flex-col gap-[3vh]">
      <p className="text-center text-2xl text-trim-dim">
        {match.tournament ? `${match.tournament.name}, ` : ""}
        {rulesSummary(match.config)}
        {match.rated ? "" : `, ${t("common.unrated").toLowerCase()}`}
      </p>
      <div className="grid grid-cols-2 gap-[4vw]">
        {match.players.map((p, i) => (
          <ArchScore
            key={p.id}
            player={p}
            remaining={finished && match.winnerIndex === i ? 0 : match.remaining[i]}
            legsWon={match.legsWon[i]}
            legsToWin={match.config.legsToWin}
            stats={match.stats[i]}
            active={!finished && match.currentPlayerIndex === i}
            winner={finished && match.winnerIndex === i}
            needsDouble={!finished && !match.opened[i]}
            bust={bust && match.lastCompletedTurn?.playerIndex === i}
            size="lg"
          />
        ))}
      </div>
      {!finished && (
        <div className="mx-auto flex w-full max-w-4xl flex-col gap-3 text-xl">
          <TurnStrip match={match} />
          <CheckoutHint match={match} />
        </div>
      )}
      {match.status === "COMPLETED" && match.winnerIndex !== null && (
        <p className="text-center font-display text-6xl text-brass">{t("match.matchWon", { name: match.players[match.winnerIndex].realName })}</p>
      )}
      <CelebrationOverlay moment={moment} />
    </div>
  );
}

function TvGame({ id }: { id: string }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const q = useGame(id);
  useTopic<GameView>(`/topic/games/${id}`, (state) => qc.setQueryData(keys.game(id), state));
  const game = q.data;
  if (!game) return null;
  return (
    <div className="flex flex-col gap-[3vh]">
      <p className="text-center text-2xl text-trim-dim">
        {t(`games.types.${game.type}`)}
        {game.teams ? `, ${t("games.doubles")}` : ""}
      </p>
      <GameBoard game={game} size="lg" />
      <div className="mx-auto w-full max-w-4xl text-xl">
        <GameTurn game={game} />
      </div>
      {game.status === "COMPLETED" && game.winnerSide !== null && (
        <p className="text-center font-display text-6xl text-brass">{t("games.won", { name: sideLabel(game, game.winnerSide) })}</p>
      )}
    </div>
  );
}

/** Between games: who's on top, who leads the month, what just happened. */
function TvIdle() {
  const { t } = useTranslation();
  const board = useLeaderboard();
  const race = useAwardRace();
  const recent = useRecentMatches(undefined, 6);
  const top = (race.data?.standings ?? []).filter((s) => s.eligible).slice(0, 3);
  return (
    <div className="grid gap-[3vw] lg:grid-cols-[1.2fr_1fr]">
      <section>
        <h2 className="mb-4 font-display text-5xl">{t("home.leaderboard")}</h2>
        <ol className="flex flex-col gap-2">
          {(board.data ?? []).slice(0, 8).map((row) => (
            <li key={row.player.id} className="panel flex items-center gap-4 px-5 py-3 text-2xl">
              <span className={`numerals w-10 text-4xl font-bold ${row.rank === 1 ? "text-brass" : "text-trim-faint"}`}>{row.rank}</span>
              <span className="min-w-0 flex-1 truncate font-semibold">{row.player.realName}</span>
              <span className="tnum text-xl text-trim-faint">{num(row.average, 1)}</span>
              <span className="text-3xl">
                <RatingBadge player={row.player} />
              </span>
            </li>
          ))}
        </ol>
      </section>
      <div className="flex flex-col gap-[3vh]">
        {race.data && (
          <section className="rounded-xl border border-brass/50 bg-masonry p-5">
            <h2 className="font-display text-3xl text-brass">{t("awards.raceTitle", { month: monthName(race.data.period) })}</h2>
            {top.length === 0 ? (
              <p className="mt-2 text-xl text-trim-faint">{t("awards.noRace")}</p>
            ) : (
              <ol className="mt-3 flex flex-col gap-2 text-2xl">
                {top.map((s) => (
                  <li key={s.player.id} className="flex items-center gap-3">
                    <span className={`numerals w-8 text-3xl font-bold ${s.rank === 1 ? "text-brass" : "text-trim-faint"}`}>{s.rank}</span>
                    <span className="flex-1 truncate font-semibold">{s.player.realName}</span>
                    <span className="tnum text-xl text-trim-dim">{num(Math.round(s.points.total))}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        )}
        <section>
          <h2 className="mb-3 font-display text-3xl">{t("home.recent")}</h2>
          <ul className="flex flex-col gap-2 text-xl">
            {(recent.data ?? []).map((m) => (
              <li key={m.id} className="panel grid grid-cols-[1fr_auto_1fr] items-center gap-3 px-4 py-2">
                <span className={`truncate ${m.winnerIndex === 0 ? "font-bold text-trim" : "text-trim-dim"}`}>{m.players[0].realName}</span>
                <span className="numerals text-3xl font-bold">
                  {m.legsWon[0]}–{m.legsWon[1]}
                </span>
                <span className={`truncate text-right ${m.winnerIndex === 1 ? "font-bold text-trim" : "text-trim-dim"}`}>{m.players[1].realName}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
