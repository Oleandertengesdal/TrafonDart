import { useQueryClient } from "@tanstack/react-query";
import { Heart, MessageCircle, PartyPopper, Pencil, Reply, Trash2 } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { api, apiPath } from "../../api/client";
import { keys, useSocial } from "../../api/queries";
import type { CommentView, SocialView } from "../../api/types";
import { Button, ErrorNotice, Modal, Spinner, useErrorText } from "../../components/ui";
import { relative } from "../../lib/format";
import { useTopic } from "../../realtime/useTopic";

const MAX = 1000;

/** Kudos + comment thread for a match, live-updated for everyone on the page. */
export function MatchSocial({ matchId }: { matchId: string }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const q = useSocial(matchId);
  const errorText = useErrorText();
  const [error, setError] = useState<unknown>(null);

  useTopic(`/topic/matches/${matchId}/social`, () => void qc.invalidateQueries({ queryKey: keys.social(matchId) }));

  /** Every mutation returns the fresh view – put it straight in the cache. */
  const mutate = async (fn: () => Promise<SocialView>) => {
    setError(null);
    try {
      qc.setQueryData(keys.social(matchId), await fn());
      void qc.invalidateQueries({ queryKey: ["matches", "recent"] });
    } catch (err) {
      setError(err);
    }
  };

  if (q.isLoading) return <Spinner />;
  if (!q.data) return q.error ? <ErrorNotice error={q.error} onRetry={() => q.refetch()} /> : null;
  const s = q.data;

  return (
    <section className="mt-10 flex flex-col gap-6" aria-label={t("social.comments")}>
      <KudosBar s={s} onToggle={() => mutate(() => (s.kudos.mine ? api.del<SocialView>(apiPath`/api/matches/${matchId}/kudos`) : api.put<SocialView>(apiPath`/api/matches/${matchId}/kudos`)))} />

      <div className="panel p-5">
        <h2 className="mb-4 flex items-center gap-2 font-display text-2xl">
          <MessageCircle className="size-5 text-trim-faint" aria-hidden />
          {t("social.comments")}
          {s.commentCount > 0 && <span className="text-base text-trim-faint">({s.commentCount})</span>}
        </h2>
        <Composer placeholder={t("social.placeholder")} onSend={(body) => mutate(() => api.post<SocialView>(apiPath`/api/matches/${matchId}/comments`, { body }))} />
        {error !== null && <p className="mt-3 text-sm text-salmon" role="alert">{errorText(error)}</p>}

        {s.comments.length === 0 ? (
          <p className="mt-6 text-sm text-trim-faint">{t("social.noComments")}</p>
        ) : (
          <ul className="mt-6 flex flex-col gap-5">
            {s.comments.map((c) => (
              <li key={c.id}>
                <Comment c={c} matchId={matchId} mutate={mutate} />
                {c.replies.length > 0 && (
                  <ul className="ml-6 mt-3 flex flex-col gap-3 border-l-2 border-mortar pl-4 sm:ml-10">
                    {c.replies.map((r) => (
                      <li key={r.id}>
                        <Comment c={r} matchId={matchId} mutate={mutate} replyTo={c.id} />
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function names(list: { realName: string }[]) {
  if (list.length <= 3) return list.map((p) => p.realName).join(", ");
  return `${list.slice(0, 3).map((p) => p.realName).join(", ")} +${list.length - 3}`;
}

function KudosBar({ s, onToggle }: { s: SocialView; onToggle: () => void }) {
  const { t } = useTranslation();
  const k = s.kudos;
  return (
    <div className="flex flex-wrap items-center gap-4">
      {k.canGive ? (
        <button
          onClick={onToggle}
          aria-pressed={k.mine}
          className={`flex h-12 items-center gap-2 rounded-full border-2 px-5 font-display text-xl    transition-transform active:scale-95 ${
            k.mine ? "border-brass bg-brass text-night" : "border-brass/60 text-brass hover:bg-brass/10"
          }`}
        >
          <PartyPopper className="size-5" aria-hidden />
          {k.mine ? t("social.removeKudos") : t("social.giveKudos")}
        </button>
      ) : (
        <span className="flex h-12 items-center gap-2 rounded-full border-2 border-mortar px-5 font-display text-xl text-trim-faint" title={t("social.ownMatch")}>
          <PartyPopper className="size-5" aria-hidden />
          {t("social.kudos")}
        </span>
      )}
      <div className="text-sm">
        <span className="font-display text-2xl text-brass">{t("social.kudosCount", { count: k.count })}</span>
        {k.givers.length > 0 && (
          <p className="text-trim-dim">
            {t("social.kudosFrom", { names: "" })}
            {k.givers.map((g, i) => (
              <span key={g.id}>
                {i > 0 && ", "}
                <Link to={`/players/${g.username}`} className="font-semibold text-trim hover:underline">
                  {g.realName}
                </Link>
              </span>
            ))}
          </p>
        )}
      </div>
    </div>
  );
}

function Composer({ placeholder, onSend, initial = "", autoFocus, onCancel, submitLabel }: {
  placeholder: string;
  onSend: (body: string) => Promise<void>;
  initial?: string;
  autoFocus?: boolean;
  onCancel?: () => void;
  submitLabel?: string;
}) {
  const { t } = useTranslation();
  const [body, setBody] = useState(initial);
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!body.trim()) return;
    setBusy(true);
    await onSend(body.trim());
    setBusy(false);
    setBody("");
  };
  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        maxLength={MAX}
        rows={2}
        autoFocus={autoFocus}
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) void submit(e);
        }}
        className="min-h-16 w-full resize-y rounded-md border border-mortar bg-night p-3 text-trim placeholder:text-trim-faint focus:border-trafon focus:outline-none"
      />
      <div className="flex items-center justify-end gap-2">
        {body.length > MAX * 0.8 && <span className="mr-auto text-xs text-trim-faint">{body.length}/{MAX}</span>}
        {onCancel && (
          <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
            {t("common.cancel")}
          </Button>
        )}
        <Button type="submit" size="sm" busy={busy} disabled={!body.trim()}>
          {submitLabel ?? t("social.send")}
        </Button>
      </div>
    </form>
  );
}

function Comment({ c, matchId, mutate, replyTo }: {
  c: CommentView;
  matchId: string;
  mutate: (fn: () => Promise<SocialView>) => Promise<void>;
  replyTo?: number;
}) {
  const { t } = useTranslation();
  const [replying, setReplying] = useState(false);
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (c.deleted) {
    return <p className="text-sm italic text-trim-faint">{t("social.deleted")}</p>;
  }

  return (
    <article className="flex gap-3">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-masonry-2 font-display text-lg text-trim-dim" aria-hidden>
        {c.author?.realName.charAt(0) ?? "?"}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm">
          {c.author ? (
            <Link to={`/players/${c.author.username}`} className="font-semibold text-trim hover:underline">
              {c.author.realName}
            </Link>
          ) : (
            "?"
          )}{" "}
          <span className="text-trim-faint">
            {relative(c.createdAt)}
            {c.editedAt && `, ${t("social.edited")}`}
          </span>
        </p>
        {editing ? (
          <div className="mt-2">
            <Composer
              placeholder={t("social.placeholder")}
              initial={c.body}
              autoFocus
              submitLabel={t("social.save")}
              onCancel={() => setEditing(false)}
              onSend={async (body) => {
                await mutate(() => api.patch<SocialView>(apiPath`/api/comments/${c.id}`, { body }));
                setEditing(false);
              }}
            />
          </div>
        ) : (
          <p className="mt-0.5 whitespace-pre-wrap break-words text-trim">{c.body}</p>
        )}
        <div className="mt-2 flex flex-wrap items-center gap-1 text-xs">
          <button
            onClick={() => mutate(() => (c.likedByMe ? api.del<SocialView>(apiPath`/api/comments/${c.id}/like`) : api.put<SocialView>(apiPath`/api/comments/${c.id}/like`)))}
            aria-pressed={c.likedByMe}
            title={c.likedBy.length ? t("social.likedBy", { names: names(c.likedBy) }) : undefined}
            className={`flex items-center gap-1 rounded px-2 py-1 font-semibold transition-colors ${c.likedByMe ? "text-trafon-bright" : "text-trim-faint hover:text-trim"}`}
          >
            <Heart className={`size-3.5 ${c.likedByMe ? "fill-current" : ""}`} aria-hidden />
            {c.likes > 0 ? t("social.likes", { count: c.likes }) : t("social.like")}
          </button>
          <button onClick={() => setReplying((v) => !v)} className="flex items-center gap-1 rounded px-2 py-1 font-semibold text-trim-faint hover:text-trim">
            <Reply className="size-3.5" aria-hidden />
            {t("social.reply")}
          </button>
          {c.canEdit && !editing && (
            <button onClick={() => setEditing(true)} className="flex items-center gap-1 rounded px-2 py-1 font-semibold text-trim-faint hover:text-trim">
              <Pencil className="size-3.5" aria-hidden />
              {t("social.edit")}
            </button>
          )}
          {c.canDelete && (
            <button onClick={() => setConfirmDelete(true)} className="flex items-center gap-1 rounded px-2 py-1 font-semibold text-trim-faint hover:text-salmon">
              <Trash2 className="size-3.5" aria-hidden />
              {t("social.delete")}
            </button>
          )}
        </div>
        {replying && (
          <div className="mt-2">
            <Composer
              placeholder={t("social.replyPlaceholder")}
              autoFocus
              initial={replyTo && c.author ? `@${c.author.username} ` : ""}
              onCancel={() => setReplying(false)}
              onSend={async (body) => {
                await mutate(() => api.post<SocialView>(apiPath`/api/matches/${matchId}/comments`, { body, parentId: replyTo ?? c.id }));
                setReplying(false);
              }}
            />
          </div>
        )}
      </div>
      <Modal open={confirmDelete} onClose={() => setConfirmDelete(false)} title={t("social.delete")}>
        <p className="mb-5 text-trim-dim">{t("social.deleteConfirm")}</p>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
            {t("common.cancel")}
          </Button>
          <Button
            onClick={() => {
              setConfirmDelete(false);
              void mutate(() => api.del<SocialView>(apiPath`/api/comments/${c.id}`));
            }}
          >
            {t("social.delete")}
          </Button>
        </div>
      </Modal>
    </article>
  );
}
