import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router";
import { ApiError, api } from "../api/client";
import type { Me } from "../api/types";
import { useAuthActions } from "../auth/auth";
import { LanguageSwitch } from "../components/Layout";
import { Logo } from "../components/Logo";
import { Button, ErrorNotice, Field, LinkButton, Spinner } from "../components/ui";
import { usePageTitle } from "../lib/pageTitle";

type Check = { valid: boolean; username: string | null; realName: string | null };

/** Landing page for a one-time password link an admin sent (e.g. on Messenger). */
export function ResetPasswordPage() {
  const { t } = useTranslation();
  usePageTitle(t("reset.title"));
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const navigate = useNavigate();
  const { afterLogin } = useAuthActions();
  const [check, setCheck] = useState<Check | null>(null);
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (!token) {
      setCheck({ valid: false, username: null, realName: null });
      return;
    }
    // The token travels in the body, never in an API URL, so it stays out of server logs.
    api
      .post<Check>("/api/auth/password-reset/check", { token })
      .then(setCheck)
      .catch(() => setCheck({ valid: false, username: null, realName: null }));
  }, [token]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const me = await api.post<Me>("/api/auth/password-reset", { token, newPassword: password });
      afterLogin(me);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  const fieldErr = error instanceof ApiError && error.fieldError("newPassword") ? t(`errors.fields.${error.fieldError("newPassword")}`) : undefined;

  return (
    <div className="flex min-h-dvh flex-col px-6 py-6">
      <div className="flex items-center justify-between">
        <Logo />
        <LanguageSwitch persist={false} />
      </div>
      <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10">
        {check === null ? (
          <Spinner label={t("reset.checking")} />
        ) : !check.valid ? (
          <>
            <h1 className="font-display text-5xl leading-none">{t("reset.invalidTitle")}</h1>
            <p className="mb-8 mt-3 text-trim-dim">{t("reset.invalidLead")}</p>
            <LinkButton to="/login" className="self-start">
              {t("reset.toLogin")}
            </LinkButton>
          </>
        ) : (
          <>
            <h1 className="font-display text-5xl leading-none">{t("reset.title")}</h1>
            <p className="mb-8 mt-3 text-trim-dim">{t("reset.lead", { name: check.realName, username: check.username })}</p>
            <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
              {/* Lets password managers save the new password against the right username. */}
              <input type="text" name="username" autoComplete="username" value={check.username ?? ""} readOnly hidden />
              <Field
                label={t("reset.newPassword")}
                hint={t("auth.passwordHint")}
                type="password"
                autoComplete="new-password"
                autoFocus
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                error={fieldErr}
              />
              {error !== null && !fieldErr && <ErrorNotice error={error} />}
              <Button type="submit" size="lg" busy={busy} disabled={password.length < 10}>
                {busy ? t("reset.saving") : t("reset.save")}
              </Button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
