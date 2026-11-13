import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import type { PlayerSummary } from "../api/types";

export function PlayerLink({ player, showUsername = true, className = "" }: { player: PlayerSummary | null; showUsername?: boolean; className?: string }) {
  const { t } = useTranslation();
  if (!player) return <span className="text-trim-faint">{t("tournament.tbd")}</span>;
  // A deleted account has no profile to link to.
  if (player.deleted) return <span className={`truncate italic text-trim-dim ${className}`}>{player.realName}</span>;
  return (
    <Link to={`/players/${player.username}`} className={`group inline-flex min-w-0 flex-col leading-tight ${className}`}>
      <span className="truncate font-semibold text-trim group-hover:underline">{player.realName}</span>
      {showUsername && <span className="truncate text-xs text-trim-faint">@{player.username}</span>}
    </Link>
  );
}

export function RatingBadge({ player, size = "sm" }: { player: Pick<PlayerSummary, "rating" | "provisional">; size?: "sm" | "lg" }) {
  const { t } = useTranslation();
  return (
    <span
      className={`inline-flex items-baseline gap-1 ${size === "lg" ? "numerals text-4xl font-bold" : "tnum font-semibold"} ${player.provisional ? "text-trim-dim" : "text-trim"}`}
      title={player.provisional ? t("common.provisionalHint") : undefined}
    >
      {player.rating}
      {player.provisional && (
        <>
          <span aria-hidden className="text-xs font-semibold text-trim-faint">
            ~
          </span>
          <span className="sr-only">{t("common.provisional")}</span>
        </>
      )}
    </span>
  );
}

export function FormDots({ form }: { form: ("W" | "L")[] }) {
  const { t } = useTranslation();
  return (
    <span className="inline-flex gap-1" aria-label={t("stats.form")}>
      {form.map((r, i) => (
        <span
          key={i}
          className={`flex size-6 items-center justify-center rounded-sm text-xs font-bold ${r === "W" ? "bg-board text-trim" : "bg-masonry-2 text-trim-dim"}`}
        >
          {r === "W" ? t("stats.win") : t("stats.loss")}
        </span>
      ))}
    </span>
  );
}
