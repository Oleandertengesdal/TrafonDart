import { useTranslation } from "react-i18next";
import type { LivePlayerStats, PlayerSummary } from "../../api/types";
import { num } from "../../lib/format";

/**
 * The signature element: a player's remaining score under Trafon's arched gable. The thrower's arch is the facade
 * itself – brick red with the white arched trim; the other player waits in charcoal. A winner gets a lamplight frame.
 */
export function ArchScore({
  player,
  remaining,
  legsWon,
  legsToWin,
  stats,
  active,
  winner,
  needsDouble,
  bust,
  size = "md",
}: {
  player: PlayerSummary;
  remaining: number;
  legsWon: number;
  legsToWin: number;
  stats: LivePlayerStats;
  active: boolean;
  winner?: boolean;
  needsDouble?: boolean;
  bust?: boolean;
  size?: "sm" | "md" | "lg";
}) {
  const { t } = useTranslation();
  const numberSize = { sm: "text-score-md", md: "text-score-lg", lg: "text-score-xl" }[size];
  return (
    <section
      aria-label={player.realName}
      aria-current={active ? "true" : undefined}
      className={`arch relative flex flex-col items-center px-3 pb-3 pt-[18%] transition-colors duration-300 ${
        active
          ? "border-2 border-t-[6px] border-trim bg-trafon"
          : winner
            ? "border-2 border-t-[6px] border-brass bg-masonry"
            : "border-2 border-t-[6px] border-mortar bg-masonry"
      } ${bust ? "animate-shake" : ""}`}
    >
      <div className="flex w-full flex-col items-center text-center">
        <span className={`max-w-full truncate font-display ${size === "sm" ? "text-lg" : "text-2xl"}`}>{player.realName}</span>
        <span className={`text-xs ${active ? "text-brick-100" : "text-trim-faint"}`}>@{player.username}</span>
      </div>

      <div
        className={`numerals my-1 font-extrabold leading-none ${numberSize} ${active ? "text-trim" : "text-trim-dim"}`}
        aria-label={`${t("match.remaining")}: ${remaining}`}
      >
        {remaining}
      </div>

      <LegLamps won={legsWon} total={legsToWin} />

      <dl className={`mt-2 grid w-full grid-cols-2 gap-2 text-center ${size === "sm" ? "text-xs" : "text-sm"}`}>
        <div>
          <dt className={active ? "text-brick-100" : "text-trim-faint"}>{t("match.average")}</dt>
          <dd className="numerals text-lg font-bold">{num(stats.average, 1)}</dd>
        </div>
        <div>
          <dt className={active ? "text-brick-100" : "text-trim-faint"}>{t("match.first9")}</dt>
          <dd className="numerals text-lg font-bold">{num(stats.first9Average, 1)}</dd>
        </div>
      </dl>

      {active && (
        <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-trim px-3 py-0.5 text-xs font-bold text-night">
          {t("match.throwing")}
        </span>
      )}
      {needsDouble && <span className={`mt-1 text-xs ${active ? "text-brick-100" : "text-salmon"}`}>{t("match.needsDouble")}</span>}
    </section>
  );
}

/** One lamp per leg needed; lit lamps are legs won. */
export function LegLamps({ won, total }: { won: number; total: number }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-2" aria-label={`${t("match.legs")}: ${won}/${total}`}>
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          className={`size-3 rounded-full border ${i < won ? "border-brass bg-brass" : "border-mortar bg-night"}`}
        />
      ))}
    </div>
  );
}
