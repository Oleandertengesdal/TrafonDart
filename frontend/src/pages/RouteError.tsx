import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { isRouteErrorResponse, useRouteError } from "react-router";
import { reportError } from "../lib/reporting";
import { NotFoundPage } from "./NotFoundPage";

/** Shown instead of a blank screen when a page crashes; the crash is reported (see docs/OPERATIONS.md). */
export function RouteError() {
  const error = useRouteError();
  const { t } = useTranslation();
  const notFound = isRouteErrorResponse(error) && error.status === 404;
  useEffect(() => {
    if (!notFound) reportError("boundary", error);
  }, [error, notFound]);
  if (notFound) {
    return (
      <div className="mx-auto max-w-3xl px-4">
        <NotFoundPage />
      </div>
    );
  }
  return (
    <div className="mx-auto flex max-w-xl flex-col items-start gap-4 px-4 py-16">
      <h1 className="font-display text-5xl font-extrabold uppercase">{t("crash.title")}</h1>
      <p className="text-trim-dim">{t("crash.lead")}</p>
      <div className="flex gap-2">
        <button onClick={() => window.location.reload()} className="h-10 rounded-md bg-trafon px-4 text-sm font-semibold text-trim">
          {t("crash.reload")}
        </button>
        <a href="/" className="flex h-10 items-center rounded-md border border-mortar px-4 text-sm font-semibold text-trim">
          {t("notFound.home")}
        </a>
      </div>
    </div>
  );
}
