import { useTranslation } from "react-i18next";
import type { LiveMatch } from "../../api/types";
import { dartLabel } from "../../lib/format";

export interface PendingDart {
  segment: number;
  multiplier: number;
}

function pendingLabel(d: PendingDart): string {
  if (d.segment === 0) return "MISS";
  if (d.segment === 25) return d.multiplier === 2 ? "BULL" : "25";
  return (d.multiplier === 1 ? "S" : d.multiplier === 2 ? "D" : "T") + d.segment;
}

/** Three slots for the current turn, the running total and the checkout path. */
export function TurnStrip({ match, pending = [] }: { match: LiveMatch; pending?: PendingDart[] }) {
  const { t } = useTranslation();
  const turn = match.currentTurn;
  const showing = turn ?? (match.turnJustEnded ? match.lastCompletedTurn : null);
  const owner = showing ? match.players[showing.playerIndex] : match.players[match.currentPlayerIndex];

  const slots: { label: string; points?: number; pending?: boolean }[] = [];
  showing?.darts.forEach((d) => slots.push({ label: dartLabel(d.label), points: d.points }));
  if (!showing || showing === turn) {
    pending.forEach((p) => slots.push({ label: dartLabel(pendingLabel(p)), pending: true }));
  }
  while (slots.length < 3) slots.push({ label: "" });

  const total = showing ? showing.total : 0;
  const ended = showing && showing !== turn;

  return (
    <div className="panel flex flex-wrap items-center gap-3 px-3 py-3">
      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-semibold text-trim-faint">
          {owner.realName}
          {ended && showing!.bust && <span className="ml-2 text-salmon">{t("match.bust")}</span>}
        </p>
        <div className="mt-1 flex gap-2">
          {slots.slice(0, 3).map((s, i) => (
            <span
              key={i}
              className={`numerals flex h-11 min-w-16 items-center justify-center rounded-md border px-2 text-2xl font-bold ${
                s.pending ? "border-dashed border-trim-faint text-trim-faint" : s.label ? "border-mortar bg-night text-trim" : "border-mortar/60 text-trim-faint"
              }`}
            >
              {s.label || "·"}
            </span>
          ))}
        </div>
      </div>
      <div className="text-right">
        <p className="text-xs font-semibold text-trim-faint">{t("match.thisTurn")}</p>
        <p className={`numerals text-4xl font-extrabold leading-none ${showing?.bust ? "text-salmon line-through" : "text-trim"}`}>{total}</p>
      </div>
    </div>
  );
}

/** The suggested route for the current thrower, or nothing when there's no finish on. */
export function CheckoutHint({ match }: { match: LiveMatch }) {
  const { t } = useTranslation();
  if (match.status !== "IN_PROGRESS" || !match.checkout) return null;
  return (
    <div className="flex items-center gap-3 rounded-md border border-board/60 bg-board/15 px-3 py-2">
      <span className="text-xs font-bold text-board-bright">{t("match.checkout")}</span>
      <span className="numerals flex gap-2 text-2xl font-bold text-trim">
        {match.checkout.map((d, i) => (
          <span key={i}>{dartLabel(d)}</span>
        ))}
      </span>
    </div>
  );
}
