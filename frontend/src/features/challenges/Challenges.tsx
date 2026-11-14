import { useQueryClient } from "@tanstack/react-query";
import { Check, Play, Swords, X } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { api, apiPath } from "../../api/client";
import { keys, useChallenges } from "../../api/queries";
import type { ChallengeView, OutRule, PlayerSummary } from "../../api/types";
import { useCurrentUser } from "../../auth/auth";
import { Button, ErrorNotice, Modal, Segmented, Toggle, useErrorText } from "../../components/ui";
import { relative, rulesSummary } from "../../lib/format";
import { startScoreOptions } from "../../lib/x01";

/** "Challenge" on someone's profile: pick the format, add a line, send. */
export function ChallengeButton({ opponent }: { opponent: PlayerSummary }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [startScore, setStartScore] = useState(501);
  const [outRule, setOutRule] = useState<OutRule>("DOUBLE");
  const [legsToWin, setLegsToWin] = useState(3);
  const [rated, setRated] = useState(true);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [sent, setSent] = useState(false);

  const send = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.post("/api/challenges", { opponentId: opponent.id, startScore, inRule: "STRAIGHT", outRule, legsToWin, rated, message: message.trim() || null });
      setSent(true);
      setOpen(false);
      void qc.invalidateQueries({ queryKey: keys.challenges });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)} disabled={sent}>
        <Swords className="size-4" aria-hidden />
        {sent ? t("challenges.sent") : t("challenges.challenge")}
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} title={t("challenges.titleFor", { name: opponent.realName })}>
        <div className="flex flex-col gap-4">
          <Segmented label={t("match.startScore")} value={startScore} onChange={setStartScore} options={startScoreOptions} />
          <Segmented label={t("match.outRule")} value={outRule} onChange={setOutRule} options={(["STRAIGHT", "DOUBLE", "MASTER"] as OutRule[]).map((v) => ({ value: v, label: t(`match.out.${v}`) }))} />
          <Segmented label={t("match.legsToWin")} value={legsToWin} onChange={setLegsToWin} options={[1, 2, 3, 4, 5].map((v) => ({ value: v, label: String(v) }))} />
          <Toggle label={t("match.rated")} checked={rated} onChange={setRated} />
          <div className="flex flex-col gap-2">
            <label htmlFor="challenge-msg" className="text-sm font-medium text-trim-dim">
              {t("challenges.message")} <span className="text-trim-faint">({t("common.optional")})</span>
            </label>
            <input
              id="challenge-msg"
              maxLength={200}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder={t("challenges.messagePlaceholder")}
              className="h-11 rounded-md border border-mortar bg-night px-3 text-trim placeholder:text-trim-faint focus:border-trafon focus:outline-none"
            />
          </div>
          <p className="text-sm text-trim-faint">{t("challenges.howItWorks")}</p>
          {error !== null && <ErrorNotice error={error} />}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)}>
              {t("common.cancel")}
            </Button>
            <Button busy={busy} onClick={send}>
              {t("challenges.send")}
            </Button>
          </div>
        </div>
      </Modal>
    </>
  );
}

function Row({ c, incoming }: { c: ChallengeView; incoming: boolean }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const errorText = useErrorText();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const other = incoming ? c.challenger : c.opponent;

  const act = async (action: "accept" | "decline" | "cancel" | "start") => {
    setBusy(action);
    setError(null);
    try {
      const next = await api.post<ChallengeView>(apiPath`/api/challenges/${c.id}/${action}`);
      void qc.invalidateQueries({ queryKey: keys.challenges });
      if (action === "start" && next.matchId) navigate(`/matches/${next.matchId}`);
    } catch (err) {
      setError(err);
      void qc.invalidateQueries({ queryKey: keys.challenges });
    } finally {
      setBusy(null);
    }
  };

  const status =
    c.status === "ACCEPTED" ? t("challenges.ready") : incoming ? t("challenges.wantsToPlay", { name: other.realName }) : t("challenges.waitingFor", { name: other.realName });

  return (
    <li className={`flex flex-col gap-2 rounded-lg border px-4 py-3 ${c.status === "ACCEPTED" ? "border-board-bright/70 bg-board/10" : "border-mortar bg-masonry"}`}>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <Swords className="size-5 shrink-0 text-trafon-bright" aria-hidden />
        <span className="font-semibold">{c.status === "ACCEPTED" ? `${c.challenger.realName} ${t("common.vs")} ${c.opponent.realName}` : status}</span>
        {c.status === "ACCEPTED" && (
          <span className="rounded bg-board/30 px-2 py-0.5 text-xs font-bold text-board-bright">{status}</span>
        )}
        <span className="text-sm text-trim-faint">
          {rulesSummary(c)}
          {c.rated ? "" : `, ${t("common.unrated").toLowerCase()}`}
        </span>
      </div>
      {c.message && <p className="text-sm italic text-trim-dim">“{c.message}”</p>}
      <div className="flex flex-wrap items-center gap-2">
        {c.status === "ACCEPTED" && (
          <Button size="sm" busy={busy === "start"} onClick={() => act("start")}>
            <Play className="size-4" aria-hidden />
            {t("challenges.start")}
          </Button>
        )}
        {c.status === "PENDING" && incoming && (
          <>
            <Button size="sm" busy={busy === "accept"} onClick={() => act("accept")}>
              <Check className="size-4" aria-hidden />
              {t("challenges.accept")}
            </Button>
            <Button size="sm" variant="ghost" busy={busy === "decline"} onClick={() => act("decline")}>
              {t("challenges.decline")}
            </Button>
          </>
        )}
        {(c.status === "ACCEPTED" || !incoming) && (
          <Button size="sm" variant="ghost" busy={busy === "cancel"} onClick={() => act("cancel")}>
            <X className="size-4" aria-hidden />
            {t("challenges.cancel")}
          </Button>
        )}
        <span className="ml-auto text-xs text-trim-faint">{t("challenges.expires", { when: relative(c.expiresAt) })}</span>
      </div>
      {error !== null && <p className="text-sm text-salmon">{errorText(error)}</p>}
    </li>
  );
}

/** On the home page: challenges waiting for you, accepted ones ready to start, and your own invites. */
export function ChallengesCard() {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const q = useChallenges(!!me);
  const incoming = q.data?.incoming ?? [];
  const outgoing = q.data?.outgoing ?? [];
  if (incoming.length + outgoing.length === 0) return null;
  // accepted ones first: they're ready to play
  const order = (list: ChallengeView[]) => [...list].sort((a, b) => Number(b.status === "ACCEPTED") - Number(a.status === "ACCEPTED"));
  return (
    <section aria-labelledby="challenges-title">
      <h2 id="challenges-title" className="mb-3 font-display text-2xl">
        {t("challenges.title")}
      </h2>
      <ul className="flex flex-col gap-2">
        {order(incoming).map((c) => (
          <Row key={c.id} c={c} incoming />
        ))}
        {order(outgoing).map((c) => (
          <Row key={c.id} c={c} incoming={false} />
        ))}
      </ul>
    </section>
  );
}
