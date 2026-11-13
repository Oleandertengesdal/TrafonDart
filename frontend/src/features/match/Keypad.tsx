import { Undo2 } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { chime, vibrate } from "../../lib/sound";

type Modifier = 1 | 2 | 3;

/**
 * Touch keypad for the board-side tablet. Pick Double or Treble first (it resets after each dart),
 * then the number. Big targets, instant visual and haptic feedback.
 */
export function Keypad({
  onDart,
  onUndo,
  disabled,
  canUndo,
}: {
  onDart: (segment: number, multiplier: number) => void;
  onUndo: () => void;
  disabled: boolean;
  canUndo: boolean;
}) {
  const { t } = useTranslation();
  const [mod, setMod] = useState<Modifier>(1);

  const hit = (segment: number, multiplier: number) => {
    if (disabled) return;
    vibrate(8);
    chime("tap");
    onDart(segment, multiplier);
    setMod(1);
  };

  const modButton = (value: Modifier, label: string, activeClass: string) => (
    <button
      type="button"
      aria-pressed={mod === value}
      disabled={disabled}
      onClick={() => setMod(mod === value ? 1 : value)}
      className={`h-14 rounded-lg border-2 font-display text-xl    transition-colors disabled:opacity-40 ${
        mod === value ? activeClass : "border-mortar bg-masonry text-trim-dim hover:text-trim"
      }`}
    >
      {label}
    </button>
  );

  const numberTint = mod === 2 ? "border-board/70" : mod === 3 ? "border-trafon/80" : "border-transparent";
  const prefix = mod === 2 ? "D" : mod === 3 ? "T" : "";

  return (
    <div role="group" aria-label={t("match.keypad")} className="flex h-full select-none flex-col gap-2">
      <div className="grid grid-cols-2 gap-2">
        {modButton(2, t("match.double"), "border-board bg-board text-trim")}
        {modButton(3, t("match.treble"), "border-trafon bg-trafon text-trim")}
      </div>
      <div className="grid flex-1 grid-cols-5 gap-2">
        {Array.from({ length: 20 }, (_, i) => i + 1).map((n) => (
          <button
            key={n}
            type="button"
            disabled={disabled}
            onClick={() => hit(n, mod)}
            aria-label={`${prefix}${n}`}
            className={`numerals min-h-14 rounded-lg border-2 bg-masonry-2 text-key font-bold text-trim transition-transform active:scale-95 active:bg-trafon disabled:opacity-40 ${numberTint}`}
          >
            {mod !== 1 && <span className="mr-0.5 align-top text-sm font-semibold text-trim-dim">{prefix}</span>}
            {n}
          </button>
        ))}
      </div>
      <div className="grid grid-cols-4 gap-2">
        <button
          type="button"
          disabled={disabled || mod !== 1}
          onClick={() => hit(25, 1)}
          className="numerals h-14 rounded-lg border-2 border-transparent bg-masonry-2 text-2xl font-bold text-trim active:scale-95 active:bg-trafon disabled:opacity-40"
        >
          25
        </button>
        <button
          type="button"
          disabled={disabled || mod === 3}
          onClick={() => hit(25, 2)}
          className="h-14 rounded-lg border-2 border-board/60 bg-masonry-2 font-display text-xl text-trim active:scale-95 active:bg-trafon disabled:opacity-40"
        >
          {t("match.bull")}
        </button>
        <button
          type="button"
          disabled={disabled}
          onClick={() => hit(0, 0)}
          className="h-14 rounded-lg border-2 border-transparent bg-masonry font-display text-xl text-trim-dim active:scale-95 disabled:opacity-40"
        >
          {t("match.miss")}
        </button>
        <button
          type="button"
          disabled={!canUndo}
          onClick={() => {
            vibrate(15);
            onUndo();
            setMod(1);
          }}
          className="flex h-14 items-center justify-center gap-2 rounded-lg border-2 border-mortar bg-night font-semibold text-trim-dim hover:text-trim active:scale-95 disabled:opacity-40"
        >
          <Undo2 className="size-5" aria-hidden />
          {t("match.undo")}
        </button>
      </div>
    </div>
  );
}
