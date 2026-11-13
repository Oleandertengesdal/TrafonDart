import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { isRouteErrorResponse, useRouteError } from "react-router";
import { RefreshCw, TriangleAlert } from "lucide-react";
import { Button, LinkButton } from "../components/ui";
import { reportError } from "../lib/reporting";
import { usePageTitle } from "../lib/pageTitle";
import { NotFound } from "./NotFoundPage";

/** After a deploy, an open tab can ask for a code chunk that no longer exists. A reload fixes it; it isn't a bug. */
function isStaleBuild(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? "");
  return /dynamically imported module|Importing a module script failed|error loading dynamically|ChunkLoadError/i.test(message);
}

/** Shown instead of a blank screen when a page crashes; the crash is reported (see docs/OPERATIONS.md). */
export function RouteError() {
  const error = useRouteError();
  const { t } = useTranslation();
  const notFound = isRouteErrorResponse(error) && error.status === 404;
  const stale = !notFound && isStaleBuild(error);
  usePageTitle(notFound ? null : stale ? t("crash.updateTitle") : t("crash.title"));
  useEffect(() => {
    if (!notFound && !stale) reportError("boundary", error);
  }, [error, notFound, stale]);
  if (notFound) {
    return (
      <div className="mx-auto max-w-5xl px-4">
        <NotFound />
      </div>
    );
  }
  const Icon = stale ? RefreshCw : TriangleAlert;
  return (
    <div className="mx-auto flex max-w-xl flex-col items-start gap-4 px-4 py-16">
      <span className="flex size-12 items-center justify-center rounded-full bg-masonry text-trim-dim">
        <Icon className="size-6" aria-hidden />
      </span>
      <h1 className="font-display text-5xl">{stale ? t("crash.updateTitle") : t("crash.title")}</h1>
      <p className="text-trim-dim">{stale ? t("crash.updateLead") : t("crash.lead")}</p>
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => window.location.reload()}>{t("crash.reload")}</Button>
        <LinkButton to="/" variant="secondary" reloadDocument>
          {t("notFound.home")}
        </LinkButton>
        {!stale && (
          <LinkButton to="/feedback" variant="ghost" reloadDocument>
            {t("errors.action.report")}
          </LinkButton>
        )}
      </div>
    </div>
  );
}
