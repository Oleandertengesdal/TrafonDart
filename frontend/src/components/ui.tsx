import { useEffect, useId, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  CircleAlert,
  Hourglass,
  LoaderCircle,
  Lock,
  LogIn,
  RefreshCw,
  SearchX,
  ServerCrash,
  Timer,
  TriangleAlert,
  WifiOff,
  X,
  type LucideIcon,
} from "lucide-react";
import { Link, type LinkProps } from "react-router";
import { describeError, type ErrorKind } from "../lib/errors";
import { useOnline } from "../lib/useOnline";

type Variant = "primary" | "secondary" | "ghost" | "danger" | "brass";

const variants: Record<Variant, string> = {
  primary: "bg-trafon text-trim hover:bg-trafon-bright active:bg-trafon-deep",
  secondary: "bg-masonry-2 text-trim border border-mortar hover:border-trim-faint",
  ghost: "text-trim-dim hover:text-trim hover:bg-masonry-2",
  danger: "bg-transparent text-salmon border border-trafon-deep hover:bg-trafon-deep/40",
  brass: "bg-brass text-night hover:brightness-110",
};

export function Button({
  variant = "primary",
  size = "md",
  busy = false,
  className = "",
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: "sm" | "md" | "lg"; busy?: boolean }) {
  const sizes = { sm: "h-8 px-3 text-sm", md: "h-10 px-4 text-sm", lg: "h-12 px-6 text-base" };
  return (
    <button
      {...rest}
      disabled={rest.disabled || busy}
      className={`inline-flex items-center justify-center gap-2 rounded-md font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${sizes[size]} ${className}`}
    >
      {busy && <LoaderCircle className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  );
}

/** A link styled as a button (avoids nesting a <button> inside an <a>). */
export function LinkButton({ variant = "primary", size = "md", className = "", ...rest }: LinkProps & { variant?: Variant; size?: "sm" | "md" | "lg" }) {
  const sizes = { sm: "h-8 px-3 text-sm", md: "h-10 px-4 text-sm", lg: "h-12 px-6 text-base" };
  return (
    <Link
      {...rest}
      className={`inline-flex items-center justify-center gap-2 rounded-md font-semibold transition-colors ${variants[variant]} ${sizes[size]} ${className}`}
    />
  );
}

export function Field({
  label,
  hint,
  error,
  className = "",
  ...input
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  const id = useId();
  const describedBy = error ? `${id}-err` : hint ? `${id}-hint` : undefined;
  return (
    <div className={`flex flex-col gap-2 ${className}`}>
      <label htmlFor={id} className="text-sm font-medium text-trim-dim">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={!!error}
        aria-describedby={describedBy}
        {...input}
        className="h-11 rounded-md border border-mortar bg-night px-3 text-trim placeholder:text-trim-faint focus:border-trafon focus:outline-none aria-[invalid=true]:border-trafon-bright"
      />
      {error ? (
        <p id={`${id}-err`} className="text-sm text-salmon">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-xs text-trim-faint">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export function Segmented<T extends string | number>({
  label,
  value,
  options,
  onChange,
  hint,
}: {
  label: string;
  value: T;
  options: { value: T; label: ReactNode }[];
  onChange: (v: T) => void;
  hint?: string;
}) {
  const id = useId();
  return (
    <fieldset className="flex flex-col gap-2">
      <legend id={id} className="mb-2 text-sm font-medium text-trim-dim">
        {label}
      </legend>
      <div role="radiogroup" aria-labelledby={id} className="flex flex-wrap gap-1 rounded-lg border border-mortar bg-night p-1">
        {options.map((o) => (
          <button
            key={String(o.value)}
            type="button"
            role="radio"
            aria-checked={o.value === value}
            onClick={() => onChange(o.value)}
            className={`min-h-10 flex-1 rounded-md px-3 text-sm font-semibold transition-colors ${
              o.value === value ? "bg-trafon text-trim" : "text-trim-dim hover:bg-masonry-2 hover:text-trim"
            }`}
          >
            {o.label}
          </button>
        ))}
      </div>
      {hint && <p className="text-xs text-trim-faint">{hint}</p>}
    </fieldset>
  );
}

export function Toggle({ label, hint, checked, onChange }: { label: string; hint?: string; checked: boolean; onChange: (v: boolean) => void }) {
  const id = useId();
  return (
    <div className="flex items-start justify-between gap-4">
      <div>
        <label htmlFor={id} className="font-medium">
          {label}
        </label>
        {hint && <p className="text-sm text-trim-faint">{hint}</p>}
      </div>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative h-7 w-12 shrink-0 rounded-full border transition-colors ${checked ? "border-trafon bg-trafon" : "border-mortar bg-night"}`}
      >
        <span className={`absolute left-0 top-0.5 size-5.5 rounded-full bg-trim transition-transform ${checked ? "translate-x-5.5" : "translate-x-0.5"}`} />
      </button>
    </div>
  );
}

export function Spinner({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-12 text-trim-dim">
      <LoaderCircle className="size-5 animate-spin" aria-hidden />
      <span>{label ?? t("common.loading")}</span>
    </div>
  );
}

/** One sentence for an error: the code's own message when it has one that says more, otherwise its kind's (see errors.ts). */
export function useErrorText() {
  const { t } = useTranslation();
  return (err: unknown): string => {
    const d = describeError(err, { online: typeof navigator === "undefined" || navigator.onLine });
    const general = t(`errors.kind.${d.kind}.body`);
    return d.specific ? t(`errors.${d.code}`, { defaultValue: general }) : general;
  };
}

const errorIcons: Record<ErrorKind, LucideIcon> = {
  offline: WifiOff,
  waking: Hourglass,
  session: LogIn,
  forbidden: Lock,
  notFound: SearchX,
  rateLimited: Timer,
  server: ServerCrash,
  conflict: RefreshCw,
  invalid: CircleAlert,
  unknown: TriangleAlert,
};

/** Kinds that are nobody's fault and pass on their own: shown calm, not alarm-red. */
const calmKinds = new Set<ErrorKind>(["offline", "waking", "session", "rateLimited", "notFound", "forbidden"]);

/**
 * Explains an error: what happened, in words, with an icon, and a way out.
 * - Without `onRetry` (forms): one line, right under the thing that failed.
 * - With `onRetry` (something failed to load): a block with a title, the explanation, and the buttons that can help –
 *   try again, log in, go home, reload, or report the bug.
 * `resource`: this page loads one thing by the id in the address, so a malformed id means "not found".
 */
export function ErrorNotice({ error, onRetry, resource = false }: { error: unknown; onRetry?: () => void; resource?: boolean }) {
  const { t } = useTranslation();
  const online = useOnline();
  const d = describeError(error, { online, resource });
  const Icon = errorIcons[d.kind];
  const body = d.specific ? t(`errors.${d.code}`, { defaultValue: t(`errors.kind.${d.kind}.body`) }) : t(`errors.kind.${d.kind}.body`);
  const tone = calmKinds.has(d.kind) ? "border-mortar bg-masonry" : "border-trafon/60 bg-trafon/15";

  if (!onRetry) {
    return (
      <div role="alert" className={`flex items-start gap-3 rounded-md border px-4 py-3 text-sm text-trim ${tone}`}>
        <Icon className="mt-0.5 size-4 shrink-0 text-trim-dim" aria-hidden />
        <span>{body}</span>
      </div>
    );
  }
  return (
    <div role="alert" className={`flex flex-col gap-4 rounded-lg border p-5 sm:flex-row sm:items-start ${tone}`}>
      <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-night text-trim-dim">
        <Icon className="size-5" aria-hidden />
      </span>
      <div className="flex flex-col gap-3">
        <div>
          <p className="font-display text-xl leading-tight">{t(`errors.kind.${d.kind}.title`)}</p>
          <p className="mt-1 max-w-prose text-sm text-trim-dim">{body}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {d.retry && (
            <Button size="sm" variant="secondary" onClick={onRetry}>
              <RefreshCw className="size-4" aria-hidden />
              {t("common.retry")}
            </Button>
          )}
          {d.action === "login" && (
            <LinkButton size="sm" to="/login">
              {t("errors.action.login")}
            </LinkButton>
          )}
          {d.action === "home" && (
            <LinkButton size="sm" variant="secondary" to="/">
              {t("errors.action.home")}
            </LinkButton>
          )}
          {d.action === "reload" && (
            <Button size="sm" variant="secondary" onClick={() => window.location.reload()}>
              {t("errors.action.reload")}
            </Button>
          )}
          {(d.kind === "server" || d.kind === "unknown") && (
            <LinkButton size="sm" variant="ghost" to="/feedback">
              {t("errors.action.report")}
            </LinkButton>
          )}
        </div>
      </div>
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-lg border border-dashed border-mortar px-6 py-8">
      <h3 className="font-display text-2xl">{title}</h3>
      {children && <p className="max-w-prose text-trim-dim">{children}</p>}
      {action}
    </div>
  );
}

export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  const { t } = useTranslation();
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      onClose={onClose}
      className="m-auto w-[min(92vw,30rem)] rounded-xl border border-mortar bg-masonry p-0 text-trim backdrop:bg-night/80"
    >
      <div className="flex items-center justify-between border-b border-mortar px-5 py-3">
        <h2 className="font-display text-xl">{title}</h2>
        <button onClick={onClose} className="rounded p-1 text-trim-dim hover:text-trim" aria-label={t("common.close")}>
          <X className="size-5" />
        </button>
      </div>
      <div className="p-5">{children}</div>
    </dialog>
  );
}

/** A button that asks for confirmation in a modal before running a (possibly destructive) action. */
export function ConfirmButton({
  label,
  message,
  onConfirm,
  variant = "danger",
  size = "sm",
  busy,
}: {
  label: string;
  message: string;
  onConfirm: () => void;
  variant?: Variant;
  size?: "sm" | "md";
  busy?: boolean;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button variant={variant} size={size} busy={busy} onClick={() => setOpen(true)}>
        {label}
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} title={label}>
        <p className="mb-5 text-trim-dim">{message}</p>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t("common.cancel")}
          </Button>
          <Button
            variant={variant === "danger" ? "primary" : variant}
            onClick={() => {
              setOpen(false);
              onConfirm();
            }}
          >
            {t("common.confirm")}
          </Button>
        </div>
      </Modal>
    </>
  );
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-3 flex items-end justify-between gap-4">
      <h2 className="font-display text-2xl text-trim">{children}</h2>
      {action}
    </div>
  );
}

export function PageHeader({ title, lead, action }: { title: string; lead?: string; action?: ReactNode }) {
  return (
    <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="font-display text-5xl leading-none tracking-tight sm:text-6xl">{title}</h1>
        {lead && <p className="mt-3 max-w-prose text-trim-dim">{lead}</p>}
      </div>
      {action}
    </header>
  );
}
