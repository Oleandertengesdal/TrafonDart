import { useTranslation } from "react-i18next";
import { LinkButton } from "../components/ui";

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-start gap-4 py-16">
      <p className="numerals text-[8rem] font-extrabold leading-none text-trafon">404</p>
      <h1 className="font-display text-5xl font-extrabold uppercase">{t("notFound.title")}</h1>
      <p className="text-trim-dim">{t("notFound.lead")}</p>
      <LinkButton to="/">{t("notFound.home")}</LinkButton>
    </div>
  );
}
