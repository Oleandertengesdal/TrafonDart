import { intlLocale } from "../i18n";
import i18n from "../i18n";

/** Locale-aware number: 3,5 in Norwegian, 3.5 in English. */
export function num(value: number, decimals = 0): string {
  return new Intl.NumberFormat(intlLocale(), {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

/** 54.3 → "54,3 %" (nb) / "54.3%" (en). Input is already a percentage. */
export function pct(value: number, decimals = 1): string {
  return new Intl.NumberFormat(intlLocale(), {
    style: "percent",
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value / 100);
}

export function date(iso: string | null | undefined): string {
  if (!iso) return "–";
  return new Intl.DateTimeFormat(intlLocale(), { day: "numeric", month: "short", year: "numeric" }).format(new Date(iso));
}

export function shortDate(iso: string | null | undefined): string {
  if (!iso) return "–";
  return new Intl.DateTimeFormat(intlLocale(), { day: "numeric", month: "short" }).format(new Date(iso));
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return "–";
  return new Intl.DateTimeFormat(intlLocale(), {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso));
}

/** "5 minutes ago" / "for 5 minutter siden". */
export function relative(iso: string | null | undefined): string {
  if (!iso) return "–";
  const diffSec = (new Date(iso).getTime() - Date.now()) / 1000;
  const rtf = new Intl.RelativeTimeFormat(intlLocale(), { numeric: "auto" });
  const abs = Math.abs(diffSec);
  if (abs < 60) return rtf.format(Math.round(diffSec), "second");
  if (abs < 3600) return rtf.format(Math.round(diffSec / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(diffSec / 3600), "hour");
  if (abs < 86400 * 30) return rtf.format(Math.round(diffSec / 86400), "day");
  return date(iso);
}

/** Board notation for display. S20 → 20, BULL → Bull, MISS → Miss/Bom; doubles and trebles stay D16 / T20. */
export function dartLabel(label: string): string {
  if (label === "MISS") return i18n.t("match.miss");
  if (label === "BULL") return i18n.t("match.bull");
  if (label.startsWith("S")) return label.substring(1);
  return label;
}

/** "501 · Double out · First to 3" style rule summary, localised. */
export function rulesSummary(cfg: { startScore: number; inRule: string; outRule: string; legsToWin: number }): string {
  const parts = [String(cfg.startScore)];
  if (cfg.inRule !== "STRAIGHT") parts.push(i18n.t(`match.in.${cfg.inRule}`));
  parts.push(i18n.t(`match.out.${cfg.outRule}`));
  parts.push(`${i18n.t("match.legsToWin")} ${i18n.t("match.legsCount", { count: cfg.legsToWin })}`);
  return parts.join(", ");
}

/** "2026-11" → "november 2026" (nb) / "November 2026" (en). */
export function monthName(period: string | null | undefined): string {
  if (!period) return "–";
  // Player of the Year is awarded for a whole year: "2025".
  if (/^\d{4}$/.test(period)) return period;
  if (!/^\d{4}-\d{2}$/.test(period)) return "–";
  const [y, m] = period.split("-").map(Number);
  return new Intl.DateTimeFormat(intlLocale(), { month: "long", year: "numeric", timeZone: "UTC" }).format(new Date(Date.UTC(y, m - 1, 15)));
}

/** Signed number: +12 / −4 (true minus sign). */
export function signed(value: number, decimals = 0): string {
  const factor = 10 ** decimals;
  const rounded = Math.round(value * factor) / factor; // −0.04 shown with one decimal is 0.0, not −0.0
  const s = num(Math.abs(rounded), decimals);
  return rounded > 0 ? `+${s}` : rounded < 0 ? `−${s}` : s;
}
