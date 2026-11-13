import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Facade } from "./Facade";
import { Spinner } from "./ui";

/**
 * Shown while we wait to learn who is logged in. Usually that takes a moment; but a free-tier server that has been
 * asleep needs up to a minute to start, and after a few seconds this explains that instead of a bare spinner.
 */
export function WakingScreen() {
  const { t } = useTranslation();
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    const id = setTimeout(() => setSlow(true), 2500);
    return () => clearTimeout(id);
  }, []);
  if (!slow) return <Spinner />;
  return (
    <div role="status" className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-6 px-6 text-center">
      <Facade className="h-48 w-auto" />
      <h1 className="font-display text-4xl leading-none">{t("waking.title")}</h1>
      <p className="text-trim-dim">{t("waking.lead")}</p>
      <div className="h-1.5 w-48 overflow-hidden rounded-full bg-masonry-2">
        <div className="h-full w-1/3 animate-[waking_1.6s_ease-in-out_infinite] rounded-full bg-trafon" />
      </div>
    </div>
  );
}
