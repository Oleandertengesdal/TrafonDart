import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { ArrowLeftRight } from "lucide-react";
import { usePlayers, useRecordResult } from "../api/queries";
import type { InRule, OutRule } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { Button, ErrorNotice, Field, PageHeader, Segmented, Spinner, Toggle } from "../components/ui";
import { isFinishedScore, resultDateRange } from "../lib/confirmation";
import { usePageTitle } from "../lib/pageTitle";
import { startScoreOptions } from "../lib/x01";
import { PlayerSelect } from "./NewMatchPage";

/**
 * Registers only the result of a match played without scoring the darts. Pick the winner and how many legs the loser
 * got, so the score is always a finished match. It waits for the other player to confirm it, like any match.
 */
export function RegisterResultPage() {
  const { t } = useTranslation();
  usePageTitle(t("result.title"));
  const { me } = useCurrentUser();
  const players = usePlayers();
  const navigate = useNavigate();
  const record = useRecordResult();
  const range = resultDateRange();

  const [p1, setP1] = useState<number | null>(me?.id ?? null);
  const [p2, setP2] = useState<number | null>(null);
  const [winner, setWinner] = useState<0 | 1>(0);
  const [startScore, setStartScore] = useState(501);
  const [inRule, setInRule] = useState<InRule>("STRAIGHT");
  const [outRule, setOutRule] = useState<OutRule>("DOUBLE");
  const [legsToWin, setLegsToWin] = useState(3);
  const [loserLegs, setLoserLegs] = useState(0);
  const [playedOn, setPlayedOn] = useState(range.max);
  const [rated, setRated] = useState(true);

  if (players.isLoading) return <Spinner />;
  if (players.error) return <ErrorNotice error={players.error} onRetry={() => players.refetch()} />;
  const list = players.data ?? [];
  const includesMe = !!me && (p1 === me.id || p2 === me.id);
  const ratedAllowed = me?.role === "ADMIN" || includesMe;
  const name = (id: number | null) => list.find((p) => p.id === id)?.realName ?? "–";

  const loser = Math.min(loserLegs, legsToWin - 1);
  const legsWon: [number, number] = winner === 0 ? [legsToWin, loser] : [loser, legsToWin];
  const dateOk = playedOn >= range.min && playedOn <= range.max;
  const ready = !!p1 && !!p2 && p1 !== p2 && isFinishedScore(legsToWin, legsWon) && dateOk;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!ready) return;
    record.mutate(
      { player1Id: p1!, player2Id: p2!, startScore, inRule, outRule, legsToWin, legsWon, rated: rated && ratedAllowed, playedOn },
      { onSuccess: (m) => navigate(`/matches/${m.id}`) },
    );
  };

  return (
    <>
      <PageHeader title={t("result.title")} lead={t("result.lead")} />
      <form onSubmit={submit} className="grid gap-8 lg:grid-cols-[1fr_1fr] [&>*]:min-w-0">
        <section className="panel flex flex-col gap-5 p-5">
          <div className="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]">
            <PlayerSelect label={t("match.player1")} value={p1} onChange={setP1} players={list} exclude={p2} />
            <button
              type="button"
              onClick={() => {
                setP1(p2);
                setP2(p1);
                setWinner(winner === 0 ? 1 : 0);
              }}
              className="mx-auto mb-2 rounded-full border border-mortar p-2 text-trim-dim hover:text-trim"
              aria-label={t("match.swap")}
              title={t("match.swap")}
            >
              <ArrowLeftRight className="size-4" />
            </button>
            <PlayerSelect label={t("match.player2")} value={p2} onChange={setP2} players={list} exclude={p1} />
          </div>
          <Segmented
            label={t("result.winner")}
            value={winner}
            onChange={setWinner}
            options={[
              { value: 0 as const, label: name(p1) },
              { value: 1 as const, label: name(p2) },
            ]}
          />
          <Segmented
            label={t("result.loserLegs", { name: name(winner === 0 ? p2 : p1) })}
            value={loser}
            onChange={setLoserLegs}
            options={Array.from({ length: legsToWin }, (_, i) => ({ value: i, label: String(i) }))}
          />
          <p className="flex items-baseline gap-3" aria-live="polite">
            <span className="text-sm text-trim-dim">{t("result.score")}</span>
            <span className="numerals text-4xl leading-none">
              {legsWon[0]}–{legsWon[1]}
            </span>
          </p>
          <Field
            label={t("result.playedOn")}
            type="date"
            min={range.min}
            max={range.max}
            value={playedOn}
            onChange={(e) => setPlayedOn(e.target.value)}
            hint={t("result.playedOnHint")}
            required
          />
          <div className="border-t border-mortar pt-5">
            <Toggle label={t("match.rated")} hint={ratedAllowed ? undefined : t("match.ratedHint")} checked={rated && !!ratedAllowed} onChange={setRated} />
          </div>
        </section>

        <section className="panel flex flex-col gap-5 p-5">
          <Segmented label={t("match.startScore")} value={startScore} onChange={setStartScore} options={startScoreOptions} />
          <Segmented
            label={t("match.inRule")}
            value={inRule}
            onChange={setInRule}
            options={(["STRAIGHT", "DOUBLE"] as InRule[]).map((v) => ({ value: v, label: t(`match.in.${v}`) }))}
          />
          <Segmented
            label={t("match.outRule")}
            value={outRule}
            onChange={setOutRule}
            options={(["STRAIGHT", "DOUBLE", "MASTER"] as OutRule[]).map((v) => ({ value: v, label: t(`match.out.${v}`) }))}
          />
          <Segmented
            label={t("match.legsToWin")}
            value={legsToWin}
            onChange={setLegsToWin}
            options={[1, 2, 3, 4, 5, 7].map((v) => ({ value: v, label: String(v) }))}
            hint={t("match.legsCount", { count: legsToWin })}
          />
        </section>

        <div className="flex flex-col gap-3 lg:col-span-2">
          <p className="text-sm text-trim-dim">{t("result.counts")}</p>
          {record.error !== null && <ErrorNotice error={record.error} />}
          <Button type="submit" size="lg" busy={record.isPending} disabled={!ready} className="w-full sm:w-auto sm:self-end">
            {t("result.submit")}
          </Button>
        </div>
      </form>
    </>
  );
}
