import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { LiveMatch } from "../../api/types";
import { chime, soundPrefs, speak } from "../../lib/sound";

import type { TFunction } from "i18next";
import type { TurnView } from "../../api/types";

export type Moment =
  | { kind: "oneEighty" }
  | { kind: "highFinish"; score: number }
  | { kind: "bust"; name: string }
  | { kind: "legWon"; name: string }
  | { kind: "matchWon"; name: string };

/**
 * A finished turn as a moment: the flash to show, with its chime and (optionally) a spoken callout played now. A high
 * finish is followed by the leg or match banner through {@code later}.
 */
export function celebrate(turn: TurnView, name: string, matchOver: boolean, t: TFunction, later: (m: Moment) => void): Moment | null {
  if (turn.bust) {
    chime("bust");
    if (soundPrefs.caller) speak(t("callouts.bust"));
    return { kind: "bust", name };
  }
  if (turn.checkout) {
    if (turn.total >= 100) {
      chime("finish");
      speak(t("callouts.highFinish", { score: turn.total }));
      setTimeout(() => later(matchOver ? { kind: "matchWon", name } : { kind: "legWon", name }), 1700);
      return { kind: "highFinish", score: turn.total };
    }
    chime("leg");
    speak(t(matchOver ? "callouts.matchWon" : "callouts.gameShot"));
    return matchOver ? { kind: "matchWon", name } : { kind: "legWon", name };
  }
  if (turn.total === 180) {
    chime("oneEighty");
    speak(t("callouts.oneEighty"));
    return { kind: "oneEighty" };
  }
  if (soundPrefs.caller) speak(t("callouts.score", { score: turn.total }));
  return null;
}

/** The moment on screen, cleared after a while. */
export function useMomentState(): [Moment | null, (m: Moment | null) => void] {
  const [moment, setMoment] = useState<Moment | null>(null);
  useEffect(() => {
    if (!moment) return;
    const h = setTimeout(() => setMoment(null), moment.kind === "matchWon" ? 4000 : 1700);
    return () => clearTimeout(h);
  }, [moment]);
  return [moment, setMoment];
}

/**
 * Watches the match state and turns notable moments into a flash overlay, a chime and (optionally) a spoken
 * callout. Works the same for the scorer and for spectators, because both receive the same states.
 */
export function useMoments(match: LiveMatch | undefined): Moment | null {
  const { t } = useTranslation();
  const prev = useRef<number | null>(null);
  const [moment, setMoment] = useMomentState();

  useEffect(() => {
    if (!match) return;
    const before = prev.current;
    prev.current = match.dartCount;
    // Only react to a new dart arriving (not first load, not undo).
    if (before === null || match.dartCount <= before || !match.turnJustEnded || !match.lastCompletedTurn) return;
    const turn = match.lastCompletedTurn;
    const next = celebrate(turn, match.players[turn.playerIndex].realName, match.lastEvent === "MATCH_WON", t, setMoment);
    if (next) setMoment(next);
  }, [match, t, setMoment]);

  return moment;
}

export function CelebrationOverlay({ moment }: { moment: Moment | null }) {
  const { t } = useTranslation();
  if (!moment) return null;

  let big = "";
  let small = "";
  let tone = "text-trim";
  switch (moment.kind) {
    case "oneEighty":
      big = "180";
      small = t("match.oneEighty");
      tone = "text-brass";
      break;
    case "highFinish":
      big = String(moment.score);
      small = t("match.highFinish", { score: moment.score });
      tone = "text-brass";
      break;
    case "bust":
      big = t("match.bust");
      small = moment.name;
      tone = "text-salmon";
      break;
    case "legWon":
      big = t("match.gameShot");
      small = t("match.legWon", { name: moment.name });
      break;
    case "matchWon":
      big = t("match.gameShot");
      small = t("match.matchWon", { name: moment.name });
      tone = "text-brass";
      break;
  }

  return (
    <div className="pointer-events-none fixed inset-0 z-50 flex items-center justify-center" aria-live="assertive">
      <div className="animate-flash flex flex-col items-center rounded-2xl border border-mortar bg-night/90 px-10 py-8 text-center shadow-2xl backdrop-blur">
        <span className={`numerals text-score-xl font-extrabold  leading-none ${tone}`}>{big}</span>
        <span className="mt-2 font-display text-2xl text-trim">{small}</span>
      </div>
    </div>
  );
}
