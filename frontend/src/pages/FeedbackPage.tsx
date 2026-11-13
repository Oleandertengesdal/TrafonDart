import { Bug, Copy, Mail } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useCurrentUser } from "../auth/auth";
import { LanguageSwitch } from "../components/Layout";
import { Logo } from "../components/Logo";
import { Button } from "../components/ui";
import { Link } from "react-router";
import { usePageTitle } from "../lib/pageTitle";

/** Where bug reports go. Override with VITE_CONTACT_EMAIL at build time. */
export const CONTACT_EMAIL = (import.meta.env.VITE_CONTACT_EMAIL as string | undefined) || "adminsjef@uka.no";

/** "Found a bug?" – a small page that opens the user's mail app with a ready-made bug report. */
export function FeedbackPage() {
  const { t } = useTranslation();
  usePageTitle(t("feedback.title"));
  const { me } = useCurrentUser();
  const [copied, setCopied] = useState(false);

  const from = document.referrer && new URL(document.referrer).origin === window.location.origin ? document.referrer : window.location.origin;
  const body = t("feedback.bodyTemplate", { page: from, agent: navigator.userAgent });
  const mailto = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(t("feedback.subject") + (me ? ` (@${me.username})` : ""))}&body=${encodeURIComponent(body)}`;

  const content = (
    <div className="mx-auto max-w-2xl py-6">
      <Bug className="mb-4 size-12 text-trafon-bright" aria-hidden />
      <h1 className="font-display text-score-sm leading-[0.9]">{t("feedback.title")}</h1>
      <p className="mt-4 text-lg text-trim-dim">{t("feedback.lead")}</p>

      <div className="mt-8 flex flex-wrap items-center gap-3">
        <a
          href={mailto}
          className="inline-flex h-12 items-center justify-center gap-2 rounded-md bg-trafon px-6 text-base font-semibold text-trim hover:bg-trafon-bright"
        >
          <Mail className="size-5" aria-hidden />
          {t("feedback.emailUs")}
        </a>
        <Button
          variant="secondary"
          size="lg"
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(CONTACT_EMAIL);
              setCopied(true);
              setTimeout(() => setCopied(false), 2000);
            } catch {
              // clipboard blocked – the address is visible below anyway
            }
          }}
        >
          <Copy className="size-4" aria-hidden />
          {copied ? t("feedback.copied") : t("feedback.copy")}
        </Button>
      </div>
      <p className="mt-3 select-all font-mono text-trim">{CONTACT_EMAIL}</p>

      <section className="panel mt-10 p-5">
        <h2 className="mb-3 font-display text-xl">{t("feedback.whatToInclude")}</h2>
        <ul className="list-inside list-disc space-y-2 text-trim-dim marker:text-trafon">
          <li>{t("feedback.include1")}</li>
          <li>{t("feedback.include2")}</li>
          <li>{t("feedback.include3")}</li>
          <li>{t("feedback.include4")}</li>
        </ul>
      </section>
    </div>
  );

  // Logged-out visitors (e.g. from the login page) get a minimal shell instead of the app chrome.
  if (me) return content;
  return (
    <div className="min-h-dvh px-6 py-6">
      <div className="flex items-center justify-between">
        <Link to="/login">
          <Logo />
        </Link>
        <LanguageSwitch persist={false} />
      </div>
      {content}
    </div>
  );
}
