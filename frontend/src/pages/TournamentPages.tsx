import { useQueryClient } from "@tanstack/react-query";
import { Crown, Trophy } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { api, apiPath } from "../api/client";
import { keys, usePlayers, useTournament, useTournaments } from "../api/queries";
import type { FixtureView, InRule, OutRule, TournamentDetail, TournamentFormat } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { PlayerLink } from "../components/PlayerName";
import { Button, ConfirmButton, EmptyState, ErrorNotice, Field, LinkButton, PageHeader, SectionTitle, Segmented, Spinner, Toggle } from "../components/ui";
import { date, rulesSummary } from "../lib/format";
import { useTopic } from "../realtime/useTopic";
import { usePageTitle } from "../lib/pageTitle";
import { NotFound } from "./NotFoundPage";
import { isNotFound } from "../lib/errors";

// ------------------------------------------------------------------ list

export function TournamentsPage() {
  const { t } = useTranslation();
  usePageTitle(t("tournament.title"));
  const q = useTournaments();
  return (
    <>
      <PageHeader title={t("tournament.title")} lead={t("tournament.lead")} action={<LinkButton to="/tournaments/new">{t("tournament.new")}</LinkButton>} />
      {q.isLoading ? (
        <Spinner />
      ) : q.error ? (
        <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
      ) : (q.data ?? []).length === 0 ? (
        <EmptyState title={t("tournament.empty")} action={<LinkButton to="/tournaments/new">{t("tournament.new")}</LinkButton>} />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {q.data!.map((x) => (
            <li key={x.id}>
              <Link to={`/tournaments/${x.id}`} className="panel flex h-full flex-col gap-2 p-5 hover:border-trim-faint">
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-display text-3xl leading-none">{x.name}</h2>
                  <StatusPill status={x.status} />
                </div>
                <p className="text-sm text-trim-dim">
                  {t(`tournament.formats.${x.format}`)}, {t("tournament.participantsCount", { count: x.participantCount })}, {rulesSummary(x.config)}
                </p>
                {x.winner && (
                  <p className="mt-auto flex items-center gap-2 pt-2 font-semibold text-brass">
                    <Crown className="size-4" aria-hidden /> {x.winner.realName}
                  </p>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function StatusPill({ status }: { status: string }) {
  const { t } = useTranslation();
  const tone =
    status === "IN_PROGRESS" ? "bg-trafon text-trim" : status === "COMPLETED" ? "bg-brass/20 text-brass" : status === "CANCELLED" ? "bg-masonry-2 text-trim-faint" : "bg-masonry-2 text-trim-dim";
  return <span className={`shrink-0 rounded px-2 py-0.5 text-xs font-bold   ${tone}`}>{t(`tournament.statuses.${status}`)}</span>;
}

// ------------------------------------------------------------------ create

export function NewTournamentPage() {
  const { t } = useTranslation();
  usePageTitle(t("tournament.new"));
  const players = usePlayers();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [name, setName] = useState("");
  const [format, setFormat] = useState<TournamentFormat>("ROUND_ROBIN");
  const [startScore, setStartScore] = useState(501);
  const [inRule, setInRule] = useState<InRule>("STRAIGHT");
  const [outRule, setOutRule] = useState<OutRule>("DOUBLE");
  const [legsToWin, setLegsToWin] = useState(2);
  const [rated, setRated] = useState(true);
  const [selected, setSelected] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const d = await api.post<TournamentDetail>("/api/tournaments", {
        name: name.trim(),
        format,
        startScore,
        inRule,
        outRule,
        legsToWin,
        rated,
        participantIds: selected,
      });
      void qc.invalidateQueries({ queryKey: keys.tournaments });
      navigate(`/tournaments/${d.summary.id}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  const toggle = (id: number) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  return (
    <>
      <PageHeader title={t("tournament.new")} lead={t("tournament.newLead")} />
      <form onSubmit={submit} className="grid gap-8 lg:grid-cols-2">
        <section className="panel flex flex-col gap-5 p-5">
          <Field label={t("tournament.name")} placeholder={t("tournament.namePlaceholder")} value={name} maxLength={60} onChange={(e) => setName(e.target.value)} />
          <Segmented
            label={t("tournament.format")}
            value={format}
            onChange={setFormat}
            hint={t(`tournament.formatHints.${format}`)}
            options={(["ROUND_ROBIN", "SINGLE_ELIMINATION"] as TournamentFormat[]).map((v) => ({ value: v, label: t(`tournament.formats.${v}`) }))}
          />
          <Segmented label={t("match.startScore")} value={startScore} onChange={setStartScore} options={[301, 501, 701].map((v) => ({ value: v, label: String(v) }))} />
          <Segmented label={t("match.inRule")} value={inRule} onChange={setInRule} options={(["STRAIGHT", "DOUBLE"] as InRule[]).map((v) => ({ value: v, label: t(`match.in.${v}`) }))} />
          <Segmented label={t("match.outRule")} value={outRule} onChange={setOutRule} options={(["STRAIGHT", "DOUBLE", "MASTER"] as OutRule[]).map((v) => ({ value: v, label: t(`match.out.${v}`) }))} />
          <Segmented label={t("match.legsToWin")} value={legsToWin} onChange={setLegsToWin} options={[1, 2, 3, 4, 5].map((v) => ({ value: v, label: String(v) }))} />
          <Toggle label={t("match.rated")} checked={rated} onChange={setRated} />
        </section>
        <section className="panel flex flex-col gap-3 p-5">
          <div className="flex items-baseline justify-between">
            <h2 className="font-display text-2xl">{t("tournament.participants")}</h2>
            <span className="text-sm text-trim-dim">{t("tournament.participantsCount", { count: selected.length })}</span>
          </div>
          {players.isLoading ? (
            <Spinner />
          ) : (
            <ul className="grid gap-2 sm:grid-cols-2">
              {(players.data ?? []).map((p) => {
                const on = selected.includes(p.id);
                return (
                  <li key={p.id}>
                    <button
                      type="button"
                      aria-pressed={on}
                      onClick={() => toggle(p.id)}
                      className={`flex w-full items-center justify-between gap-2 rounded-md border px-3 py-3 text-left transition-colors ${
                        on ? "border-trafon bg-trafon/15" : "border-mortar hover:border-trim-faint"
                      }`}
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-semibold">{p.realName}</span>
                        <span className="block text-xs text-trim-faint">@{p.username}</span>
                      </span>
                      <span className="tnum text-sm text-trim-dim">{p.rating}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          {selected.length < 2 && <p className="text-sm text-trim-faint">{t("tournament.minPlayers")}</p>}
        </section>
        <div className="flex flex-col gap-3 lg:col-span-2">
          {error !== null && <ErrorNotice error={error} />}
          <Button type="submit" size="lg" busy={busy} disabled={!name.trim() || selected.length < 2} className="sm:self-end">
            {busy ? t("tournament.creating") : t("tournament.create")}
          </Button>
        </div>
      </form>
    </>
  );
}

// ------------------------------------------------------------------ detail

export function TournamentPage() {
  const { id = "" } = useParams();
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const q = useTournament(id);
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useTopic(`/topic/tournaments/${id}`, () => void qc.invalidateQueries({ queryKey: keys.tournament(id) }));
  usePageTitle(q.data?.summary.name);

  if (q.isLoading) return <Spinner />;
  if (q.error || !q.data) {
    return isNotFound(q.error) ? <NotFound what="tournament" /> : <ErrorNotice error={q.error} onRetry={() => q.refetch()} resource />;
  }
  const d = q.data;
  const s = d.summary;
  const canManage = !!me && (me.id === d.organiserId || me.role === "ADMIN");

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      await qc.invalidateQueries({ queryKey: keys.tournament(id) });
      void qc.invalidateQueries({ queryKey: keys.tournaments });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  const play = (f: FixtureView) =>
    run(async () => {
      const res = await api.post<{ matchId: string }>(apiPath`/api/tournaments/${id}/fixtures/${f.id}/start`);
      navigate(`/matches/${res.matchId}`);
    });

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-wrap items-end justify-between gap-4 border-b border-mortar pb-6">
        <div>
          <div className="flex items-center gap-3">
            <StatusPill status={s.status} />
            <span className="text-sm text-trim-faint">{t(`tournament.formats.${s.format}`)}</span>
          </div>
          <h1 className="mt-2 font-display text-5xl leading-none sm:text-6xl">{s.name}</h1>
          <p className="mt-2 text-trim-dim">
            {rulesSummary(s.config)}
            {s.rated ? "" : `, ${t("common.unrated").toLowerCase()}`}
          </p>
          {s.organiser && <p className="text-sm text-trim-faint">{t("tournament.organiser", { name: s.organiser.realName })}, {date(s.createdAt)}</p>}
        </div>
        {s.winner && (
          <div className="arch flex flex-col items-center border-2 border-brass bg-masonry px-8 pb-4 pt-10 text-center">
            <Trophy className="size-8 text-brass" aria-hidden />
            <span className="mt-1 text-xs font-bold text-brass">{t("tournament.champion")}</span>
            <span className="font-display text-3xl">{s.winner.realName}</span>
          </div>
        )}
      </header>

      {error !== null && <ErrorNotice error={error} />}

      {s.status === "DRAFT" && (
        <section className="panel flex flex-col gap-4 p-5">
          <SectionTitle>{t("tournament.participants")}</SectionTitle>
          <ul className="flex flex-wrap gap-2">
            {d.participants.map((p) => (
              <li key={p.player.id} className="rounded-md border border-mortar px-3 py-2">
                <PlayerLink player={p.player} />
              </li>
            ))}
          </ul>
          {canManage && (
            <div className="flex flex-wrap items-center gap-3 border-t border-mortar pt-4">
              <Button busy={busy} onClick={() => run(() => api.post(apiPath`/api/tournaments/${id}/start`))} disabled={d.participants.length < 2}>
                {t("tournament.start")}
              </Button>
              <p className="text-sm text-trim-faint">{t("tournament.startHint")}</p>
            </div>
          )}
        </section>
      )}

      {s.format === "ROUND_ROBIN" && s.status !== "DRAFT" && <Standings d={d} />}
      {s.format === "SINGLE_ELIMINATION" && s.status !== "DRAFT" && <Bracket d={d} canPlay={(f) => canStart(f, me?.id, canManage)} onPlay={play} busy={busy} />}
      {s.format === "ROUND_ROBIN" && s.status !== "DRAFT" && <FixtureRounds d={d} canPlay={(f) => canStart(f, me?.id, canManage)} onPlay={play} busy={busy} />}

      {canManage && (s.status === "DRAFT" || s.status === "IN_PROGRESS") && (
        <div className="flex justify-end">
          <ConfirmButton label={t("tournament.cancel")} message={t("tournament.cancelConfirm")} onConfirm={() => run(() => api.post(apiPath`/api/tournaments/${id}/cancel`))} />
        </div>
      )}
    </div>
  );
}

function canStart(f: FixtureView, myId: number | undefined, canManage: boolean) {
  if (f.status !== "READY" && f.status !== "IN_PROGRESS") return false;
  return canManage || (!!myId && (f.player1?.id === myId || f.player2?.id === myId));
}

function Standings({ d }: { d: TournamentDetail }) {
  const { t } = useTranslation();
  return (
    <section>
      <SectionTitle>{t("tournament.standings")}</SectionTitle>
      <div className="panel overflow-x-auto">
        <table className="w-full min-w-[30rem]">
          <thead className="text-left text-xs text-trim-faint">
            <tr>
              <th className="w-10 px-4 py-3">#</th>
              <th className="px-2 py-3">{t("players.player")}</th>
              <th className="px-2 py-3 text-right">{t("stats.played")}</th>
              <th className="px-2 py-3 text-right">{t("tournament.w")}</th>
              <th className="px-2 py-3 text-right">{t("tournament.l")}</th>
              <th className="px-2 py-3 text-right">{t("stats.legsShort")}</th>
              <th className="px-2 py-3 text-right">{t("tournament.legDiff")}</th>
              <th className="px-4 py-3 text-right">{t("tournament.pts")}</th>
            </tr>
          </thead>
          <tbody>
            {d.standings.map((r, i) => (
              <tr key={r.player.id} className="border-t border-mortar/60">
                <td className={`numerals px-4 py-3 text-xl font-bold ${i === 0 ? "text-brass" : "text-trim-faint"}`}>{i + 1}</td>
                <td className="px-2">
                  <PlayerLink player={r.player} showUsername={false} />
                </td>
                <td className="tnum px-2 text-right text-trim-dim">{r.played}</td>
                <td className="tnum px-2 text-right">{r.won}</td>
                <td className="tnum px-2 text-right text-trim-dim">{r.lost}</td>
                <td className="tnum px-2 text-right text-trim-dim">
                  {r.legsFor}–{r.legsAgainst}
                </td>
                <td className="tnum px-2 text-right">{r.legDiff > 0 ? `+${r.legDiff}` : r.legDiff}</td>
                <td className="tnum px-4 text-right text-xl font-bold">{r.points}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function FixtureCard({ f, canPlay, onPlay, busy }: { f: FixtureView; canPlay: boolean; onPlay: (f: FixtureView) => void; busy: boolean }) {
  const { t } = useTranslation();
  const side = (slot: 1 | 2) => {
    const p = slot === 1 ? f.player1 : f.player2;
    const won = f.winnerSlot === slot;
    const lost = f.winnerSlot !== null && !won;
    return (
      <div className={`flex items-center justify-between gap-2 px-3 py-2 ${lost ? "opacity-50" : ""}`}>
        {p ? (
          <span className={`truncate ${won ? "font-bold text-trim" : "font-medium"}`}>{p.realName}</span>
        ) : (
          <span className="text-sm text-trim-faint">{f.status === "BYE" ? t("tournament.bye") : t("tournament.tbd")}</span>
        )}
        {f.legsWon && <span className={`numerals text-lg font-bold ${won ? "text-trim" : "text-trim-dim"}`}>{f.legsWon[slot - 1]}</span>}
      </div>
    );
  };
  return (
    <div className={`panel overflow-hidden ${f.status === "IN_PROGRESS" ? "border-trafon" : ""}`}>
      {side(1)}
      <div className="border-t border-mortar/60" />
      {side(2)}
      <div className="flex items-center justify-between gap-2 border-t border-mortar/60 bg-night/40 px-3 py-2">
        <span className={`text-xs font-bold   ${f.status === "IN_PROGRESS" ? "text-trafon-bright" : "text-trim-faint"}`}>
          {t(`tournament.fixtureStatus.${f.status}`)}
        </span>
        {f.matchId && f.status !== "READY" ? (
          <Link to={`/matches/${f.matchId}`} className="text-xs font-semibold text-trim-dim hover:text-trim">
            {f.status === "IN_PROGRESS" && canPlay ? t("tournament.play") : t("tournament.watch")}
          </Link>
        ) : canPlay && f.status === "READY" ? (
          <Button size="sm" busy={busy} onClick={() => onPlay(f)} className="h-7">
            {t("tournament.play")}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

function FixtureRounds({ d, canPlay, onPlay, busy }: { d: TournamentDetail; canPlay: (f: FixtureView) => boolean; onPlay: (f: FixtureView) => void; busy: boolean }) {
  const { t } = useTranslation();
  const rounds = Array.from({ length: d.rounds }, (_, i) => i + 1);
  return (
    <section>
      <SectionTitle>{t("tournament.fixtures")}</SectionTitle>
      <div className="flex flex-col gap-6">
        {rounds.map((r) => (
          <div key={r}>
            <h3 className="mb-2 text-sm font-bold text-trim-faint">{t("tournament.round", { n: r })}</h3>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {d.fixtures
                .filter((f) => f.round === r)
                .map((f) => (
                  <FixtureCard key={f.id} f={f} canPlay={canPlay(f)} onPlay={onPlay} busy={busy} />
                ))}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function roundName(t: (k: string, o?: Record<string, unknown>) => string, round: number, rounds: number) {
  const fromEnd = rounds - round;
  if (fromEnd === 0) return t("tournament.final");
  if (fromEnd === 1) return t("tournament.semiFinal");
  if (fromEnd === 2) return t("tournament.quarterFinal");
  return t("tournament.roundOf", { n: 2 ** (fromEnd + 1) });
}

/** Knockout bracket: one column per round, fixtures vertically centred against their feeders. */
function Bracket({ d, canPlay, onPlay, busy }: { d: TournamentDetail; canPlay: (f: FixtureView) => boolean; onPlay: (f: FixtureView) => void; busy: boolean }) {
  const { t } = useTranslation();
  const rounds = Array.from({ length: d.rounds }, (_, i) => i + 1);
  return (
    <section>
      <SectionTitle>{t("tournament.bracket")}</SectionTitle>
      <div className="overflow-x-auto pb-2">
        <div className="grid min-w-max auto-cols-[15rem] grid-flow-col gap-6">
          {rounds.map((r) => (
            <div key={r} className="flex flex-col">
              <h3 className="mb-3 text-sm font-bold text-trim-faint">{roundName(t, r, d.rounds)}</h3>
              <div className="flex flex-1 flex-col justify-around gap-4">
                {d.fixtures
                  .filter((f) => f.round === r)
                  .map((f) => (
                    <FixtureCard key={f.id} f={f} canPlay={canPlay(f)} onPlay={onPlay} busy={busy} />
                  ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
