import { Check, Crosshair, Heart, Skull } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { GameView, SideState } from "../../api/types";
import { dartLabel } from "../../lib/format";
import { CRICKET_TARGETS, clockTargetLabel, markLevel, sideLabel } from "./gameLogic";

function activeSide(game: GameView): number | null {
  if (game.status !== "IN_PROGRESS") return null;
  return game.players.find((p) => p.slot === game.currentSlot)?.side ?? null;
}

/** A cricket mark drawn as the chalk sign: one stroke, a cross, a cross in a closed circle. */
function CricketMark({ level }: { level: 0 | 1 | 2 | 3 }) {
  if (level === 0) return null;
  return (
    <svg viewBox="0 0 24 24" className="mx-auto size-6" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" aria-hidden="true">
      <path d="M17 7 7 17" />
      {level >= 2 && <path d="M7 7l10 10" />}
      {level === 3 && <circle cx="12" cy="12" r="9.5" strokeWidth="2.2" />}
    </svg>
  );
}

/** The right scoreboard for the game type. */
export function GameBoard({ game, size = "md" }: { game: GameView; size?: "md" | "lg" }) {
  switch (game.type) {
    case "X01":
      return <X01Board game={game} size={size} />;
    case "CRICKET":
      return <CricketBoard game={game} size={size} />;
    case "KILLER":
      return <KillerBoard game={game} size={size} />;
    case "AROUND_THE_CLOCK":
      return <ClockBoard game={game} size={size} />;
  }
}

function sideClass(game: GameView, s: SideState) {
  const active = activeSide(game) === s.side;
  const won = game.winnerSide === s.side;
  return `panel relative flex flex-col gap-1 p-3 ${active ? "border-trafon ring-2 ring-trafon/60" : ""} ${won ? "border-brass ring-2 ring-brass/60" : ""}`;
}

function X01Board({ game, size }: { game: GameView; size: "md" | "lg" }) {
  const { t } = useTranslation();
  const legs = game.settings.legsToWin ?? 1;
  return (
    <ul className={`grid gap-3 ${game.sides.length > 2 ? "grid-cols-2 md:grid-cols-4" : "grid-cols-2"}`}>
      {game.sides.map((s) => (
        <li key={s.side} className={sideClass(game, s)} aria-current={activeSide(game) === s.side ? "true" : undefined}>
          <span className="truncate text-sm font-semibold text-trim-dim">{sideLabel(game, s.side)}</span>
          <span className={`numerals font-extrabold leading-none ${size === "lg" ? "text-score-lg" : "text-6xl"}`}>{s.remaining}</span>
          <span className="flex items-center gap-2 text-xs text-trim-faint">
            {legs > 1 && <span>{t("games.legs", { won: s.legsWon, of: legs })}</span>}
            {s.opened === false && <span className="text-salmon">{t("match.needsDouble")}</span>}
          </span>
        </li>
      ))}
    </ul>
  );
}

function CricketBoard({ game, size }: { game: GameView; size: "md" | "lg" }) {
  const { t } = useTranslation();
  const closedByAll = (target: number) => game.sides.every((s) => (s.marks?.[String(target)] ?? 0) >= 3);
  const cell = size === "lg" ? "py-2 text-4xl" : "py-1 text-2xl";
  return (
    <div className="panel overflow-x-auto p-2">
      <table className="w-full table-fixed text-center">
        <caption className="sr-only">{t("games.types.CRICKET")}</caption>
        <thead>
          <tr>
            <th scope="col" className="w-14 text-xs font-semibold text-trim-faint">
              {t("games.target")}
            </th>
            {game.sides.map((s) => (
              <th
                key={s.side}
                scope="col"
                title={sideLabel(game, s.side)}
                className={`truncate px-1 py-2 text-sm font-semibold ${activeSide(game) === s.side ? "text-trafon-bright" : game.winnerSide === s.side ? "text-brass" : "text-trim-dim"}`}
              >
                {sideLabel(game, s.side)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {CRICKET_TARGETS.map((target) => (
            <tr key={target} className={`border-t border-mortar/60 ${closedByAll(target) ? "opacity-35" : ""}`}>
              <th scope="row" className={`numerals font-bold ${cell}`}>
                {target === 25 ? t("games.bull") : target}
              </th>
              {game.sides.map((s) => {
                const m = s.marks?.[String(target)] ?? 0;
                return (
                  <td key={s.side} className={`numerals font-bold ${cell} ${m >= 3 ? "text-board-bright" : "text-trim"}`} aria-label={t("games.marks", { count: Math.min(m, 3) })}>
                    <CricketMark level={markLevel(m)} />
                  </td>
                );
              })}
            </tr>
          ))}
          <tr className="border-t-2 border-mortar">
            <th scope="row" className="py-2 text-xs font-semibold text-trim-faint">
              {t("games.points")}
            </th>
            {game.sides.map((s) => (
              <td key={s.side} className={`numerals font-extrabold ${size === "lg" ? "text-6xl" : "text-4xl"}`}>
                {s.points ?? 0}
              </td>
            ))}
          </tr>
        </tbody>
      </table>
      {(game.settings.roundLimit ?? 0) > 0 && (
        <p className="px-2 pt-1 text-right text-xs text-trim-faint">{t("games.roundOf", { round: game.round, of: game.settings.roundLimit })}</p>
      )}
    </div>
  );
}

function KillerBoard({ game, size }: { game: GameView; size: "md" | "lg" }) {
  const { t } = useTranslation();
  const max = game.settings.lives ?? 3;
  return (
    <ul className="grid grid-cols-2 gap-3 md:grid-cols-4">
      {game.sides.map((s) => (
        <li key={s.side} className={`${sideClass(game, s)} ${s.out ? "opacity-40" : ""}`}>
          <span className={`truncate text-sm font-semibold ${s.out ? "text-trim-faint line-through" : "text-trim-dim"}`}>{sideLabel(game, s.side)}</span>
          <span className="flex items-baseline gap-2">
            <span className={`numerals font-extrabold leading-none ${size === "lg" ? "text-8xl" : "text-6xl"}`} title={t("games.yourNumber")}>
              {s.number}
            </span>
            {s.killer && !s.out && (
              <span className="inline-flex items-center gap-1 rounded bg-trafon px-2 py-0.5 text-xs font-bold text-trim">
                <Crosshair className="size-3.5" aria-hidden />
                {t("games.killer")}
              </span>
            )}
            {s.out && <Skull className="size-6 text-trim-faint" aria-label={t("games.out")} />}
          </span>
          <span className="flex gap-1" aria-label={t("games.livesLeft", { count: s.lives ?? 0 })}>
            {Array.from({ length: max }, (_, i) => (
              <Heart key={i} className={`size-5 ${i < (s.lives ?? 0) ? "fill-trafon-bright text-trafon-bright" : "text-mortar"}`} aria-hidden />
            ))}
          </span>
        </li>
      ))}
    </ul>
  );
}

function ClockBoard({ game, size }: { game: GameView; size: "md" | "lg" }) {
  const { t } = useTranslation();
  return (
    <ul className="grid grid-cols-2 gap-3 md:grid-cols-4">
      {game.sides.map((s) => {
        const done = !!s.finished;
        const position = done ? 21 : s.target === 25 ? 20 : (s.target ?? 1) - 1;
        return (
          <li key={s.side} className={sideClass(game, s)}>
            <span className="truncate text-sm font-semibold text-trim-dim">{sideLabel(game, s.side)}</span>
            <span className="text-xs text-trim-faint">{done ? t("games.finished") : t("games.next")}</span>
            <span className={`numerals font-extrabold leading-none ${done ? "text-board-bright" : ""} ${size === "lg" ? "text-8xl" : "text-6xl"}`}>
              {done ? <Check className="size-[0.8em]" strokeWidth={3} aria-label={t("games.finished")} /> : clockTargetLabel(s.target)}
            </span>
            <span className="mt-1 h-2 overflow-hidden rounded-full bg-night" role="progressbar" aria-valuemin={0} aria-valuemax={21} aria-valuenow={position}>
              <span className="block h-full rounded-full bg-board" style={{ width: `${(position / 21) * 100}%` }} />
            </span>
          </li>
        );
      })}
    </ul>
  );
}

/** Who's throwing, the darts of this turn (plus queued ones), and the X01 checkout. */
export function GameTurn({ game, pending = [] }: { game: GameView; pending?: { segment: number; multiplier: number }[] }) {
  const { t } = useTranslation();
  const thrower = game.players.find((p) => p.slot === game.currentSlot)?.player;
  const label = (d: { segment: number; multiplier: number }) =>
    d.segment === 0 ? "MISS" : d.segment === 25 ? (d.multiplier === 2 ? "BULL" : "25") : `${["S", "D", "T"][d.multiplier - 1]}${d.segment}`;
  const slots = [...game.turnDarts.map((l) => ({ l, p: false })), ...pending.map((d) => ({ l: label(d), p: true }))].slice(0, 3);
  while (slots.length < 3) slots.push({ l: "", p: false });
  if (game.status !== "IN_PROGRESS") return null;
  return (
    <div className="panel flex flex-wrap items-center gap-3 px-3 py-3">
      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-semibold text-trim-faint">{thrower?.realName}</p>
        <div className="mt-1 flex gap-2">
          {slots.map((s, i) => (
            <span
              key={i}
              className={`numerals flex h-11 min-w-16 items-center justify-center rounded-md border px-2 text-2xl font-bold ${
                s.p ? "border-dashed border-trim-faint text-trim-faint" : s.l ? "border-mortar bg-night text-trim" : "border-mortar/60 text-trim-faint"
              }`}
            >
              {s.l ? dartLabel(s.l) : "·"}
            </span>
          ))}
        </div>
      </div>
      {game.checkout && game.checkout.length > 0 && (
        <p className="text-right text-sm">
          <span className="block text-xs font-semibold text-trim-faint">{t("match.checkout")}</span>
          <span className="numerals text-2xl font-bold text-board-bright">{game.checkout.map(dartLabel).join(" ")}</span>
        </p>
      )}
    </div>
  );
}
