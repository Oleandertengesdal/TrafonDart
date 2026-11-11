import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from "react-router";
import { CheckCircle2 } from "lucide-react";
import { ApiError, api } from "../api/client";
import type { Me } from "../api/types";
import { useAuthActions, useCurrentUser } from "../auth/auth";
import { Facade } from "../components/Facade";
import { LanguageSwitch } from "../components/Layout";
import { Logo } from "../components/Logo";
import { Button, ErrorNotice, Field } from "../components/ui";
import { currentLanguage } from "../i18n";

function AuthShell({ title, lead, children }: { title: string; lead: string; children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      <aside className="relative hidden overflow-hidden border-r border-mortar bg-masonry lg:flex lg:flex-col lg:justify-between lg:p-12">
        <Logo />
        <Facade className="mx-auto w-full max-w-sm drop-shadow-[0_30px_60px_rgba(200,35,31,0.25)]" />
        <p className="max-w-sm font-display text-3xl font-bold uppercase leading-tight tracking-wide text-trim">{t("app.tagline")}</p>
      </aside>
      <div className="flex flex-col px-6 py-6 sm:px-12">
        <div className="flex items-center justify-between lg:justify-end">
          <span className="lg:hidden">
            <Logo />
          </span>
          <LanguageSwitch persist={false} />
        </div>
        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10">
          <h1 className="font-display text-5xl font-extrabold uppercase leading-none">{title}</h1>
          <p className="mb-8 mt-3 text-trim-dim">{lead}</p>
          {children}
        </div>
      </div>
    </div>
  );
}

export function LoginPage() {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const { afterLogin } = useAuthActions();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  if (me) return <Navigate to={from} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user = await api.post<Me>("/api/auth/login", { username: username.trim(), password });
      afterLogin(user);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  return (
    <AuthShell title={t("auth.loginTitle")} lead={t("auth.loginLead")}>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <Field label={t("auth.username")} autoComplete="username" autoCapitalize="none" value={username} onChange={(e) => setUsername(e.target.value)} required />
        <Field label={t("auth.password")} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error !== null && <ErrorNotice error={error} />}
        <Button type="submit" size="lg" busy={busy} disabled={!username || !password}>
          {busy ? t("auth.loggingIn") : t("auth.login")}
        </Button>
      </form>
      <p className="mt-4 text-sm text-trim-faint">{t("forgot.hint")}</p>
      <p className="mt-8 text-sm text-trim-dim">
        {t("auth.noAccount")}{" "}
        <Link to="/register" className="font-semibold text-trim underline decoration-trafon decoration-2 underline-offset-4">
          {t("auth.createAccount")}
        </Link>
      </p>
      <p className="mt-3 text-sm">
        <Link to="/feedback" className="text-trim-faint hover:text-trim">
          {t("feedback.link")}
        </Link>
      </p>
    </AuthShell>
  );
}

export function RegisterPage() {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const { afterLogin } = useAuthActions();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [form, setForm] = useState({ inviteCode: params.get("code") ?? "", username: "", realName: "", password: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [invite, setInvite] = useState<{ state: "idle" | "checking" | "ok" | "bad"; reason?: string }>({ state: "idle" });

  // Check the invite code as soon as it looks complete (12 characters, dashes optional).
  useEffect(() => {
    const normalised = form.inviteCode.replace(/[\s-]/g, "");
    if (normalised.length !== 12) {
      setInvite({ state: "idle" });
      return;
    }
    setInvite({ state: "checking" });
    const handle = setTimeout(async () => {
      try {
        const res = await api.get<{ valid: boolean; reason: string | null }>(`/api/auth/invites/${encodeURIComponent(normalised)}`);
        setInvite(res.valid ? { state: "ok" } : { state: "bad", reason: res.reason ?? "INVITE_INVALID" });
      } catch (err) {
        setInvite({ state: "bad", reason: err instanceof ApiError ? err.code : "UNKNOWN" });
      }
    }, 350);
    return () => clearTimeout(handle);
  }, [form.inviteCode]);

  if (me) return <Navigate to="/" replace />;

  const fieldError = (field: string) => {
    if (!(error instanceof ApiError)) return undefined;
    const code = error.fieldError(field);
    return code ? t(`errors.fields.${code}`) : undefined;
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user = await api.post<Me>("/api/auth/register", { ...form, username: form.username.trim(), realName: form.realName.trim(), preferredLocale: currentLanguage() });
      afterLogin(user);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value });

  return (
    <AuthShell title={t("auth.registerTitle")} lead={t("auth.registerLead")}>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <div>
          <Field
            label={t("auth.inviteCode")}
            value={form.inviteCode}
            onChange={set("inviteCode")}
            autoCapitalize="characters"
            autoComplete="off"
            placeholder="XXXX-XXXX-XXXX"
            className="[&_input]:font-mono [&_input]:uppercase [&_input]:tracking-widest"
            error={invite.state === "bad" ? t(`errors.${invite.reason}`) : fieldError("inviteCode")}
          />
          {invite.state === "ok" && (
            <p className="mt-1.5 flex items-center gap-1.5 text-sm text-board-bright">
              <CheckCircle2 className="size-4" aria-hidden /> {t("auth.inviteValid")}
            </p>
          )}
          {invite.state === "checking" && <p className="mt-1.5 text-sm text-trim-faint">{t("auth.inviteChecking")}</p>}
        </div>
        <Field label={t("auth.realName")} hint={t("auth.realNameHint")} autoComplete="name" value={form.realName} onChange={set("realName")} error={fieldError("realName")} />
        <Field label={t("auth.username")} hint={t("auth.usernameHint")} autoComplete="username" autoCapitalize="none" value={form.username} onChange={set("username")} error={fieldError("username")} />
        <Field label={t("auth.password")} hint={t("auth.passwordHint")} type="password" autoComplete="new-password" value={form.password} onChange={set("password")} error={fieldError("password")} />
        {error !== null && <ErrorNotice error={error} />}
        <Button type="submit" size="lg" busy={busy} disabled={invite.state === "bad"}>
          {busy ? t("auth.registering") : t("auth.register")}
        </Button>
      </form>
      <p className="mt-8 text-sm text-trim-dim">
        {t("auth.haveAccount")}{" "}
        <Link to="/login" className="font-semibold text-trim underline decoration-trafon decoration-2 underline-offset-4">
          {t("auth.login")}
        </Link>
      </p>
    </AuthShell>
  );
}
