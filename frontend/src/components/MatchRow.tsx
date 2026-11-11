import { useTranslation } from "react-i18next";
import { MessageCircle, PartyPopper } from "lucide-react";
import { Link } from "react-router";
import type { MatchSummary } from "../api/types";
import { num, relative } from "../lib/format";

/** One finished (or live) match in a list. The winner's side is bright, the loser's dimmed. */
export function MatchRow({ match, perspective }: { match: MatchSummary; perspective?: number }) {
  const { t } = useTranslation();
  const voided = match.status === "VOIDED";
  const live = match.status === "IN_PROGRESS";
  const won = perspective !== undefined && match.winnerIndex !== null ? match.players[match.winnerIndex].id === perspective : null;

  return (
    <li>
      <Link
        to={`/matches/${match.id}`}
        className={`grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 rounded-md px-3 py-3 transition-colors hover:bg-masonry-2 ${voided ? "opacity-50" : ""}`}
      >
        {[0, 1].map((i) => {
          const isWinner = match.winnerIndex === i;
          const side = (
            <span key={i} className={`flex min-w-0 flex-col ${i === 1 ? "items-end text-right" : ""}`}>
              <span className={`block max-w-full truncate font-semibold ${isWinner || live ? "text-trim" : "text-trim-dim"}`}>{match.players[i].realName}</span>
              <span className="text-xs text-trim-faint">
                {t("match.average")} {num(match.averages[i], 1)}
              </span>
            </span>
          );
          if (i === 0) return side;
          return (
            <span key="score-and-p2" className="contents">
              <span className="flex flex-col items-center">
                <span className="numerals text-2xl font-bold leading-none">
                  <span className={match.winnerIndex === 0 ? "text-trim" : "text-trim-dim"}>{match.legsWon[0]}</span>
                  <span className="mx-1 text-trim-faint">–</span>
                  <span className={match.winnerIndex === 1 ? "text-trim" : "text-trim-dim"}>{match.legsWon[1]}</span>
                </span>
                {(match.kudos > 0 || match.comments > 0) && (
                  <span className="mt-1 flex items-center gap-2 text-[11px] text-trim-faint">
                    {match.kudos > 0 && (
                      <span className="flex items-center gap-0.5 text-brass" title={t("social.kudosCount", { count: match.kudos })}>
                        <PartyPopper className="size-3" aria-hidden />
                        {match.kudos}
                      </span>
                    )}
                    {match.comments > 0 && (
                      <span className="flex items-center gap-0.5" title={t("social.commentsCount", { count: match.comments })}>
                        <MessageCircle className="size-3" aria-hidden />
                        {match.comments}
                      </span>
                    )}
                  </span>
                )}
                <span className="mt-1 text-[11px] uppercase tracking-wider text-trim-faint">
                  {live ? (
                    <span className="font-bold text-trafon-bright">{t("common.live")}</span>
                  ) : voided ? (
                    t("match.status.VOIDED")
                  ) : won === null ? (
                    relative(match.completedAt)
                  ) : (
                    <span className={won ? "font-bold text-board-bright" : "text-trim-faint"}>{won ? t("stats.win") : t("stats.loss")}</span>
                  )}
                </span>
              </span>
              {side}
            </span>
          );
        })}
      </Link>
    </li>
  );
}
