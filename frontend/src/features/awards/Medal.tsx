import { Activity, Crown, Star, Target, TrendingUp, Zap, type LucideIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useAwardRace } from "../../api/queries";
import type { AwardType } from "../../api/types";
import { monthName, num } from "../../lib/format";

export const AWARD_ORDER: AwardType[] = ["PLAYER_OF_MONTH", "MOST_IMPROVED", "MOST_ACTIVE", "MOST_180S", "HIGHEST_CHECKOUT"];

const ICON: Record<AwardType, LucideIcon> = {
  PLAYER_OF_MONTH: Crown,
  MOST_IMPROVED: TrendingUp,
  MOST_ACTIVE: Activity,
  MOST_180S: Target,
  HIGHEST_CHECKOUT: Zap,
  PLAYER_OF_YEAR: Star,
};

/** Player of the Month is brass (the one champion's colour); the side awards wear the facade palette. */
const TONE: Record<AwardType, string> = {
  PLAYER_OF_MONTH: "border-brass bg-brass/15 text-brass",
  MOST_IMPROVED: "border-board-bright/70 bg-board/15 text-board-bright",
  MOST_ACTIVE: "border-salmon/70 bg-salmon/10 text-salmon",
  MOST_180S: "border-trafon-bright/80 bg-trafon/15 text-trafon-bright",
  HIGHEST_CHECKOUT: "border-trim-dim/70 bg-trim/10 text-trim",
  PLAYER_OF_YEAR: "border-brass bg-brass text-night",
};

/** The value of an award formatted for its category ("+42 rating", "9 kamper", "121 i utgang"). */
export function useAwardValue() {
  const { t } = useTranslation();
  return (award: AwardType, value: number) => t(`awards.values.${award}`, { value: num(Math.round(value)) });
}

/** A little arched window, like the ones on the facade, with the award's icon inside. */
export function Medal({ award, size = "md", title }: { award: AwardType; size?: "sm" | "md" | "lg"; title?: string }) {
  const Icon = ICON[award];
  const dims = { sm: "h-8 w-7 pt-1.5", md: "h-12 w-10 pt-2.5", lg: "h-20 w-16 pt-4" }[size];
  const icon = { sm: "size-3.5", md: "size-5", lg: "size-8" }[size];
  return (
    <span
      className={`inline-flex shrink-0 justify-center rounded-t-full border-2 ${TONE[award]} ${dims}`}
      role={title ? "img" : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
      title={title}
    >
      <Icon className={icon} strokeWidth={2.25} aria-hidden />
    </span>
  );
}

/** Chip for the profile hero: "Månedens spiller, oktober 2026". */
export function ReigningChip({ period }: { period: string }) {
  const { t } = useTranslation();
  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-brass/70 bg-brass/10 py-1 pl-1.5 pr-3 text-sm font-semibold text-brass">
      <Crown className="size-4" aria-hidden />
      {t("awards.reigning", { month: monthName(period) })}
    </span>
  );
}

/** Small crown after a name in lists, shown for the reigning Player of the Month. */
export function CrownMark({ period }: { period: string }) {
  const { t } = useTranslation();
  return (
    <Crown className="size-4 shrink-0 text-brass" role="img" aria-label={t("awards.reigning", { month: monthName(period) })}>
      <title>{t("awards.reigning", { month: monthName(period) })}</title>
    </Crown>
  );
}

/** Who reigns as Player of the Month right now (last month's winner), if anyone. */
export function useReigning(): { id: number; period: string } | null {
  const race = useAwardRace();
  const r = race.data?.reigning;
  return r?.player ? { id: r.player.id, period: r.period } : null;
}
