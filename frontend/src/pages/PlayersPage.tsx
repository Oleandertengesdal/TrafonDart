import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { useLeaderboard } from "../api/queries";
import { RatingBadge } from "../components/PlayerName";
import { ErrorNotice, PageHeader, Spinner } from "../components/ui";
import { num, pct } from "../lib/format";

export function PlayersPage() {
  const { t } = useTranslation();
  const board = useLeaderboard();
  return (
    <>
      <PageHeader title={t("players.title")} lead={t("players.lead")} />
      {board.isLoading ? (
        <Spinner />
      ) : board.error ? (
        <ErrorNotice error={board.error} onRetry={() => board.refetch()} />
      ) : (
        <div className="panel overflow-x-auto">
          <table className="w-full min-w-[36rem]">
            <thead className="text-left text-xs uppercase tracking-wider text-trim-faint">
              <tr>
                <th className="w-12 px-4 py-3 font-semibold">#</th>
                <th className="px-2 py-3 font-semibold">{t("players.player")}</th>
                <th className="px-2 py-3 text-right font-semibold">{t("stats.rating")}</th>
                <th className="px-2 py-3 text-right font-semibold">{t("players.played")}</th>
                <th className="px-2 py-3 text-right font-semibold">{t("players.winRate")}</th>
                <th className="px-2 py-3 text-right font-semibold">{t("players.avg")}</th>
                <th className="px-2 py-3 text-right font-semibold">{t("players.s180")}</th>
                <th className="px-4 py-3 text-right font-semibold">{t("players.hc")}</th>
              </tr>
            </thead>
            <tbody>
              {(board.data ?? []).map((r) => (
                <tr key={r.player.id} className="border-t border-mortar/60 hover:bg-masonry-2">
                  <td className={`numerals px-4 py-3 text-2xl font-bold ${r.rank === 1 ? "text-brass" : "text-trim-faint"}`}>{r.rank}</td>
                  <td className="px-2 py-3">
                    <Link to={`/players/${r.player.username}`} className="flex flex-col leading-tight hover:underline">
                      <span className="font-semibold">{r.player.realName}</span>
                      <span className="text-xs text-trim-faint">@{r.player.username}</span>
                    </Link>
                  </td>
                  <td className="px-2 text-right">
                    <RatingBadge player={r.player} />
                  </td>
                  <td className="tnum px-2 text-right text-trim-dim">{r.played}</td>
                  <td className="tnum px-2 text-right text-trim-dim">{r.played ? pct(r.winRate, 0) : "–"}</td>
                  <td className="tnum px-2 text-right">{r.played ? num(r.average, 1) : "–"}</td>
                  <td className="tnum px-2 text-right text-trim-dim">{r.count180}</td>
                  <td className={`tnum px-4 text-right ${r.highestCheckout >= 100 ? "font-bold text-brass" : "text-trim-dim"}`}>{r.highestCheckout || "–"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
