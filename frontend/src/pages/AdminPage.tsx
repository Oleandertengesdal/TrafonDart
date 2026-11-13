import { useQueryClient } from "@tanstack/react-query";
import { Copy, KeyRound, Link2, LockKeyhole } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { api, apiPath } from "../api/client";
import { keys, useAdminUsers, useDisputes, useInvites } from "../api/queries";
import type { InviteView } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { MatchRow } from "../components/MatchRow";
import { Button, ConfirmButton, EmptyState, ErrorNotice, Field, Modal, PageHeader, SectionTitle, Segmented, Spinner } from "../components/ui";
import { dateTime, relative } from "../lib/format";
import { usePageTitle } from "../lib/pageTitle";

type Tab = "invites" | "users" | "disputes";

export function AdminPage() {
  const { t } = useTranslation();
  usePageTitle(t("admin.title"));
  const [tab, setTab] = useState<Tab>("invites");
  const disputes = useDisputes();
  const open = (disputes.data ?? []).filter((d) => d.status === "OPEN").length;
  return (
    <>
      <PageHeader title={t("admin.title")} />
      <div role="tablist" className="mb-8 flex gap-1 border-b border-mortar">
        {(["invites", "users", "disputes"] as Tab[]).map((x) => (
          <button
            key={x}
            role="tab"
            aria-selected={tab === x}
            onClick={() => setTab(x)}
            className={`-mb-px flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-semibold ${tab === x ? "border-trafon text-trim" : "border-transparent text-trim-dim hover:text-trim"}`}
          >
            {t(`admin.${x}`)}
            {x === "disputes" && open > 0 && <span className="rounded-full bg-trafon px-2 text-xs text-trim">{open}</span>}
          </button>
        ))}
      </div>
      {tab === "invites" && <Invites />}
      {tab === "users" && <Users />}
      {tab === "disputes" && <Disputes />}
    </>
  );
}

function CopyButton({ text, label, icon = "copy" }: { text: string; label: string; icon?: "copy" | "link" }) {
  const { t } = useTranslation();
  const [done, setDone] = useState(false);
  return (
    <Button
      variant="secondary"
      size="sm"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setDone(true);
          setTimeout(() => setDone(false), 2000);
        } catch {
          // clipboard blocked
        }
      }}
    >
      {icon === "link" ? <Link2 className="size-4" aria-hidden /> : <Copy className="size-4" aria-hidden />}
      {done ? t("common.copied") : label}
    </Button>
  );
}

function Invites() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const q = useInvites();
  const [maxUses, setMaxUses] = useState("5");
  const [validHours, setValidHours] = useState(24 * 7);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [created, setCreated] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api.post<{ code: string; invite: InviteView }>("/api/admin/invites", { maxUses: Number(maxUses), validHours, note: note.trim() || null });
      setCreated(res.code);
      setNote("");
      void qc.invalidateQueries({ queryKey: keys.invites });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  const signupLink = created ? `${window.location.origin}/register?code=${encodeURIComponent(created)}` : "";

  return (
    <div className="grid gap-8 lg:grid-cols-[22rem_1fr]">
      <section className="flex flex-col gap-4">
        <form onSubmit={submit} className="panel flex flex-col gap-4 p-5">
          <div>
            <h2 className="font-display text-2xl">{t("admin.newInvite")}</h2>
            <p className="text-sm text-trim-dim">{t("admin.newInviteLead")}</p>
          </div>
          <Field label={t("admin.maxUses")} type="number" min={1} max={100} inputMode="numeric" value={maxUses} onChange={(e) => setMaxUses(e.target.value)} />
          <Segmented
            label={t("admin.validFor")}
            value={validHours}
            onChange={setValidHours}
            options={[
              { value: 1, label: t("admin.validOptions.h1") },
              { value: 24, label: t("admin.validOptions.h24") },
              { value: 24 * 7, label: t("admin.validOptions.d7") },
              { value: 24 * 30, label: t("admin.validOptions.d30") },
            ]}
          />
          <Field label={`${t("admin.note")} (${t("common.optional")})`} placeholder={t("admin.notePlaceholder")} maxLength={100} value={note} onChange={(e) => setNote(e.target.value)} />
          {error !== null && <ErrorNotice error={error} />}
          <Button type="submit" busy={busy} disabled={!(Number(maxUses) >= 1 && Number(maxUses) <= 100)}>
            <KeyRound className="size-4" aria-hidden />
            {t("admin.createInvite")}
          </Button>
        </form>
        {created && (
          <div className="rounded-lg border-2 border-brass bg-brass/10 p-5" role="status">
            <p className="text-xs font-bold text-brass">{t("admin.inviteCreated")}</p>
            <p className="my-2 select-all font-mono text-2xl font-bold text-trim">{created}</p>
            <p className="mb-3 text-sm text-trim-dim">{t("admin.inviteOnce")}</p>
            <div className="flex flex-wrap gap-2">
              <CopyButton text={created} label={t("admin.copyCode")} />
              <CopyButton text={signupLink} label={t("admin.shareLink")} icon="link" />
            </div>
          </div>
        )}
      </section>

      <section>
        <SectionTitle>{t("admin.invites")}</SectionTitle>
        {q.isLoading ? (
          <Spinner />
        ) : (q.data ?? []).length === 0 ? (
          <EmptyState title={t("admin.noInvites")} />
        ) : (
          <ul className="flex flex-col gap-2">
            {q.data!.map((inv) => (
              <li key={inv.id} className={`panel flex flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 ${inv.status !== "ACTIVE" ? "opacity-60" : ""}`}>
                <span className="font-mono font-bold">{inv.hint}</span>
                <span className={`rounded px-2 py-0.5 text-xs font-bold  ${inv.status === "ACTIVE" ? "bg-board/25 text-board-bright" : "bg-masonry-2 text-trim-faint"}`}>
                  {t(`admin.inviteStatus.${inv.status}`)}
                </span>
                <span className="tnum text-sm">
                  {t("admin.uses")} {inv.uses}/{inv.maxUses}
                </span>
                <span className="text-sm text-trim-dim">
                  {t("admin.expires")} {dateTime(inv.expiresAt)}
                </span>
                {inv.note && <span className="text-sm italic text-trim-dim">{inv.note}</span>}
                {inv.usedBy.length > 0 && (
                  <span className="w-full text-xs text-trim-faint">
                    {t("admin.usedBy")}: {inv.usedBy.map((u) => `@${u}`).join(", ")}
                  </span>
                )}
                {inv.status === "ACTIVE" && (
                  <span className="ml-auto">
                    <ConfirmButton
                      label={t("admin.revoke")}
                      message={t("admin.revokeConfirm")}
                      onConfirm={async () => {
                        await api.post(apiPath`/api/admin/invites/${inv.id}/revoke`);
                        void qc.invalidateQueries({ queryKey: keys.invites });
                      }}
                    />
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function Users() {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const qc = useQueryClient();
  const q = useAdminUsers();
  const [error, setError] = useState<unknown>(null);
  const [recomputed, setRecomputed] = useState<number | null>(null);
  const [awardMonths, setAwardMonths] = useState<number | null>(null);
  const [resetLink, setResetLink] = useState<{ username: string; url: string; expiresAt: string } | null>(null);

  const createResetLink = async (id: number) => {
    setError(null);
    try {
      const res = await api.post<{ token: string; username: string; expiresAt: string }>(apiPath`/api/admin/users/${id}/password-reset`);
      setResetLink({
        username: res.username,
        url: `${window.location.origin}/reset-password?token=${encodeURIComponent(res.token)}`,
        expiresAt: res.expiresAt,
      });
    } catch (err) {
      setError(err);
    }
  };

  const update = async (id: number, body: Record<string, unknown>) => {
    setError(null);
    try {
      await api.patch(apiPath`/api/admin/users/${id}`, body);
      void qc.invalidateQueries({ queryKey: keys.adminUsers });
      void qc.invalidateQueries({ queryKey: keys.players });
    } catch (err) {
      setError(err);
    }
  };

  if (q.isLoading) return <Spinner />;
  return (
    <div className="flex flex-col gap-4">
      {error !== null && <ErrorNotice error={error} />}
      <div className="panel overflow-x-auto">
        <table className="w-full min-w-[36rem] text-sm">
          <thead className="text-left text-xs text-trim-faint">
            <tr>
              <th className="px-4 py-3">{t("players.player")}</th>
              <th className="px-2 py-3">{t("admin.role")}</th>
              <th className="px-2 py-3 text-right">{t("stats.rating")}</th>
              <th className="px-2 py-3">{t("admin.joined")}</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {(q.data ?? []).map((u) => (
              <tr key={u.id} className={`border-t border-mortar/60 ${u.enabled ? "" : "opacity-50"}`}>
                <td className="px-4 py-3">
                  {u.deleted ? (
                    <span className="font-semibold italic text-trim-dim">{u.realName}</span>
                  ) : (
                    <>
                      <Link to={`/players/${u.username}`} className="font-semibold hover:underline">
                        {u.realName}
                      </Link>
                      <span className="ml-2 text-trim-faint">@{u.username}</span>
                    </>
                  )}
                  {u.deleted ? (
                    <span className="ml-2 text-xs font-bold text-trim-faint">{t("admin.deleted")}</span>
                  ) : (
                    !u.enabled && <span className="ml-2 text-xs font-bold text-salmon">{t("admin.disabled")}</span>
                  )}
                </td>
                <td className="px-2">
                  <span className={u.role === "ADMIN" ? "font-bold text-brass" : "text-trim-dim"}>{t(`admin.roles.${u.role}`)}</span>
                </td>
                <td className="tnum px-2 text-right">{u.rating}</td>
                <td className="px-2 text-trim-faint">{relative(u.createdAt)}</td>
                <td className="px-4 py-2 text-right">
                  {!u.deleted && (
                  <span className="inline-flex gap-2">
                    <Button size="sm" variant="secondary" onClick={() => createResetLink(u.id)}>
                      <LockKeyhole className="size-3.5" aria-hidden />
                      {t("admin.resetLink")}
                    </Button>
                  {u.id !== me?.id && (
                    <>
                      <Button size="sm" variant="ghost" onClick={() => update(u.id, { role: u.role === "ADMIN" ? "USER" : "ADMIN" })}>
                        {u.role === "ADMIN" ? t("admin.makeUser") : t("admin.makeAdmin")}
                      </Button>
                      <Button size="sm" variant={u.enabled ? "danger" : "secondary"} onClick={() => update(u.id, { enabled: !u.enabled })}>
                        {u.enabled ? t("admin.disable") : t("admin.enable")}
                      </Button>
                    </>
                  )}
                  </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Modal open={resetLink !== null} onClose={() => setResetLink(null)} title={resetLink ? t("admin.resetTitle", { username: resetLink.username }) : ""}>
        {resetLink && (
          <div className="flex flex-col gap-4">
            <p className="text-sm text-trim-dim">{t("admin.resetHow")}</p>
            <p className="select-all break-all rounded-md border border-brass/60 bg-brass/10 p-3 font-mono text-sm text-trim">{resetLink.url}</p>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-xs text-trim-faint">{t("admin.resetExpires", { date: dateTime(resetLink.expiresAt) })}</span>
              <CopyButton text={resetLink.url} label={t("admin.copyLink")} icon="link" />
            </div>
          </div>
        )}
      </Modal>
      <div className="flex flex-wrap items-center gap-3">
        <ConfirmButton
          variant="secondary"
          label={t("admin.recompute")}
          message={t("admin.recompute")}
          onConfirm={async () => {
            const res = await api.post<{ matches: number }>("/api/admin/ratings/recompute");
            setRecomputed(res.matches);
            void qc.invalidateQueries();
          }}
        />
        {recomputed !== null && <span className="text-sm text-board-bright">{t("admin.recomputeDone", { count: recomputed })}</span>}
        <ConfirmButton
          variant="secondary"
          label={t("admin.recomputeAwards")}
          message={t("admin.recomputeAwards")}
          onConfirm={async () => {
            const res = await api.post<{ months: number }>("/api/admin/awards/recompute");
            setAwardMonths(res.months);
            void qc.invalidateQueries({ queryKey: ["awards"] });
          }}
        />
        {awardMonths !== null && <span className="text-sm text-board-bright">{t("admin.recomputeAwardsDone", { count: awardMonths })}</span>}
      </div>
    </div>
  );
}

function Disputes() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const q = useDisputes();
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [error, setError] = useState<unknown>(null);

  const resolve = async (id: number, outcome: "REJECT" | "VOID_MATCH") => {
    setError(null);
    try {
      await api.post(apiPath`/api/admin/disputes/${id}/resolve`, { outcome, note: notes[id]?.trim() || null });
      void qc.invalidateQueries();
    } catch (err) {
      setError(err);
    }
  };

  if (q.isLoading) return <Spinner />;
  if ((q.data ?? []).length === 0) return <EmptyState title={t("admin.noDisputes")} />;
  return (
    <div className="flex flex-col gap-4">
      {error !== null && <ErrorNotice error={error} />}
      {q.data!.map((d) => (
        <article key={d.id} className={`panel p-5 ${d.status === "OPEN" ? "border-salmon/60" : "opacity-70"}`}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm text-trim-dim">
              {t("admin.raisedBy", { name: `@${d.raisedBy}` })}, {relative(d.createdAt)}
            </p>
            <span className="rounded bg-masonry-2 px-2 py-0.5 text-xs font-bold">{t(`admin.disputeStatus.${d.status}`)}</span>
          </div>
          <blockquote className="my-3 border-l-2 border-trafon pl-3 text-trim">{d.reason}</blockquote>
          {d.match && (
            <ul className="rounded-md border border-mortar">
              <MatchRow match={d.match} />
            </ul>
          )}
          {d.status === "OPEN" ? (
            <div className="mt-4 flex flex-col gap-3">
              <Field label={`${t("admin.resolveNote")} (${t("common.optional")})`} value={notes[d.id] ?? ""} maxLength={500} onChange={(e) => setNotes({ ...notes, [d.id]: e.target.value })} />
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" onClick={() => resolve(d.id, "REJECT")}>
                  {t("admin.reject")}
                </Button>
                <ConfirmButton size="md" label={t("admin.voidMatch")} message={t("admin.voidConfirm")} onConfirm={() => resolve(d.id, "VOID_MATCH")} />
              </div>
            </div>
          ) : (
            d.resolutionNote && <p className="mt-3 text-sm text-trim-dim">{d.resolutionNote}</p>
          )}
        </article>
      ))}
    </div>
  );
}
