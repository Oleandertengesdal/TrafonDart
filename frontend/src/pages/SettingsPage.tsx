import { useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { ApiError, api } from "../api/client";
import { keys } from "../api/queries";
import type { Me } from "../api/types";
import { useAuthActions, useCurrentUser } from "../auth/auth";
import { LanguageSwitch } from "../components/Layout";
import { Button, ConfirmButton, ErrorNotice, Field, PageHeader, Toggle } from "../components/ui";
import { soundPrefs } from "../lib/sound";

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="panel flex flex-col gap-4 p-5">
      <h2 className="font-display text-2xl font-bold uppercase">{title}</h2>
      {children}
    </section>
  );
}

export function SettingsPage() {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { logout } = useAuthActions();
  const [sound, setSound] = useState(soundPrefs.sound);
  const [caller, setCaller] = useState(soundPrefs.caller);
  const [realName, setRealName] = useState(me?.realName ?? "");
  const [savedName, setSavedName] = useState(false);
  const [nameError, setNameError] = useState<unknown>(null);
  const [pw, setPw] = useState({ current: "", next: "" });
  const [pwBusy, setPwBusy] = useState(false);
  const [pwError, setPwError] = useState<unknown>(null);
  const [pwDone, setPwDone] = useState(false);

  const saveName = async (e: FormEvent) => {
    e.preventDefault();
    setNameError(null);
    try {
      const updated = await api.patch<Me>("/api/users/me", { realName: realName.trim() });
      qc.setQueryData(keys.me, updated);
      setSavedName(true);
      setTimeout(() => setSavedName(false), 2000);
    } catch (err) {
      setNameError(err);
    }
  };

  const changePassword = async (e: FormEvent) => {
    e.preventDefault();
    setPwBusy(true);
    setPwError(null);
    setPwDone(false);
    try {
      const updated = await api.post<Me>("/api/auth/change-password", { currentPassword: pw.current, newPassword: pw.next });
      qc.setQueryData(keys.me, updated);
      setPw({ current: "", next: "" });
      setPwDone(true);
    } catch (err) {
      setPwError(err);
    } finally {
      setPwBusy(false);
    }
  };

  const fieldErr = (err: unknown, field: string) => {
    if (!(err instanceof ApiError)) return undefined;
    const code = err.fieldError(field);
    return code ? t(`errors.fields.${code}`) : undefined;
  };

  return (
    <>
      <PageHeader title={t("settings.title")} />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title={t("settings.language")}>
          <div className="flex items-center justify-between gap-4">
            <p className="text-sm text-trim-dim">{t("settings.languageHint")}</p>
            <LanguageSwitch />
          </div>
          <div className="border-t border-mortar pt-4">
            <Toggle
              label={t("settings.sound")}
              hint={t("settings.soundHint")}
              checked={sound}
              onChange={(v) => {
                soundPrefs.sound = v;
                setSound(v);
              }}
            />
          </div>
          <Toggle
            label={t("settings.caller")}
            hint={t("settings.callerHint")}
            checked={caller}
            onChange={(v) => {
              soundPrefs.caller = v;
              setCaller(v);
            }}
          />
        </Card>

        <Card title={t("settings.profile")}>
          <form onSubmit={saveName} className="flex flex-col gap-3">
            <Field label={t("auth.realName")} value={realName} maxLength={60} onChange={(e) => setRealName(e.target.value)} error={fieldErr(nameError, "realName")} />
            <div className="flex items-center gap-3">
              <Button type="submit" variant="secondary" disabled={!realName.trim() || realName.trim() === me?.realName}>
                {t("common.save")}
              </Button>
              {savedName && <span className="text-sm text-board-bright">{t("settings.saved")}</span>}
            </div>
          </form>
        </Card>

        <Card title={t("settings.password")}>
          <form onSubmit={changePassword} className="flex flex-col gap-3">
            <Field label={t("settings.currentPassword")} type="password" autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} />
            <Field
              label={t("settings.newPassword")}
              hint={t("auth.passwordHint")}
              type="password"
              autoComplete="new-password"
              value={pw.next}
              onChange={(e) => setPw({ ...pw, next: e.target.value })}
              error={fieldErr(pwError, "newPassword")}
            />
            {pwError !== null && <ErrorNotice error={pwError} />}
            {pwDone && <p className="text-sm text-board-bright">{t("settings.passwordChanged")}</p>}
            <Button type="submit" busy={pwBusy} disabled={!pw.current || pw.next.length < 10} className="self-start">
              {t("settings.changePassword")}
            </Button>
          </form>
        </Card>

        <Card title={t("settings.sessions")}>
          <p className="text-sm text-trim-dim">{t("settings.logoutAllHint")}</p>
          <div>
            <ConfirmButton
              label={t("settings.logoutAll")}
              message={t("settings.logoutAllHint")}
              onConfirm={async () => {
                await api.post("/api/auth/logout-all");
                await logout();
                navigate("/login");
              }}
            />
          </div>
        </Card>
      </div>
    </>
  );
}
