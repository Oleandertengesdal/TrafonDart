import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Facade } from "../components/Facade";
import { LinkButton } from "../components/ui";
import { usePageTitle } from "../lib/pageTitle";

export type Missing = "page" | "match" | "game" | "player" | "tournament";

/**
 * "Not found", for an unknown address and for a match, game, player or tournament that doesn't exist (or a link with a
 * malformed id). Trafon in the snow, a way home and a few places to go instead.
 */
export function NotFound({ what = "page" }: { what?: Missing }) {
  const { t } = useTranslation();
  const title = what === "page" ? t("notFound.title") : t(`notFound.${what}.title`);
  const lead = what === "page" ? t("notFound.lead") : t(`notFound.${what}.lead`);
  usePageTitle(title);
  const links = [
    { to: "/matches", label: t("nav.matches") },
    { to: "/players", label: t("nav.players") },
    { to: "/tournaments", label: t("nav.tournaments") },
  ];
  return (
    <section className="grid items-center gap-10 py-12 md:grid-cols-[1fr_minmax(0,22rem)]">
      <div className="flex flex-col items-start gap-4">
        <p className="numerals text-score-lg leading-none text-trafon-bright" aria-hidden>
          404
        </p>
        <h1 className="font-display text-5xl">{title}</h1>
        <p className="max-w-prose text-trim-dim">{lead}</p>
        <LinkButton to="/">{t("notFound.home")}</LinkButton>
        <nav aria-label={t("notFound.elsewhere")} className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
          <span className="text-trim-faint">{t("notFound.elsewhere")}</span>
          {links.map((l) => (
            <Link key={l.to} to={l.to} className="font-semibold text-trim underline decoration-trafon decoration-2 underline-offset-4 hover:decoration-trafon-bright">
              {l.label}
            </Link>
          ))}
        </nav>
      </div>
      <Facade scene className="hidden w-full rounded-xl md:block" />
    </section>
  );
}

export function NotFoundPage() {
  return <NotFound />;
}
