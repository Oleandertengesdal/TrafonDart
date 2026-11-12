import {
  Award,
  Circle,
  Clock3,
  Crown,
  Crosshair,
  Flame,
  Footprints,
  Gauge,
  Goal,
  Lock,
  Medal as MedalIcon,
  Rocket,
  Shield,
  Sparkles,
  Star,
  Swords,
  Target,
  Trophy,
  TrendingUp,
  Zap,
  type LucideIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { useAchievements } from "../../api/queries";
import type { Achievement, AchievementCode } from "../../api/types";
import { ErrorNotice, SectionTitle, Spinner } from "../../components/ui";
import { date, num } from "../../lib/format";

const ICONS: Record<AchievementCode, LucideIcon> = {
  FIRST_WIN: Sparkles,
  MATCHES_10: Footprints,
  MATCHES_50: Footprints,
  MATCHES_100: Footprints,
  WINS_25: TrendingUp,
  STREAK_5: Flame,
  FIRST_180: Target,
  MAX_COLLECTOR: Target,
  TON_PLUS_CHECKOUT: Zap,
  BIG_FISH: Rocket,
  BULL_FINISH: Circle,
  SHORT_LEG: Gauge,
  NINE_DARTER: Star,
  AVERAGE_50: Gauge,
  AVERAGE_70: Gauge,
  GIANT_KILLER: Swords,
  TOURNAMENT_WIN: Trophy,
  PLAYER_OF_MONTH: Crown,
  PLAYER_OF_YEAR: Award,
  CRICKET_WIN: Goal,
  KILLER_WIN: Crosshair,
  CLOCK_UNDER_40: Clock3,
  DARTS_10K: Shield,
};

/** The rarest ones are brass; the rest wear the board green once unlocked. */
const RARE = new Set<AchievementCode>(["NINE_DARTER", "BIG_FISH", "PLAYER_OF_YEAR", "MATCHES_100", "DARTS_10K", "GIANT_KILLER"]);

export function AchievementIcon({ code, unlocked, size = "md" }: { code: AchievementCode; unlocked: boolean; size?: "sm" | "md" }) {
  const Icon = unlocked ? (ICONS[code] ?? MedalIcon) : Lock;
  const dims = size === "sm" ? "h-7 w-6 pt-1" : "h-14 w-12 pt-3";
  const icon = size === "sm" ? "size-3.5" : "size-6";
  const tone = !unlocked ? "border-mortar bg-night text-trim-faint" : RARE.has(code) ? "border-brass bg-brass/20 text-brass" : "border-board-bright/70 bg-board/15 text-board-bright";
  return (
    <span className={`inline-flex shrink-0 justify-center rounded-t-full border-2 ${tone} ${dims}`} aria-hidden>
      <Icon className={icon} strokeWidth={2.25} />
    </span>
  );
}

function Item({ a }: { a: Achievement }) {
  const { t } = useTranslation();
  const link = a.matchId ? `/matches/${a.matchId}` : a.gameId ? `/games/${a.gameId}` : null;
  const showProgress = !a.unlocked && a.target !== null && a.progress !== null;
  const body = (
    <>
      <AchievementIcon code={a.code} unlocked={a.unlocked} />
      <span className="min-w-0 flex-1">
        <span className={`block font-semibold ${a.unlocked ? "text-trim" : "text-trim-dim"}`}>{t(`achievements.${a.code}.title`)}</span>
        <span className="block text-sm text-trim-faint">{t(`achievements.${a.code}.how`)}</span>
        {a.unlocked && a.unlockedAt && <span className="mt-0.5 block text-xs text-board-bright">{t("achievements.unlockedOn", { date: date(a.unlockedAt) })}</span>}
        {showProgress && (
          <span className="mt-1.5 flex items-center gap-2">
            <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-night" role="progressbar" aria-valuemin={0} aria-valuemax={a.target!} aria-valuenow={a.progress!}>
              <span className="block h-full rounded-full bg-trim-dim" style={{ width: `${(a.progress! / a.target!) * 100}%` }} />
            </span>
            <span className="tnum text-xs text-trim-faint">
              {num(a.progress!)}/{num(a.target!)}
            </span>
          </span>
        )}
      </span>
    </>
  );
  const cls = `flex items-start gap-3 rounded-lg border px-3 py-3 ${a.unlocked ? "border-mortar bg-masonry" : "border-dashed border-mortar/70"}`;
  return <li>{link && a.unlocked ? <Link to={link} className={`${cls} hover:border-trim-faint`}>{body}</Link> : <div className={cls}>{body}</div>}</li>;
}

/** The trophy cabinet's lifetime achievements: unlocked ones first, then the ones still to get. */
export function AchievementCabinet({ username }: { username: string }) {
  const { t } = useTranslation();
  const q = useAchievements(username);
  if (q.isLoading) return <Spinner />;
  if (q.error || !q.data) return <ErrorNotice error={q.error} onRetry={() => q.refetch()} />;
  const items = [...q.data.items].sort((x, y) => Number(y.unlocked) - Number(x.unlocked));
  return (
    <section>
      <SectionTitle action={<span className="tnum text-sm font-semibold text-trim-dim">{t("achievements.count", { unlocked: q.data.unlocked, total: q.data.total })}</span>}>
        {t("achievements.title")}
      </SectionTitle>
      <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((a) => (
          <Item key={a.code} a={a} />
        ))}
      </ul>
    </section>
  );
}
