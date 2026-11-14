import { useId, useMemo, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router";
import { ArrowLeftRight, Bot, Users } from "lucide-react";
import { api } from "../api/client";
import { usePlayers } from "../api/queries";
import type { InRule, LiveMatch, OutRule, PlayerSummary } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { Button, ErrorNotice, LinkButton, PageHeader, Segmented, Spinner, Toggle } from "../components/ui";
import { usePageTitle } from "../lib/pageTitle";
import { startScoreOptions } from "../lib/x01";

export function PlayerSelect({
  label,
  value,
  onChange,
  players,
  exclude,
}: {
  label: string;
  value: number | null;
  onChange: (id: number) => void;
  players: PlayerSummary[];
  exclude?: number | null;
}) {
  const { t } = useTranslation();
  const id = useId();
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="text-sm font-medium text-trim-dim">
        {label}
      </label>
      <select
        id={id}
        value={value ?? ""}
        onChange={(e) => onChange(Number(e.target.value))}
        className="h-12 w-full min-w-0 rounded-md border border-mortar bg-night px-3 text-base font-semibold text-trim focus:border-trafon focus:outline-none"
      >
        <option value="" disabled>
          {t("match.choosePlayer")}
        </option>
        {players
          .filter((p) => p.id !== exclude)
          .map((p) => (
            <option key={p.id} value={p.id}>
              {p.realName} (@{p.username}) – {p.rating}
            </option>
          ))}
      </select>
    </div>
  );
}

export function NewMatchPage() {
  const { t } = useTranslation();
  usePageTitle(t("match.newTitle"));
  const { me } = useCurrentUser();
  const players = usePlayers();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const initial = useMemo(
    () => ({
      p1: params.get("p1") ? Number(params.get("p1")) : (me?.id ?? null),
      p2: params.get("p2") ? Number(params.get("p2")) : null,
      score: Number(params.get("score") ?? 501),
      inRule: (params.get("in") as InRule) ?? "STRAIGHT",
      outRule: (params.get("out") as OutRule) ?? "DOUBLE",
      legs: Number(params.get("legs") ?? 3),
      rated: params.get("rated") !== "0",
    }),
    [params, me?.id],
  );

  const [p1, setP1] = useState<number | null>(initial.p1);
  const [p2, setP2] = useState<number | null>(initial.p2);
  const [startScore, setStartScore] = useState(initial.score);
  const [inRule, setInRule] = useState<InRule>(initial.inRule);
  const [outRule, setOutRule] = useState<OutRule>(initial.outRule);
  const [legsToWin, setLegsToWin] = useState(initial.legs);
  const [starter, setStarter] = useState<"0" | "1" | "random">("random");
  const [rated, setRated] = useState(initial.rated);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  if (players.isLoading) return <Spinner />;
  if (players.error) return <ErrorNotice error={players.error} onRetry={() => players.refetch()} />;
  const list = players.data ?? [];
  const includesMe = me && (p1 === me.id || p2 === me.id);
  const ratedAllowed = me?.role === "ADMIN" || includesMe;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!p1 || !p2) return;
    setBusy(true);
    setError(null);
    try {
      const match = await api.post<LiveMatch>("/api/matches", {
        player1Id: p1,
        player2Id: p2,
        startScore,
        inRule,
        outRule,
        legsToWin,
        startingPlayer: starter === "random" ? null : Number(starter),
        rated: rated && !!ratedAllowed,
      });
      navigate(`/matches/${match.id}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  const name = (id: number | null) => list.find((p) => p.id === id)?.realName ?? "–";

  return (
    <>
      <PageHeader
        title={t("match.newTitle")}
        lead={t("match.newLead")}
        action={
          <div className="flex flex-wrap gap-2">
            <LinkButton to="/bot" variant="secondary">
              <Bot className="size-4" aria-hidden />
              {t("bot.nav")}
            </LinkButton>
            <LinkButton to="/games/new" variant="secondary">
              <Users className="size-4" aria-hidden />
              {t("games.moreThanTwo")}
            </LinkButton>
          </div>
        }
      />
      <form onSubmit={submit} className="grid gap-8 lg:grid-cols-[1fr_1fr] [&>*]:min-w-0">
        <section className="panel flex flex-col gap-5 p-5">
          <div className="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]">
            <PlayerSelect label={t("match.player1")} value={p1} onChange={setP1} players={list} exclude={p2} />
            <button
              type="button"
              onClick={() => {
                setP1(p2);
                setP2(p1);
              }}
              className="mx-auto mb-2 rounded-full border border-mortar p-2 text-trim-dim hover:text-trim"
              aria-label={t("match.swap")}
              title={t("match.swap")}
            >
              <ArrowLeftRight className="size-4" />
            </button>
            <PlayerSelect label={t("match.player2")} value={p2} onChange={setP2} players={list} exclude={p1} />
          </div>
          <Segmented
            label={t("match.whoStarts")}
            value={starter}
            onChange={setStarter}
            options={[
              { value: "0", label: name(p1) },
              { value: "random", label: t("match.random") },
              { value: "1", label: name(p2) },
            ]}
          />
          <div className="border-t border-mortar pt-5">
            <Toggle label={t("match.rated")} hint={ratedAllowed ? undefined : t("match.ratedHint")} checked={rated && !!ratedAllowed} onChange={(v) => setRated(v)} />
          </div>
        </section>

        <section className="panel flex flex-col gap-5 p-5">
          <Segmented label={t("match.startScore")} value={startScore} onChange={setStartScore} options={startScoreOptions} />
          <Segmented
            label={t("match.inRule")}
            value={inRule}
            onChange={setInRule}
            hint={t(`match.inHint.${inRule}`)}
            options={(["STRAIGHT", "DOUBLE"] as InRule[]).map((v) => ({ value: v, label: t(`match.in.${v}`) }))}
          />
          <Segmented
            label={t("match.outRule")}
            value={outRule}
            onChange={setOutRule}
            hint={t(`match.outHint.${outRule}`)}
            options={(["STRAIGHT", "DOUBLE", "MASTER"] as OutRule[]).map((v) => ({ value: v, label: t(`match.out.${v}`) }))}
          />
          <Segmented
            label={t("match.legsToWin")}
            value={legsToWin}
            onChange={setLegsToWin}
            options={[1, 2, 3, 4, 5, 7].map((v) => ({ value: v, label: String(v) }))}
            hint={t("match.legsCount", { count: legsToWin })}
          />
        </section>

        <div className="flex flex-col gap-3 lg:col-span-2">
          {error !== null && <ErrorNotice error={error} />}
          <Button type="submit" size="lg" busy={busy} disabled={!p1 || !p2 || p1 === p2} className="w-full sm:w-auto sm:self-end">
            {busy ? t("match.starting") : t("match.start")}
          </Button>
        </div>
      </form>
    </>
  );
}
