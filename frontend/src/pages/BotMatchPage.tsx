import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Bot, Flag, RotateCcw, Trophy } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { api, apiPath } from "../api/client";
import { keys, useBotMatch } from "../api/queries";
import type { BotMatch, TurnView } from "../api/types";
import { LogoMark } from "../components/Logo";
import { Button, ConfirmButton, ErrorNotice, LinkButton, Spinner, useErrorText } from "../components/ui";
import { asLiveMatch, botDartsToReveal, nextLevel } from "../features/bot/botLogic";
import { ArchScore } from "../features/match/ArchScore";
import { CelebrationOverlay, celebrate, useMomentState } from "../features/match/Celebration";
import { Keypad } from "../features/match/Keypad";
import { MatchStatsTable } from "../features/match/MatchStatsTable";
import { CheckoutHint, TurnStrip } from "../features/match/TurnStrip";
import { pathOrEmpty } from "../features/match/useScorer";
import { OfflineNotice } from "../features/scoring/OfflineNotice";
import { useDartQueue } from "../features/scoring/useDartQueue";
import { isNotFound } from "../lib/errors";
import { dartLabel, num, rulesSummary } from "../lib/format";
import { matchTitle, usePageTitle } from "../lib/pageTitle";
import { useWakeLock } from "../lib/wakeLock";
import { NotFound } from "./NotFoundPage";

const DART_MS = 650;

/** Your last dart on the board, for recognising a dart whose answer got lost (see useDartQueue). */
function lastPlayerDart(m: BotMatch): string | null {
  const turn = m.playerTurn;
  return turn && turn.darts.length > 0 ? turn.darts[turn.darts.length - 1].label : null;
}

/** Trafo's darts being shown one by one; Trafo's score and legs stay as they were until they are all shown. */
interface Reveal {
  turns: TurnView[];
  shown: number;
  /** Pause before the first dart (longer after a celebration of your own turn). */
  delay: number;
  heldRemaining: number;
  heldLegs: number;
}

/** A practice match against Trafo: you score your darts, Trafo answers with its own. */
export function BotMatchPage() {
  const { id = "" } = useParams();
  const { t } = useTranslation();
  const q = useBotMatch(id);
  const queryKey = useMemo(() => keys.botMatch(id), [id]);
  const scorer = useDartQueue<BotMatch>({
    kind: "bot",
    id,
    queryKey,
    throwPath: pathOrEmpty(() => apiPath`/api/bot-matches/${id}/throws`),
    undoPath: pathOrEmpty(() => apiPath`/api/bot-matches/${id}/undo`),
    lastLabel: lastPlayerDart,
  });
  const match = q.data;
  const [moment, setMoment] = useMomentState();
  const [reveal, setReveal] = useState<Reveal | null>(null);
  const previous = useRef<BotMatch | null>(null);

  // React to each new state: celebrate your finished turn, then show Trafo's answer dart by dart.
  useEffect(() => {
    if (!match) return;
    const before = previous.current;
    previous.current = match;
    const turns = botDartsToReveal(before ? before.dartCount : null, match);
    const grew = !!before && match.dartCount > before.dartCount;
    let wait = 300;
    if (grew && turns && match.playerTurn) {
      const m = celebrate(match.playerTurn, match.players[0].realName, false, t, setMoment);
      if (m) {
        setMoment(m);
        wait = 1700;
      }
    } else if (grew && !turns && match.turnJustEnded && match.lastCompletedTurn?.playerIndex === 0) {
      const m = celebrate(match.lastCompletedTurn, match.players[0].realName, match.lastEvent === "MATCH_WON", t, setMoment);
      if (m) setMoment(m);
    }
    if (!turns) return;
    // Set at once (not on a timer): a refetch with the same state must not cancel it.
    const sameLeg = !!before && before.currentLeg === match.currentLeg;
    setReveal({
      turns,
      shown: 0,
      delay: wait,
      heldRemaining: sameLeg ? before!.remaining[1] : match.config.startScore,
      heldLegs: before ? before.legsWon[1] : 0,
    });
  }, [match, t, setMoment]);

  const total = reveal ? reveal.turns.reduce((n, turn) => n + turn.darts.length, 0) : 0;
  useEffect(() => {
    if (!reveal || !match) return;
    if (reveal.shown < total) {
      const h = setTimeout(() => setReveal((r) => (r ? { ...r, shown: r.shown + 1 } : r)), reveal.shown === 0 ? reveal.delay : DART_MS);
      return () => clearTimeout(h);
    }
    const h = setTimeout(() => {
      const last = reveal.turns[reveal.turns.length - 1];
      const m = celebrate(last, "Trafo", match.lastEvent === "MATCH_WON" && match.winnerIndex === 1, t, setMoment);
      if (m) setMoment(m);
      setReveal(null);
    }, 500);
    return () => clearTimeout(h);
  }, [reveal, total, match, t, setMoment]);

  usePageTitle(match && matchTitle([match.players[0].realName, "Trafo"], match.remaining, match.status === "IN_PROGRESS", t("games.versus").trim()));
  useWakeLock(match?.status === "IN_PROGRESS");

  if (q.isLoading) return <Spinner />;
  if (q.error || !match) {
    return (
      <div className="mx-auto flex min-h-dvh max-w-5xl items-center p-6 [&>*]:w-full">
        {isNotFound(q.error) ? <NotFound what="match" /> : <ErrorNotice error={q.error} onRetry={() => q.refetch()} resource />}
      </div>
    );
  }

  const finished = match.status !== "IN_PROGRESS" && !reveal;
  return (
    <div className="min-h-dvh">
      <BotTopBar match={match} />
      {finished ? <ResultView match={match} /> : <ScorerView match={match} scorer={scorer} reveal={reveal} onSkip={() => setReveal((r) => (r ? { ...r, shown: total } : r))} />}
      <CelebrationOverlay moment={moment} />
    </div>
  );
}

function BotTopBar({ match }: { match: BotMatch }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-mortar bg-night/95 px-3 backdrop-blur">
      <Link to="/bot" className="flex items-center gap-2 rounded-md p-2 text-trim-dim hover:text-trim" aria-label={t("common.back")}>
        <ArrowLeft className="size-5" />
        <LogoMark className="size-7" />
      </Link>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">
          {match.players[0].realName} {t("common.vs")} Trafo, {t(`bot.levels.${match.level}.name`).toLowerCase()}
        </p>
        <p className="truncate text-xs text-trim-faint">
          {rulesSummary(match.config)}, {t("bot.practice").toLowerCase()}
        </p>
      </div>
      {match.status === "IN_PROGRESS" && (
        <ConfirmButton
          label={t("bot.forfeit")}
          message={t("bot.forfeitConfirm")}
          onConfirm={async () => {
            await api.post(apiPath`/api/bot-matches/${match.id}/forfeit`);
            navigate("/bot");
          }}
        />
      )}
    </header>
  );
}

function ScorerView({
  match,
  scorer,
  reveal,
  onSkip,
}: {
  match: BotMatch;
  scorer: ReturnType<typeof useDartQueue<BotMatch>>;
  reveal: Reveal | null;
  onSkip: () => void;
}) {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const live = asLiveMatch(match);
  const bust = !reveal && match.turnJustEnded && match.lastCompletedTurn?.bust;
  const trafosTurn = !!reveal;
  // While Trafo's first turn is shown, its score counts down dart by dart.
  const first = reveal?.turns[0];
  const counted = first ? first.darts.slice(0, Math.min(reveal!.shown, first.darts.length)).reduce((n, d) => n + d.points, 0) : 0;

  return (
    <div className="grid gap-3 p-3 md:min-h-[calc(100dvh-3.5rem)] md:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)] md:p-4">
      <div className="flex flex-col gap-3">
        <div className="grid grid-cols-2 gap-3 pt-3">
          <ArchScore
            player={match.players[0]}
            remaining={match.remaining[0]}
            legsWon={match.legsWon[0]}
            legsToWin={match.config.legsToWin}
            stats={match.stats[0]}
            active={!trafosTurn && match.currentPlayerIndex === 0}
            needsDouble={!match.opened[0]}
            bust={bust && match.lastCompletedTurn?.playerIndex === 0}
            size="sm"
          />
          <ArchScore
            player={match.players[1]}
            handle={t(`bot.levels.${match.level}.name`)}
            remaining={reveal ? Math.max(0, reveal.heldRemaining - counted) : match.remaining[1]}
            legsWon={reveal ? reveal.heldLegs : match.legsWon[1]}
            legsToWin={match.config.legsToWin}
            stats={match.stats[1]}
            active={trafosTurn}
            needsDouble={!match.opened[1]}
            bust={bust && match.lastCompletedTurn?.playerIndex === 1}
            size="sm"
          />
        </div>
        {reveal ? <TrafoThrows reveal={reveal} onSkip={onSkip} /> : <TurnStrip match={live} pending={scorer.pending} />}
        <OfflineNotice offline={scorer.offline} count={scorer.pending.length} />
        {!reveal && <CheckoutHint match={live} />}
        {scorer.error !== null && (
          <div role="alert" className="rounded-md border border-trafon-deep bg-trafon-deep/20 px-3 py-2 text-sm">
            {errorText(scorer.error)}
          </div>
        )}
        <p className="mt-auto hidden text-xs text-trim-faint md:block">{t("match.legN", { n: match.currentLeg })}</p>
      </div>
      <div className="min-h-[52dvh] md:min-h-0">
        <Keypad
          onDart={scorer.throwDart}
          onUndo={scorer.undo}
          disabled={match.status !== "IN_PROGRESS" || trafosTurn}
          canUndo={!!match.playerTurn || scorer.pending.length > 0}
        />
      </div>
    </div>
  );
}

/** Trafo's darts appearing one by one. Tap to skip ahead. */
function TrafoThrows({ reveal, onSkip }: { reveal: Reveal; onSkip: () => void }) {
  const { t } = useTranslation();
  // Show the turn the next dart belongs to (more than one only when Trafo wins a leg and starts the next).
  let left = reveal.shown;
  let index = 0;
  while (index < reveal.turns.length - 1 && left > reveal.turns[index].darts.length) {
    left -= reveal.turns[index].darts.length;
    index++;
  }
  const turn = reveal.turns[index];
  const shown = turn.darts.slice(0, Math.min(left, turn.darts.length));
  const done = shown.length === turn.darts.length;
  const running = shown.reduce((n, d) => n + d.points, 0);
  return (
    <button type="button" onClick={onSkip} className="panel flex w-full flex-wrap items-center gap-3 px-3 py-3 text-left" aria-live="polite">
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-2 text-xs font-semibold text-trim-faint">
          <Bot className="size-4" aria-hidden />
          {t("bot.throwing")}
          {done && turn.bust && <span className="text-salmon">{t("match.bust")}</span>}
          <span className="ml-auto text-trim-faint underline underline-offset-2">{t("bot.skip")}</span>
        </p>
        <div className="mt-1 flex gap-2">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className={`numerals flex h-11 min-w-16 items-center justify-center rounded-md border px-2 text-2xl font-bold ${
                shown[i] ? "animate-pop border-trafon bg-trafon/15 text-trim" : "border-mortar/60 text-trim-faint"
              }`}
            >
              {shown[i] ? dartLabel(shown[i].label) : ""}
            </span>
          ))}
        </div>
      </div>
      <div className="text-right">
        <p className="text-xs font-semibold text-trim-faint">{t("match.thisTurn")}</p>
        <p className={`numerals text-4xl font-extrabold leading-none ${done && turn.bust ? "text-salmon line-through" : "text-trim"}`}>
          {done ? turn.total : running}
        </p>
      </div>
    </button>
  );
}

function ResultView({ match }: { match: BotMatch }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const won = match.status === "COMPLETED" && match.winnerIndex === 0;
  const up = won ? nextLevel(match.level) : null;
  const c = match.config;
  const title = match.status === "FORFEITED" ? t("bot.forfeited") : won ? t("bot.won") : t("bot.lost");
  const finishedLegs = match.legs.filter((l) => l.winnerIndex !== null);

  const again = async (level = match.level) => {
    setBusy(true);
    setError(null);
    try {
      const next = await api.post<BotMatch>("/api/bot-matches", {
        level,
        startScore: c.startScore,
        inRule: c.inRule,
        outRule: c.outRule,
        legsToWin: c.legsToWin,
        startingPlayer: null,
      });
      qc.setQueryData(keys.botMatch(next.id), next);
      void qc.invalidateQueries({ queryKey: ["bot-record"] });
      navigate(`/bot/${next.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 pb-16 pt-6">
      <div className={`mb-6 flex flex-wrap items-center gap-4 rounded-xl border px-5 py-4 ${won ? "border-brass/60 bg-brass/10" : "border-mortar bg-masonry"}`}>
        {won ? <Trophy className="size-8 text-brass" aria-hidden /> : match.status === "FORFEITED" ? <Flag className="size-8 text-trim-dim" aria-hidden /> : <Bot className="size-8 text-trim-dim" aria-hidden />}
        <div className="flex-1">
          <p className="font-display text-3xl leading-none">{title}</p>
          <p className="mt-2 text-trim-dim">
            {t("bot.resultLine", {
              legs: `${match.legsWon[0]}–${match.legsWon[1]}`,
              mine: num(match.stats[0].average, 1),
              trafo: num(match.stats[1].average, 1),
            })}
          </p>
        </div>
      </div>

      <div className="mb-8 flex flex-wrap gap-3">
        {up && (
          <Button size="lg" busy={busy} onClick={() => void again(up)}>
            {t("bot.tryLevel", { level: t(`bot.levels.${up}.name`).toLowerCase() })}
          </Button>
        )}
        <Button size="lg" variant={up ? "secondary" : "primary"} busy={busy && !up} onClick={() => void again()}>
          <RotateCcw className="size-4" aria-hidden />
          {t("bot.playAgain")}
        </Button>
        <LinkButton size="lg" variant="ghost" to={`/bot?level=${match.level}&score=${c.startScore}&in=${c.inRule}&out=${c.outRule}&legs=${c.legsToWin}`}>
          {t("bot.changeSettings")}
        </LinkButton>
        <LinkButton size="lg" variant="ghost" to={`/players/${match.players[0].username}#trafo`}>
          {t("bot.record.mine")}
        </LinkButton>
      </div>
      {error !== null && (
        <div className="mb-6">
          <ErrorNotice error={error} />
        </div>
      )}

      <div className={`grid items-start gap-6 ${finishedLegs.length > 0 ? "lg:grid-cols-[1.4fr_1fr]" : ""}`}>
        <section className="panel p-4">
          <h2 className="mb-2 font-display text-xl">{t("match.statsTitle")}</h2>
          <MatchStatsTable match={asLiveMatch(match)} />
        </section>
        {finishedLegs.length > 0 && (
          <section className="panel p-4">
            <h2 className="mb-2 font-display text-xl">{t("match.legHistory")}</h2>
            <ol className="flex flex-col gap-2">
              {finishedLegs.map((l) => (
                <li key={l.legNo} className="flex items-center justify-between gap-2 border-b border-mortar/60 pb-2 text-sm last:border-0">
                  <span className="text-trim-faint">{t("match.legN", { n: l.legNo })}</span>
                  <span className="font-semibold">{match.players[l.winnerIndex!].realName}</span>
                  <span className="tnum text-trim-dim">
                    {l.winnerDarts} {t("common.darts")}
                  </span>
                  <span className="numerals font-bold text-board-bright">{l.checkout}</span>
                </li>
              ))}
            </ol>
          </section>
        )}
      </div>
    </div>
  );
}
