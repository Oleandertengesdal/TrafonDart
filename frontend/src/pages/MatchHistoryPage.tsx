import { useTranslation } from "react-i18next";
import { useRecentMatches } from "../api/queries";
import { MatchRow } from "../components/MatchRow";
import { EmptyState, LinkButton, ErrorNotice, PageHeader, Spinner } from "../components/ui";

export function MatchHistoryPage() {
  const { t } = useTranslation();
  const q = useRecentMatches(undefined, 100);
  return (
    <>
      <PageHeader
        title={t("match.historyTitle")}
        lead={t("match.historyLead")}
        action={<LinkButton to="/play">{t("home.startMatch")}</LinkButton>}
      />
      {q.isLoading ? (
        <Spinner />
      ) : q.error ? (
        <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
      ) : (q.data ?? []).length === 0 ? (
        <EmptyState title={t("match.noMatches")} />
      ) : (
        <ul className="panel divide-y divide-mortar/60 p-1">{q.data!.map((m) => <MatchRow key={m.id} match={m} />)}</ul>
      )}
    </>
  );
}
