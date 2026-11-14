import { useQueryClient } from "@tanstack/react-query";
import { Bot, Check, Play } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useSearchParams } from "react-router";
import { api, apiPath } from "../api/client";
import { keys, useBotRecord } from "../api/queries";
import type { BotLevel, BotMatch, BotRecord, InRule, OutRule } from "../api/types";
import { useCurrentUser } from "../auth/auth";
import { Button, ConfirmButton, ErrorNotice, LinkButton, PageHeader, Segmented } from "../components/ui";
import { BOT_AVERAGES, BOT_LEVELS } from "../features/bot/botLogic";
import { num } from "../lib/format";
import { usePageTitle } from "../lib/pageTitle";
import { startScoreOptions } from "../lib/x01";

const isLevel = (v: string | null): v is BotLevel => !!v && (BOT_LEVELS as readonly string[]).includes(v);

/** Setting up a practice match against Trafo, with your record against it at a glance. */
export function BotPage() {
  const { t } = useTranslation();
  usePageTitle(t("bot.title"));
  const { me } = useCurrentUser();
  const record = useBotRecord(me?.username);
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [params] = useSearchParams();

  const [level, setLevel] = useState<BotLevel>(isLevel(params.get("level")) ? (params.get("level") as BotLevel) : "CLUB");
  const [startScore, setStartScore] = useState(Number(params.get("score") ?? 501));
  const [inRule, setInRule] = useState<InRule>((params.get("in") as InRule) ?? "STRAIGHT");
  const [outRule, setOutRule] = useState<OutRule>((params.get("out") as OutRule) ?? "DOUBLE");
  const [legsToWin, setLegsToWin] = useState(Number(params.get("legs") ?? 1));
  const [starter, setStarter] = useState<"0" | "1" | "random">("0");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const open = record.data?.openMatchId ?? null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const match = await api.post<BotMatch>("/api/bot-matches", {
        level,
        startScore,
        inRule,
        outRule,
        legsToWin,
        startingPlayer: starter === "random" ? null : Number(starter),
      });
      qc.setQueryData(keys.botMatch(match.id), match);
      navigate(`/bot/${match.id}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title={t("bot.title")} lead={t("bot.lead")} />

      {open && (
        <div className="mb-8 flex flex-wrap items-center gap-4 rounded-xl border border-brass/50 bg-brass/10 px-5 py-4">
          <Bot className="size-7 text-brass" aria-hidden />
          <p className="flex-1 font-display text-xl">{t("bot.open")}</p>
          <LinkButton to={`/bot/${open}`}>
            <Play className="size-4" aria-hidden />
            {t("bot.carryOn")}
          </LinkButton>
          <ConfirmButton
            label={t("bot.forfeit")}
            message={t("bot.forfeitConfirm")}
            onConfirm={async () => {
              await api.post(apiPath`/api/bot-matches/${open}/forfeit`);
              void qc.invalidateQueries({ queryKey: keys.botRecord(me!.username) });
            }}
          />
        </div>
      )}

      <form onSubmit={submit} className="grid gap-8 lg:grid-cols-[1.1fr_1fr] [&>*]:min-w-0">
        <fieldset className="panel flex flex-col gap-3 p-5">
          <legend className="sr-only">{t("bot.level")}</legend>
          <p className="text-sm font-medium text-trim-dim" aria-hidden>
            {t("bot.level")}
          </p>
          {BOT_LEVELS.map((l) => (
            <LevelOption key={l} level={l} checked={level === l} onChange={() => setLevel(l)} record={record.data} />
          ))}
        </fieldset>

        <section className="panel flex flex-col gap-5 p-5">
          <Segmented
            label={t("match.whoStarts")}
            value={starter}
            onChange={setStarter}
            options={[
              { value: "0", label: t("bot.you") },
              { value: "random", label: t("match.random") },
              { value: "1", label: "Trafo" },
            ]}
          />
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
          <Button type="submit" size="lg" busy={busy} disabled={!!open} className="w-full sm:w-auto sm:self-end">
            {busy ? t("bot.starting") : t("bot.start")}
          </Button>
        </div>
      </form>

      {record.data && record.data.played > 0 && <RecordGlance record={record.data} />}
    </>
  );
}

function LevelOption({ level, checked, onChange, record }: { level: BotLevel; checked: boolean; onChange: () => void; record?: BotRecord }) {
  const { t } = useTranslation();
  const mine = record?.levels.find((l) => l.level === level);
  const beaten = !!mine && mine.won > 0;
  return (
    <label
      className={`flex cursor-pointer items-start gap-4 rounded-lg border p-4 transition-colors ${
        checked ? "border-trafon bg-trafon/15" : "border-mortar bg-night hover:border-trim-faint"
      }`}
    >
      <input type="radio" name="level" value={level} checked={checked} onChange={onChange} className="sr-only" />
      <span
        aria-hidden
        className={`mt-1 flex size-5 shrink-0 items-center justify-center rounded-full border-2 ${checked ? "border-trafon-bright" : "border-mortar"}`}
      >
        {checked && <span className="size-2 rounded-full bg-trafon-bright" />}
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="flex flex-wrap items-baseline gap-x-3">
          <span className="font-display text-xl">{t(`bot.levels.${level}.name`)}</span>
          <span className="text-sm text-trim-faint">{t("bot.average", { n: BOT_AVERAGES[level] })}</span>
        </span>
        <span className="text-sm text-trim-dim">{t(`bot.levels.${level}.hint`)}</span>
        {mine && mine.played > 0 && (
          <span className="text-xs text-trim-faint">{t("bot.levelRecord", { won: mine.won, played: mine.played })}</span>
        )}
      </span>
      {beaten && (
        <span className="flex shrink-0 items-center gap-1 rounded-full bg-board/20 px-2 py-0.5 text-xs font-semibold text-board-bright">
          <Check className="size-3.5" aria-hidden />
          {t("bot.beaten")}
        </span>
      )}
    </label>
  );
}

function RecordGlance({ record }: { record: BotRecord }) {
  const { t } = useTranslation();
  const tiles = [
    { label: t("bot.record.won"), value: t("stats.record", { won: record.won, lost: record.lost }) },
    { label: t("bot.record.streak"), value: String(record.currentStreak) },
    { label: t("bot.record.bestStreak"), value: String(record.bestStreak) },
    { label: t("bot.record.average"), value: num(record.throwing.average, 1) },
  ];
  return (
    <section className="mt-10">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="font-display text-2xl">{t("bot.record.mine")}</h2>
        <Link
          to={`/players/${record.player.username}#trafo`}
          className="text-sm font-semibold text-trim underline decoration-trafon decoration-2 underline-offset-4"
        >
          {t("bot.record.seeAll")}
        </Link>
      </div>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {tiles.map((tile) => (
          <div key={tile.label} className="panel flex flex-col gap-1 p-4">
            <dt className="text-xs font-semibold text-trim-faint">{tile.label}</dt>
            <dd className="numerals text-4xl font-extrabold leading-none">{tile.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
