import { Bot, Check } from "lucide-react";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation } from "react-router";
import { useBotRecord } from "../../api/queries";
import { LinkButton, SectionTitle } from "../../components/ui";
import { num, pct, relative } from "../../lib/format";

/**
 * A player's record against Trafo, on their profile. Kept visibly apart from the match stats above it: these are
 * practice matches and never count towards rating, the leaderboard or the match record.
 */
export function BotRecordSection({ username, isMe }: { username: string; isMe: boolean }) {
  const { t } = useTranslation();
  const q = useBotRecord(username);
  const r = q.data;
  const { hash } = useLocation();
  const anchor = useRef<HTMLElement>(null);
  // A link to "#trafo" arrives before the record has loaded; scroll once it's there.
  useEffect(() => {
    if (hash === "#trafo" && r) anchor.current?.scrollIntoView({ block: "start" });
  }, [hash, r]);
  if (!r) return null;
  if (r.played === 0) {
    if (!isMe) return null;
    return (
      <section id="trafo" ref={anchor} className="scroll-mt-24">
        <SectionTitle>{t("bot.record.title")}</SectionTitle>
        <div className="panel flex flex-wrap items-center gap-4 p-5">
          <Bot className="size-6 text-trim-dim" aria-hidden />
          <p className="flex-1 text-trim-dim">{t("bot.record.emptyMine")}</p>
          <LinkButton to="/bot" variant="secondary">
            {t("bot.record.challenge")}
          </LinkButton>
        </div>
      </section>
    );
  }

  const tiles: { label: string; value: string; sub?: string; accent?: boolean }[] = [
    { label: t("bot.record.won"), value: t("stats.record", { won: r.won, lost: r.lost }), sub: `${t("bot.record.winRate")} ${pct(r.winRate)}` },
    { label: t("bot.record.streak"), value: num(r.currentStreak), sub: `${t("bot.record.bestStreak")} ${num(r.bestStreak)}`, accent: r.currentStreak >= 3 },
    { label: t("bot.record.hardest"), value: r.hardestBeaten ? t(`bot.levels.${r.hardestBeaten}.name`) : t("bot.record.none") },
    { label: t("bot.record.average"), value: num(r.throwing.average, 2), sub: `${t("bot.record.trafoAverage")} ${num(r.throwing.trafoAverage, 1)}` },
    {
      label: t("bot.record.checkout"),
      value: pct(r.throwing.checkoutPercentage),
      sub: t("stats.checkoutDetail", { hits: r.throwing.checkoutsHit, attempts: r.throwing.checkoutAttempts }),
    },
    { label: t("bot.record.oneEighties"), value: num(r.throwing.count180), accent: r.throwing.count180 > 0 },
    { label: t("bot.record.highestCheckout"), value: r.throwing.highestCheckout ? num(r.throwing.highestCheckout) : "–" },
    { label: t("bot.record.first9"), value: num(r.throwing.first9Average, 2) },
  ];

  return (
    <section id="trafo" ref={anchor} className="scroll-mt-24">
      <SectionTitle action={isMe ? <LinkButton to="/bot" variant="secondary" size="sm">{t("bot.record.challenge")}</LinkButton> : undefined}>
        {t("bot.record.title")}
      </SectionTitle>
      <p className="-mt-2 mb-4 max-w-prose text-sm text-trim-faint">{t("bot.record.lead")}</p>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {tiles.map((tile) => (
          <div key={tile.label} className={`panel flex flex-col gap-1 p-4 ${tile.accent ? "border-brass/70" : ""}`}>
            <span className="text-xs font-semibold text-trim-faint">{tile.label}</span>
            <span className={`numerals text-4xl font-extrabold leading-none ${tile.accent ? "text-brass" : "text-trim"}`}>{tile.value}</span>
            {tile.sub && <span className="text-xs text-trim-dim">{tile.sub}</span>}
          </div>
        ))}
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
        <div>
          <h3 className="mb-2 font-display text-xl">{t("bot.record.levels")}</h3>
          <ul className="panel divide-y divide-mortar/60 p-1">
            {r.levels.map((l) => (
              <li key={l.level} className="flex items-center gap-3 px-3 py-3 text-sm">
                <span className="flex-1">
                  <span className="font-semibold">{t(`bot.levels.${l.level}.name`)}</span>{" "}
                  <span className="text-trim-faint">{t("bot.average", { n: l.typicalAverage }).toLowerCase()}</span>
                </span>
                {l.won > 0 && <Check className="size-4 text-board-bright" aria-label={t("bot.beaten")} />}
                <span className="tnum w-16 text-right text-trim-dim">{l.played > 0 ? `${l.won}–${l.played - l.won}` : "–"}</span>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h3 className="mb-2 font-display text-xl">{t("bot.record.recent")}</h3>
          <ul className="panel divide-y divide-mortar/60 p-1">
            {r.recent.map((m) => (
              <li key={m.id}>
                {isMe ? (
                  <Link to={`/bot/${m.id}`} className="flex items-center gap-3 rounded px-3 py-3 hover:bg-masonry-2">
                    <RecentRow m={m} />
                  </Link>
                ) : (
                  <div className="flex items-center gap-3 px-3 py-3">
                    <RecentRow m={m} />
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}

function RecentRow({ m }: { m: NonNullable<ReturnType<typeof useBotRecord>["data"]>["recent"][number] }) {
  const { t } = useTranslation();
  return (
    <>
      <span className={`w-6 text-center text-xs font-bold ${m.won ? "text-board-bright" : "text-trim-faint"}`}>{m.won ? t("stats.win") : t("stats.loss")}</span>
      <span className="flex-1 truncate text-sm">
        <span className="font-semibold">{t(`bot.levels.${m.level}.name`)}</span>{" "}
        <span className="text-trim-dim">
          {m.status === "FORFEITED" ? t("bot.forfeited").toLowerCase() : `${m.legsWon}–${m.legsLost}`}, {m.startScore}
        </span>
      </span>
      <span className="numerals text-lg">{num(m.average, 1)}</span>
      <span className="shrink-0 whitespace-nowrap text-right text-xs text-trim-faint">{relative(m.completedAt)}</span>
    </>
  );
}
