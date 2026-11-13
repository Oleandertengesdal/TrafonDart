import { Crown, Home, LogOut, Settings, Shield, Swords, Trophy, Tv, Users, CircleUser, History, WifiOff } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link, NavLink, Outlet, useNavigate } from "react-router";
import { api } from "../api/client";
import { useAccountLanguage, useAuthActions, useCurrentUser } from "../auth/auth";
import i18n, { LANGUAGES, currentLanguage, type LanguageCode } from "../i18n";
import { useOnline } from "../lib/useOnline";
import { Logo } from "./Logo";

export function LanguageSwitch({ persist = true }: { persist?: boolean }) {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const active = currentLanguage();
  const change = (code: LanguageCode) => {
    void i18n.changeLanguage(code);
    if (persist && me) void api.patch("/api/users/me", { preferredLocale: code }).catch(() => undefined);
  };
  return (
    <div role="group" aria-label={t("nav.language")} className="flex rounded-md border border-mortar p-0.5 text-xs font-bold">
      {LANGUAGES.map((l) => (
        <button
          key={l.code}
          type="button"
          lang={l.code}
          aria-pressed={active === l.code}
          title={l.label}
          onClick={() => change(l.code)}
          className={`rounded px-2 py-1 transition-colors ${active === l.code ? "bg-trim text-night" : "text-trim-dim hover:text-trim"}`}
        >
          {l.short}
        </button>
      ))}
    </div>
  );
}

/** Says so when this device loses its connection, so stale numbers aren't mistaken for live ones. */
function OfflineBanner() {
  const { t } = useTranslation();
  const online = useOnline();
  if (online) return null;
  return (
    <div role="status" className="border-t border-mortar bg-masonry">
      <p className="mx-auto flex max-w-6xl items-center gap-2 px-4 py-2 text-sm text-trim-dim">
        <WifiOff className="size-4 shrink-0" aria-hidden />
        {t("offline.banner")}
      </p>
    </div>
  );
}

export function Layout() {
  const { t } = useTranslation();
  const { me } = useCurrentUser();
  const { logout } = useAuthActions();
  const navigate = useNavigate();
  useAccountLanguage(me);

  const links = [
    { to: "/", label: t("nav.home"), icon: Home, end: true },
    { to: "/play", label: t("nav.play"), icon: Swords },
    { to: "/matches", label: t("nav.matches"), icon: History },
    { to: "/players", label: t("nav.players"), icon: Users },
    { to: "/tournaments", label: t("nav.tournaments"), icon: Trophy },
    { to: "/awards", label: t("nav.awards"), icon: Crown },
  ];
  const navClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2 rounded-md px-3 py-2 text-sm font-semibold transition-colors ${
      isActive ? "bg-masonry-2 text-trim" : "text-trim-dim hover:text-trim"
    }`;

  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-trim focus:px-3 focus:py-2 focus:text-night">
        {t("nav.skipToContent")}
      </a>
      <header className="sticky top-0 z-30 border-b border-mortar bg-night/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
          <Link to="/" aria-label={t("nav.home")}>
            <Logo />
          </Link>
          <nav className="ml-4 hidden items-center gap-1 lg:flex" aria-label={t("nav.menu")}>
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.end} className={navClass}>
                {l.label}
              </NavLink>
            ))}
            {me?.role === "ADMIN" && (
              <NavLink to="/admin" className={navClass}>
                {t("nav.admin")}
              </NavLink>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <LanguageSwitch />
            {me && (
              <>
                <Link
                  to={`/players/${me.username}`}
                  className="hidden items-center gap-2 rounded-md px-2 py-2 text-sm text-trim-dim hover:text-trim sm:flex"
                  title={t("nav.profile")}
                >
                  <CircleUser className="size-5" aria-hidden />
                  <span className="font-semibold">{me.username}</span>
                </Link>
                <Link to="/tv" className="hidden rounded-md p-2 text-trim-dim hover:text-trim sm:block" aria-label={t("tv.open")} title={t("tv.open")}>
                  <Tv className="size-5" />
                </Link>
                <Link to="/settings" className="rounded-md p-2 text-trim-dim hover:text-trim" aria-label={t("nav.settings")} title={t("nav.settings")}>
                  <Settings className="size-5" />
                </Link>
                <button
                  onClick={async () => {
                    await logout();
                    navigate("/login");
                  }}
                  className="rounded-md p-2 text-trim-dim hover:text-trim"
                  aria-label={t("nav.logout")}
                  title={t("nav.logout")}
                >
                  <LogOut className="size-5" />
                </button>
              </>
            )}
          </div>
        </div>
        <OfflineBanner />
      </header>

      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 pb-10 pt-8">
        <Outlet />
      </main>

      <footer className="mx-auto mb-24 flex w-full max-w-6xl flex-wrap items-center justify-between gap-3 border-t border-mortar px-4 pt-5 text-sm text-trim-faint lg:mb-8">
        <span>{t("app.name")}</span>
        <Link to="/feedback" className="font-semibold text-trim-dim hover:text-trim">
          {t("feedback.link")}
        </Link>
      </footer>

      {/* Bottom tab bar on phones and tablets in portrait */}
      <nav
        className="fixed inset-x-0 bottom-0 z-30 border-t border-mortar bg-night/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden"
        aria-label={t("nav.menu")}
      >
        <ul className="mx-auto flex max-w-xl">
          {[...links.filter((l) => l.to !== "/matches"), ...(me?.role === "ADMIN" ? [{ to: "/admin", label: t("nav.admin"), icon: Shield, end: false }] : [])].map((l) => (
            <li key={l.to} className="min-w-0 flex-1">
              <NavLink
                to={l.to}
                end={l.end}
                className={({ isActive }) =>
                  `flex flex-col items-center gap-0.5 px-0.5 py-2 text-xs font-semibold ${isActive ? "text-trim" : "text-trim-faint"}`
                }
              >
                {({ isActive }) => (
                  <>
                    <l.icon className={`size-5 ${isActive ? "text-trafon-bright" : ""}`} aria-hidden />
                    <span className="max-w-full truncate">{l.label}</span>
                  </>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
