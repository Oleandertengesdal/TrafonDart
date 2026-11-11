import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Eye, Flag, Link2, Radio, Target, Trophy } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { api } from "../api/client";
import { keys, useMatch } from "../api/queries";
import type { LiveMatch } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { LanguageSwitch } from "../components/Layout";
import { LogoMark } from "../components/Logo";
import { Button, ConfirmButton, ErrorNotice, Modal, Spinner, useErrorText } from "../components/ui";
import { ArchScore } from "../features/match/ArchScore";
import { CelebrationOverlay, useMoments } from "../features/match/Celebration";
import { Keypad } from "../features/match/Keypad";
import { MatchStatsTable } from "../features/match/MatchStatsTable";
import { AnalysisTabs, MatchAnalysisPanel, MatchHeadToHead, TurnByTurn, type AnalysisTab } from "../features/match/MatchAnalysis";
import { MatchSocial } from "../features/social/MatchSocial";
import { CheckoutHint, TurnStrip } from "../features/match/TurnStrip";
import { useScorer } from "../features/match/useScorer";
import { num, rulesSummary } from "../lib/format";
import { useLiveStatus, useTopic } from "../realtime/useTopic";

export function MatchPage() {
  const { id = "" } = useParams();
  const { me } = useCurrentUser();
  const qc = useQueryClient();
  const q = useMatch(id);
  const scorer = useScorer(id);
  const connected = useLiveStatus();
  const [mode, setMode] = useState<"score" | "watch" | null>(null);

  useTopic<LiveMatch>(`/topic/matches/${id}`, (state) => {
    if (!scorer.busy()) qc.setQueryData(keys.match(id), state);
  });

  const match = q.data;
  const moment = useMoments(match);

  if (q.isLoading) return <Spinner />;
  if (q.error || !match) {
    return (
      <div className="mx-auto max-w-xl p-6">
        <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
      </div>
    );
  }

  const inProgress = match.status === "IN_PROGRESS";
  const canScore = inProgress && !!me && (me.role === "ADMIN" || match.scorerIds.includes(me.id));
  const view = mode ?? (canScore ? "score" : "watch");

  return (
    <div className="min-h-dvh">
      <MatchTopBar match={match} connected={connected} canScore={canScore} view={view} onView={setMode} />
      {view === "score" && canScore ? <ScorerView match={match} scorer={scorer} /> : <WatchView match={match} />}
      <CelebrationOverlay moment={moment} />
    </div>
  );
}

function MatchTopBar({
  match,
  connected,
  canScore,
  view,
  onView,
}: {
  match: LiveMatch;
  connected: boolean;
  canScore: boolean;
  view: "score" | "watch";
  onView: (v: "score" | "watch") => void;
}) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const share = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // clipboard blocked – nothing to do
    }
  };
  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-mortar bg-night/95 px-3 backdrop-blur">
      <Link to="/" className="flex items-center gap-2 rounded-md p-1.5 text-trim-dim hover:text-trim" aria-label={t("common.back")}>
        <ArrowLeft className="size-5" />
        <LogoMark className="size-7" />
      </Link>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">
          {match.players[0].realName} {t("common.vs")} {match.players[1].realName}
        </p>
        <p className="truncate text-xs text-trim-faint">
          {match.tournament ? `${match.tournament.name}, ` : ""}
          {rulesSummary(match.config)}
          {match.rated ? "" : `, ${t("common.unrated").toLowerCase()}`}
        </p>
      </div>
      {match.status === "IN_PROGRESS" ? (
        <span className={`hidden items-center gap-1.5 text-xs font-bold uppercase tracking-wider sm:flex ${connected ? "text-trafon-bright" : "text-trim-faint"}`}>
          <Radio className={`size-4 ${connected ? "animate-pulse" : ""}`} aria-hidden />
          {connected ? t("match.connection.live") : t("match.connection.reconnecting")}
        </span>
      ) : (
        <span className="rounded bg-masonry-2 px-2 py-0.5 text-xs font-bold uppercase tracking-wider text-trim-dim">{t(`match.status.${match.status}`)}</span>
      )}
      {canScore && (
        <div className="flex rounded-md border border-mortar p-0.5 text-xs font-bold">
          <button
            onClick={() => onView("score")}
            aria-pressed={view === "score"}
            className={`flex items-center gap-1 rounded px-2 py-1 ${view === "score" ? "bg-trafon text-trim" : "text-trim-dim"}`}
          >
            <Target className="size-3.5" aria-hidden />
            {t("match.scorer")}
          </button>
          <button
            onClick={() => onView("watch")}
            aria-pressed={view === "watch"}
            className={`flex items-center gap-1 rounded px-2 py-1 ${view === "watch" ? "bg-trafon text-trim" : "text-trim-dim"}`}
          >
            <Eye className="size-3.5" aria-hidden />
            {t("match.watch")}
          </button>
        </div>
      )}
      <button onClick={share} className="rounded-md p-2 text-trim-dim hover:text-trim" title={t("match.shareLink")} aria-label={t("match.shareLink")}>
        <Link2 className="size-5" />
      </button>
      {copied && <span className="sr-only" role="status">{t("match.linkCopied")}</span>}
      <span className="hidden md:block">
        <LanguageSwitch />
      </span>
    </header>
  );
}

function ScorerView({ match, scorer }: { match: LiveMatch; scorer: ReturnType<typeof useScorer> }) {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const navigate = useNavigate();
  const bust = match.turnJustEnded && match.lastCompletedTurn?.bust;

  return (
    <div className="grid gap-3 p-3 md:min-h-[calc(100dvh-3.5rem)] md:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)] md:p-4 landscape:md:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
      <div className="flex flex-col gap-3">
        <div className="grid grid-cols-2 gap-3 pt-3">
          {match.players.map((p, i) => (
            <ArchScore
              key={p.id}
              player={p}
              remaining={match.remaining[i]}
              legsWon={match.legsWon[i]}
              legsToWin={match.config.legsToWin}
              stats={match.stats[i]}
              active={match.currentPlayerIndex === i}
              needsDouble={!match.opened[i]}
              bust={bust && match.lastCompletedTurn?.playerIndex === i}
              size="sm"
            />
          ))}
        </div>
        <TurnStrip match={match} pending={scorer.pending} />
        <CheckoutHint match={match} />
        {scorer.error !== null && (
          <div role="alert" className="rounded-md border border-trafon-deep bg-trafon-deep/20 px-3 py-2 text-sm">
            {errorText(scorer.error)}
          </div>
        )}
        <div className="mt-auto hidden items-center justify-between gap-2 md:flex">
          <span className="text-xs text-trim-faint">{t("match.legN", { n: match.currentLeg })}</span>
          <ConfirmButton
            label={t("match.abandon")}
            message={t("match.abandonConfirm")}
            onConfirm={async () => {
              await api.post(`/api/matches/${match.id}/abandon`);
              navigate("/");
            }}
          />
        </div>
      </div>
      <div className="min-h-[52dvh] md:min-h-0">
        <Keypad onDart={scorer.throwDart} onUndo={scorer.undo} disabled={match.status !== "IN_PROGRESS"} canUndo={match.dartCount > 0 || scorer.pending.length > 0} />
      </div>
      <div className="flex justify-end md:hidden">
        <ConfirmButton
          label={t("match.abandon")}
          message={t("match.abandonConfirm")}
          onConfirm={async () => {
            await api.post(`/api/matches/${match.id}/abandon`);
            navigate("/");
          }}
        />
      </div>
    </div>
  );
}

function WatchView({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  const finished = match.status !== "IN_PROGRESS";
  const bust = match.turnJustEnded && match.lastCompletedTurn?.bust;
  const finishedLegs = match.legs.filter((l) => l.winnerIndex !== null);
  const [tab, setTab] = useState<AnalysisTab>("overview");
  return (
    <div className="mx-auto max-w-5xl px-4 pb-16 pt-6">
      {match.status === "COMPLETED" && match.winnerIndex !== null && <WinnerBanner match={match} />}
      <div className="grid grid-cols-2 gap-4 pt-4 sm:gap-8">
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
        <div className="mt-4 flex flex-col gap-3">
          <TurnStrip match={match} />
          <CheckoutHint match={match} />
        </div>
      )}
      <AnalysisTabs value={tab} onChange={setTab} />
      {tab === "turns" && <TurnByTurn match={match} />}
      {tab === "analysis" && <MatchAnalysisPanel match={match} />}
      {tab === "h2h" && <MatchHeadToHead match={match} />}
      {tab === "overview" && (
      <div className={`mt-6 grid gap-6 ${finishedLegs.length > 0 ? "lg:grid-cols-[1.4fr_1fr]" : ""}`}>
        <section className="panel p-4">
          <h2 className="mb-2 font-display text-xl font-bold uppercase tracking-wide">{t("match.statsTitle")}</h2>
          <MatchStatsTable match={match} />
        </section>
        {finishedLegs.length > 0 && (
        <section className="panel p-4">
          <h2 className="mb-2 font-display text-xl font-bold uppercase tracking-wide">{t("match.legHistory")}</h2>
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
      )}
      {match.status === "COMPLETED" && <DisputeAndRematch match={match} />}
      <MatchSocial matchId={match.id} />
    </div>
  );
}

function WinnerBanner({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  const w = match.winnerIndex!;
  return (
    <div className="mb-6 flex flex-wrap items-center gap-4 rounded-xl border border-brass/60 bg-gradient-to-r from-brass/20 to-transparent px-5 py-4">
      <Trophy className="size-8 text-brass" aria-hidden />
      <div className="flex-1">
        <p className="font-display text-3xl font-extrabold uppercase leading-none">{t("match.matchWon", { name: match.players[w].realName })}</p>
        <p className="numerals mt-1 text-xl text-trim-dim">
          {match.legsWon[0]}–{match.legsWon[1]}, {t("match.average")} {num(match.stats[w].average, 2)}
        </p>
      </div>
      {match.disputed && <span className="rounded bg-salmon/20 px-2 py-1 text-xs font-bold uppercase text-salmon">{t("match.disputed")}</span>}
    </div>
  );
}

function DisputeAndRematch({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [sent, setSent] = useState(false);
  const qc = useQueryClient();
  const isPlayer = !!me && match.players.some((p) => p.id === me.id);
  if (!isPlayer) return null;

  const rematch = () => {
    const c = match.config;
    navigate(
      `/play?p1=${match.players[1].id}&p2=${match.players[0].id}&score=${c.startScore}&in=${c.inRule}&out=${c.outRule}&legs=${c.legsToWin}&rated=${match.rated ? 1 : 0}`,
    );
  };

  return (
    <div className="mt-8 flex flex-wrap items-center gap-3">
      <Button size="lg" onClick={rematch}>
        {t("match.rematch")}
      </Button>
      {!match.disputed && !sent && (
        <Button variant="ghost" onClick={() => setOpen(true)}>
          <Flag className="size-4" aria-hidden />
          {t("match.dispute")}
        </Button>
      )}
      {sent && <p className="text-sm text-board-bright">{t("match.disputeSent")}</p>}
      <Modal open={open} onClose={() => setOpen(false)} title={t("match.dispute")}>
        <p className="mb-4 text-sm text-trim-dim">{t("match.disputeLead")}</p>
        <label className="mb-1.5 block text-sm font-medium text-trim-dim" htmlFor="dispute-reason">
          {t("match.disputeReason")}
        </label>
        <textarea
          id="dispute-reason"
          maxLength={500}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          className="h-28 w-full rounded-md border border-mortar bg-night p-3 text-trim focus:border-trafon focus:outline-none"
        />
        {error !== null && (
          <div className="mt-3">
            <ErrorNotice error={error} />
          </div>
        )}
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t("common.cancel")}
          </Button>
          <Button
            busy={busy}
            disabled={!reason.trim()}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                await api.post(`/api/matches/${match.id}/disputes`, { reason: reason.trim() });
                setSent(true);
                setOpen(false);
                void qc.invalidateQueries({ queryKey: keys.match(match.id) });
              } catch (err) {
                setError(err);
              } finally {
                setBusy(false);
              }
            }}
          >
            {t("match.disputeSend")}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
