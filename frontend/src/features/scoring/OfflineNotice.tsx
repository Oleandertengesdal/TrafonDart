import { CloudOff } from "lucide-react";
import { useTranslation } from "react-i18next";

/** Shown while darts wait for the connection: they're saved on this device and sent when it's back. */
export function OfflineNotice({ offline, count }: { offline: boolean; count: number }) {
  const { t } = useTranslation();
  if (!offline) return null;
  return (
    <div role="status" className="flex items-center gap-3 rounded-md border border-brass/60 bg-brass/10 px-3 py-2 text-sm">
      <CloudOff className="size-5 shrink-0 text-brass" aria-hidden />
      <span>
        <strong className="font-semibold">{t("offline.title")}</strong> {t("offline.waiting", { count })}
      </span>
    </div>
  );
}
