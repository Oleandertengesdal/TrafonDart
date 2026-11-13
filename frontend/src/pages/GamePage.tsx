import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Eye, Radio, Target, Trophy } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { api, apiPath } from "../api/client";
import { keys, useGame } from "../api/queries";
import type { GameView } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { LanguageSwitch } from "../components/Layout";
import { LogoMark } from "../components/Logo";
import { ConfirmButton, ErrorNotice, LinkButton, Spinner, useErrorText } from "../components/ui";
import { GameBoard, GameTurn } from "../features/games/Boards";
import { sideLabel } from "../features/games/gameLogic";
import { Keypad } from "../features/match/Keypad";
import { pathOrEmpty } from "../features/match/useScorer";
import { OfflineNotice } from "../features/scoring/OfflineNotice";
import { useDartQueue } from "../features/scoring/useDartQueue";
import { useWakeLock } from "../lib/wakeLock";
import { useLiveStatus, useTopic } from "../realtime/useTopic";
import { NotFound } from "./NotFoundPage";
import { isNotFound } from "../lib/errors";
import { usePageTitle } from "../lib/pageTitle";

/** A party game (never rated): scoreboard, keypad for the scorer, live view for everyone else. */
export function GamePage() {
  const { id = "" } = useParams();
  const { me } = useCurrentUser();
  const qc = useQueryClient();
  const q = useGame(id);
  const connected = useLiveStatus();
  const [mode, setMode] = useState<"score" | "watch" | null>(null);
  const queryKey = useMemo(() => keys.game(id), [id]);
  const scorer = useDartQueue<GameView>({
    kind: "game",
    id,
    queryKey,
    throwPath: pathOrEmpty(() => apiPath`/api/games/${id}/throws`),
    undoPath: pathOrEmpty(() => apiPath`/api/games/${id}/undo`),
    lastLabel: (g) => g.lastDart,
  });
  useTopic<GameView>(id ? `/topic/games/${id}` : null, (state) => {
    if (!scorer.busy()) qc.setQueryData(queryKey, state);
  });
  const game = q.data;
  useWakeLock(game?.status === "IN_PROGRESS");
  const { t } = useTranslation();
  usePageTitle(game && `${t(`games.types.${game.type}`)}: ${game.players.map((p) => p.player.realName).join(", ")}`);

  if (q.isLoading) return <Spinner />;
  if (q.error || !game) {
    return (
      <div className="mx-auto flex min-h-dvh max-w-5xl items-center p-6 [&>*]:w-full">
        {isNotFound(q.error) ? <NotFound what="game" /> : <ErrorNotice error={q.error} onRetry={() => q.refetch()} resource />}
      </div>
    );
  }

  const inProgress = game.status === "IN_PROGRESS";
  const canScore = inProgress && !!me && (me.role === "ADMIN" || game.createdBy === me.id || game.players.some((p) => p.player.id === me.id));
  const view = mode ?? (canScore ? "score" : "watch");

  return (
    <div className="min-h-dvh">
      <GameTopBar game={game} connected={connected} canScore={canScore} view={view} onView={setMode} />
      {view === "score" && canScore ? <ScoreGame game={game} scorer={scorer} /> : <WatchGame game={game} />}
    </div>
  );
}

function GameTopBar({
  game,
  connected,
  canScore,
  view,
  onView,
}: {
  game: GameView;
  connected: boolean;
  canScore: boolean;
  view: "score" | "watch";
  onView: (v: "score" | "watch") => void;
}) {
  const { t } = useTranslation();
  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-mortar bg-night/95 px-3 backdrop-blur">
      <Link to="/" className="flex items-center gap-2 rounded-md p-2 text-trim-dim hover:text-trim" aria-label={t("common.back")}>
        <ArrowLeft className="size-5" />
        <LogoMark className="size-7" />
      </Link>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">
          {t(`games.types.${game.type}`)}
          {game.teams ? `, ${t("games.doubles")}` : ""}
        </p>
        <p className="truncate text-xs text-trim-faint">
          {game.sides.map((s) => sideLabel(game, s.side)).join(t("games.versus"))}, {t("common.unrated").toLowerCase()}
        </p>
      </div>
      {game.status === "IN_PROGRESS" ? (
        <span className={`hidden items-center gap-2 text-xs font-bold   sm:flex ${connected ? "text-trafon-bright" : "text-trim-faint"}`}>
          <Radio className={`size-4 ${connected ? "animate-pulse" : ""}`} aria-hidden />
          {connected ? t("match.connection.live") : t("match.connection.reconnecting")}
        </span>
      ) : (
        <span className="rounded bg-masonry-2 px-2 py-0.5 text-xs font-bold text-trim-dim">{t(`games.status.${game.status}`)}</span>
      )}
      {canScore && (
        <div className="flex rounded-md border border-mortar p-0.5 text-xs font-bold">
          {(["score", "watch"] as const).map((v) => (
            <button
              key={v}
              onClick={() => onView(v)}
              aria-pressed={view === v}
              className={`flex items-center gap-1 rounded px-2 py-1 ${view === v ? "bg-trafon text-trim" : "text-trim-dim"}`}
            >
              {v === "score" ? <Target className="size-3.5" aria-hidden /> : <Eye className="size-3.5" aria-hidden />}
              {v === "score" ? t("match.scorer") : t("match.watch")}
            </button>
          ))}
        </div>
      )}
      <span className="hidden md:block">
        <LanguageSwitch />
      </span>
    </header>
  );
}

function ScoreGame({ game, scorer }: { game: GameView; scorer: ReturnType<typeof useDartQueue<GameView>> }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const errorText = useErrorText();
  const abandon = (
    <ConfirmButton
      label={t("games.abandon")}
      message={t("games.abandonConfirm")}
      onConfirm={async () => {
        await api.post(apiPath`/api/games/${game.id}/abandon`);
        navigate("/");
      }}
    />
  );
  return (
    <div className="grid gap-3 p-3 md:min-h-[calc(100dvh-3.5rem)] md:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)] md:p-4">
      <div className="flex min-w-0 flex-col gap-3">
        <GameBoard game={game} />
        <GameTurn game={game} pending={scorer.pending} />
        <OfflineNotice offline={scorer.offline} count={scorer.pending.length} />
        {scorer.error !== null && (
          <div role="alert" className="rounded-md border border-trafon-deep bg-trafon-deep/20 px-3 py-2 text-sm">
            {errorText(scorer.error)}
          </div>
        )}
        <p className="text-sm text-trim-dim">{t(`games.howTo.${game.type}`)}</p>
        <div className="mt-auto hidden justify-end md:flex">{abandon}</div>
      </div>
      <div className="min-h-[52dvh] min-w-0 md:min-h-0">
        <Keypad onDart={scorer.throwDart} onUndo={scorer.undo} disabled={game.status !== "IN_PROGRESS"} canUndo={game.dartCount > 0 || scorer.pending.length > 0} />
      </div>
      <div className="flex justify-end md:hidden">{abandon}</div>
    </div>
  );
}

function WatchGame({ game }: { game: GameView }) {
  const { t } = useTranslation();
  const finished = game.status === "COMPLETED";
  const ranked = [...game.slots].sort((a, b) => (a.placement || 99) - (b.placement || 99));
  const name = (slot: number) => game.players.find((p) => p.slot === slot)?.player.realName ?? "?";
  const scoreLabel = t(`games.scoreLabel.${game.type}`);
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6 px-4 pb-16 pt-6">
      {finished && game.winnerSide !== null && (
        <div className="flex flex-wrap items-center gap-4 rounded-xl border border-brass/60 bg-brass/10 px-5 py-4">
          <Trophy className="size-8 text-brass" aria-hidden />
          <p className="flex-1 font-display text-3xl leading-none">{t("games.won", { name: sideLabel(game, game.winnerSide) })}</p>
          <LinkButton to="/games/new" variant="secondary">
            {t("games.again")}
          </LinkButton>
        </div>
      )}
      <GameBoard game={game} size="lg" />
      <GameTurn game={game} />
      <section className="panel p-4">
        <h2 className="mb-2 font-display text-xl">{t("games.statsTitle")}</h2>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-trim-faint">
              {finished && <th className="py-1 font-semibold">#</th>}
              <th className="py-1 font-semibold">{t("games.player")}</th>
              <th className="py-1 text-right font-semibold">{t("common.dartsHeading")}</th>
              <th className="py-1 text-right font-semibold">{scoreLabel}</th>
              {game.type === "X01" && <th className="py-1 text-right font-semibold">{t("match.average")}</th>}
              {game.type === "X01" && <th className="py-1 text-right font-semibold">180</th>}
            </tr>
          </thead>
          <tbody>
            {(finished ? ranked : game.slots).map((s) => (
              <tr key={s.slot} className="border-t border-mortar/60">
                {finished && <td className="numerals py-2 text-lg font-bold text-trim-faint">{s.placement || "–"}</td>}
                <td className="py-2 font-semibold">{name(s.slot)}</td>
                <td className="tnum py-2 text-right">{s.darts}</td>
                <td className="tnum py-2 text-right">{s.score}</td>
                {game.type === "X01" && <td className="tnum py-2 text-right">{s.darts ? ((s.score * 3) / s.darts).toFixed(1) : "–"}</td>}
                {game.type === "X01" && <td className="tnum py-2 text-right">{s.count180}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
