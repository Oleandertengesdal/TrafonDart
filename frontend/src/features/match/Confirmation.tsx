import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Check, Hourglass, Scale } from "lucide-react";
import { useConfirmMatch, useToConfirm } from "../../api/queries";
import type { LiveMatch, MatchSummary } from "../../api/types";
import { useCurrentUser } from "../../auth/auth";
import { Button, ErrorNotice } from "../../components/ui";
import { confirmationState } from "../../lib/confirmation";
import { dateTime } from "../../lib/format";

/**
 * On a finished match that doesn't count yet: whose confirmation it waits for and when it counts by itself, with the
 * Confirm button for the player (or admin) who can. A disputed one says an admin decides.
 */
export function ConfirmationPanel({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const confirm = useConfirmMatch();
  const s = confirmationState(match, me);
  if (!s.waiting) return null;

  const auto = match.confirmation?.autoConfirmAt;
  // "yours" only for the player it waits for; an admin who isn't that player sees who it waits for, and a note
  const mine = !!me && !match.disputed && (match.confirmation?.confirmerIds ?? []).includes(me.id);
  const adminOverride = s.canConfirm && !mine;
  const Icon = match.disputed ? Scale : mine ? Check : Hourglass;
  return (
    <section
      aria-labelledby="confirm-title"
      className={`mb-6 flex flex-col gap-4 rounded-xl border px-5 py-4 sm:flex-row sm:items-center ${mine ? "border-brass/60 bg-brass/10" : "border-mortar bg-masonry"}`}
    >
      <Icon className={`size-7 shrink-0 ${mine ? "text-brass" : "text-trim-dim"}`} aria-hidden />
      <div className="flex-1">
        <h2 id="confirm-title" className="font-display text-xl leading-tight">
          {match.disputed
            ? t("confirm.disputedTitle")
            : mine
              ? t("confirm.yoursTitle")
              : t("confirm.waitingTitle", { names: s.waitingFor.join(` ${t("common.or")} `) })}
        </h2>
        <p className="mt-1 max-w-prose text-sm text-trim-dim">
          {match.disputed
            ? t("confirm.disputedLead")
            : mine
              ? t("confirm.yoursLead")
              : t("confirm.waitingLead", { when: auto ? dateTime(auto) : "" })}
          {adminOverride && ` ${t("confirm.adminNote")}`}
        </p>
        {confirm.error !== null && (
          <div className="mt-3">
            <ErrorNotice error={confirm.error} />
          </div>
        )}
      </div>
      {s.canConfirm && (
        <Button
          variant={mine ? "primary" : "secondary"}
          onClick={() => confirm.mutate(match.id)}
          busy={confirm.isPending}
          className="sm:self-center"
        >
          <Check className="size-4" aria-hidden />
          {t("confirm.button")}
        </Button>
      )}
    </section>
  );
}

/** Home page: results waiting for this player to confirm them. Hidden when there are none. */
export function ToConfirmCard() {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const list = useToConfirm(!!me);
  const confirm = useConfirmMatch();
  const items = list.data ?? [];
  if (items.length === 0) return null;
  return (
    <section aria-labelledby="confirm-list-title" className="rounded-xl border border-brass/60 bg-brass/10 p-5">
      <h2 id="confirm-list-title" className="font-display text-2xl">
        {t("confirm.homeTitle")}
      </h2>
      <p className="mt-1 text-sm text-trim-dim">{t("confirm.homeLead")}</p>
      {confirm.error !== null && (
        <div className="mt-3">
          <ErrorNotice error={confirm.error} />
        </div>
      )}
      <ul className="mt-4 flex flex-col divide-y divide-mortar">
        {items.map((m) => (
          <ToConfirmRow key={m.id} match={m} busy={confirm.isPending && confirm.variables === m.id} onConfirm={() => confirm.mutate(m.id)} />
        ))}
      </ul>
    </section>
  );
}

function ToConfirmRow({ match, busy, onConfirm }: { match: MatchSummary; busy: boolean; onConfirm: () => void }) {
  const { t } = useTranslation();
  return (
    <li className="flex flex-wrap items-center gap-3 py-3">
      <Link to={`/matches/${match.id}`} className="min-w-0 flex-1 hover:underline">
        <span className="font-semibold">{match.players[0].realName}</span>
        <span className="numerals mx-2 whitespace-nowrap text-xl">
          {match.legsWon[0]}–{match.legsWon[1]}
        </span>
        <span className="font-semibold">{match.players[1].realName}</span>
        <span className="block text-xs text-trim-faint">
          {match.resultOnly ? `${t("match.resultOnly")}, ` : ""}
          {match.completedAt ? dateTime(match.completedAt) : ""}
        </span>
      </Link>
      <Button size="sm" onClick={onConfirm} busy={busy}>
        <Check className="size-4" aria-hidden />
        {t("confirm.button")}
      </Button>
    </li>
  );
}
