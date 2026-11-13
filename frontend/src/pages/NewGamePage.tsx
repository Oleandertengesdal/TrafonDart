import { Check, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { api } from "../api/client";
import { usePlayers } from "../api/queries";
import type { GameType, GameView, PartySettings } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { Button, ErrorNotice, PageHeader, Segmented, Spinner, Toggle } from "../components/ui";
import { lineupError, playerIdsForRequest } from "../features/games/gameLogic";
import { usePageTitle } from "../lib/pageTitle";

const TYPES: GameType[] = ["X01", "CRICKET", "KILLER", "AROUND_THE_CLOCK"];

/** Party games: X01 for 3–4 players or 2 v 2, Cricket, Killer and Around the Clock. Never rated. */
export function NewGamePage() {
  const { t } = useTranslation();
  usePageTitle(t("games.newTitle"));
  const { me } = useCurrentUser();
  const players = usePlayers();
  const navigate = useNavigate();
  const [type, setType] = useState<GameType>("X01");
  const [teams, setTeams] = useState(false);
  const [chosen, setChosen] = useState<number[]>(me ? [me.id] : []);
  const [startScore, setStartScore] = useState(501);
  const [outRule, setOutRule] = useState<"STRAIGHT" | "DOUBLE" | "MASTER">("DOUBLE");
  const [legsToWin, setLegsToWin] = useState(1);
  const [roundLimit, setRoundLimit] = useState(20);
  const [lives, setLives] = useState(3);
  const [skips, setSkips] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  if (players.isLoading) return <Spinner />;
  if (players.error) return <ErrorNotice error={players.error} onRetry={() => players.refetch()} />;
  const list = players.data ?? [];
  const teamsAllowed = type === "X01" || type === "CRICKET";
  const useTeams = teams && teamsAllowed;
  const problem = lineupError(type, useTeams, chosen.length);
  const name = (id: number) => list.find((p) => p.id === id)?.realName ?? "?";

  const toggle = (id: number) => setChosen((c) => (c.includes(id) ? c.filter((x) => x !== id) : c.length >= 8 ? c : [...c, id]));

  const settings = (): Partial<PartySettings> => {
    switch (type) {
      case "X01":
        return { startScore, inRule: "STRAIGHT", outRule, legsToWin };
      case "CRICKET":
        return { legsToWin: 1, roundLimit };
      case "KILLER":
        return { lives };
      case "AROUND_THE_CLOCK":
        return { multiplierSkips: skips };
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (problem) return;
    setBusy(true);
    setError(null);
    try {
      const ids = playerIdsForRequest(useTeams, useTeams ? chosen.slice(0, 2) : chosen, useTeams ? chosen.slice(2, 4) : []);
      const game = await api.post<GameView>("/api/games", { type, teams: useTeams, playerIds: ids, settings: settings() });
      navigate(`/games/${game.id}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title={t("games.newTitle")} lead={t("games.newLead")} />
      <form onSubmit={submit} className="grid gap-8 lg:grid-cols-[1fr_1fr] [&>*]:min-w-0">
        <section className="panel flex flex-col gap-5 p-5">
          <Segmented label={t("games.type")} value={type} onChange={setType} options={TYPES.map((v) => ({ value: v, label: t(`games.types.${v}`) }))} />
          <p className="-mt-2 text-sm text-trim-dim">{t(`games.typeHint.${type}`)}</p>
          {teamsAllowed && <Toggle label={t("games.teams")} hint={t("games.teamsHint")} checked={teams} onChange={setTeams} />}

          {type === "X01" && (
            <>
              <Segmented label={t("match.startScore")} value={startScore} onChange={setStartScore} options={[301, 501, 701].map((v) => ({ value: v, label: String(v) }))} />
              <Segmented
                label={t("match.outRule")}
                value={outRule}
                onChange={setOutRule}
                hint={t(`match.outHint.${outRule}`)}
                options={(["STRAIGHT", "DOUBLE", "MASTER"] as const).map((v) => ({ value: v, label: t(`match.out.${v}`) }))}
              />
              <Segmented label={t("match.legsToWin")} value={legsToWin} onChange={setLegsToWin} options={[1, 2, 3].map((v) => ({ value: v, label: String(v) }))} />
            </>
          )}
          {type === "CRICKET" && (
            <Segmented
              label={t("games.roundLimit")}
              value={roundLimit}
              onChange={setRoundLimit}
              hint={t("games.roundLimitHint")}
              options={[
                { value: 20, label: "20" },
                { value: 25, label: "25" },
                { value: 0, label: t("games.noLimit") },
              ]}
            />
          )}
          {type === "KILLER" && (
            <Segmented label={t("games.lives")} value={lives} onChange={setLives} hint={t("games.livesHint")} options={[1, 2, 3, 4, 5].map((v) => ({ value: v, label: String(v) }))} />
          )}
          {type === "AROUND_THE_CLOCK" && <Toggle label={t("games.skips")} hint={t("games.skipsHint")} checked={skips} onChange={setSkips} />}
        </section>

        <section className="panel flex flex-col gap-4 p-5">
          <div>
            <h2 className="font-display text-2xl">{t("games.players")}</h2>
            <p className="text-sm text-trim-dim">{useTeams ? t("games.pickTeams") : t("games.pickOrder")}</p>
          </div>
          {chosen.length > 0 && (
            <ol className="flex flex-col gap-2" aria-label={t("games.order")}>
              {chosen.map((id, i) => (
                <li key={id} className="flex items-center gap-3 rounded-md border border-mortar bg-night px-3 py-2">
                  <span className="numerals w-6 text-xl font-bold text-trim-faint">{i + 1}</span>
                  <span className="flex-1 font-semibold">{name(id)}</span>
                  {useTeams && <span className="text-xs font-bold text-trim-dim">{i < 2 ? t("games.teamA") : i < 4 ? t("games.teamB") : ""}</span>}
                  <button type="button" onClick={() => toggle(id)} className="rounded p-1 text-trim-faint hover:text-trim" aria-label={t("games.remove", { name: name(id) })}>
                    <X className="size-4" />
                  </button>
                </li>
              ))}
            </ol>
          )}
          <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {list.map((p) => {
              const on = chosen.includes(p.id);
              return (
                <li key={p.id}>
                  <button
                    type="button"
                    aria-pressed={on}
                    onClick={() => toggle(p.id)}
                    className={`flex min-h-12 w-full items-center gap-2 rounded-md border px-3 py-2 text-left text-sm font-semibold ${
                      on ? "border-trafon bg-trafon/15 text-trim" : "border-mortar text-trim-dim hover:text-trim"
                    }`}
                  >
                    {on && <Check className="size-4 shrink-0 text-trafon-bright" aria-hidden />}
                    <span className="truncate">{p.realName}</span>
                  </button>
                </li>
              );
            })}
          </ul>
          {problem && chosen.length > 0 && <p className="text-sm text-salmon">{t(`games.lineup.${problem}`)}</p>}
        </section>

        <div className="flex flex-col gap-3 lg:col-span-2">
          <p className="text-sm text-trim-faint">{t("games.neverRated")}</p>
          {error !== null && <ErrorNotice error={error} />}
          <Button type="submit" size="lg" busy={busy} disabled={!!problem} className="w-full sm:w-auto sm:self-end">
            {t("games.start")}
          </Button>
        </div>
      </form>
    </>
  );
}
