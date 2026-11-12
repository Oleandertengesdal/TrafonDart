import { lazy, Suspense, type ReactNode } from "react";
import { createBrowserRouter } from "react-router";
import { Outlet } from "react-router";
import { RequireAdmin, RequireAuth, useCurrentUser } from "./auth/auth";
import { Layout } from "./components/Layout";
import { HomePage } from "./pages/HomePage";
import { LoginPage, RegisterPage } from "./pages/AuthPages";
import { MatchPage } from "./pages/MatchPage";
import { MatchHistoryPage } from "./pages/MatchHistoryPage";
import { NewMatchPage } from "./pages/NewMatchPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { PlayersPage } from "./pages/PlayersPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { FeedbackPage } from "./pages/FeedbackPage";
import { SettingsPage } from "./pages/SettingsPage";
import { Spinner } from "./components/ui";
import { RouteError } from "./pages/RouteError";
import { WakingScreen } from "./components/WakingScreen";

// Heavier pages (charts, brackets, admin) are split into their own chunks.
const ProfilePage = lazy(() => import("./pages/ProfilePage").then((m) => ({ default: m.ProfilePage })));
const AdminPage = lazy(() => import("./pages/AdminPage").then((m) => ({ default: m.AdminPage })));
const RecapPage = lazy(() => import("./pages/RecapPage").then((m) => ({ default: m.RecapPage })));
const GamePage = lazy(() => import("./pages/GamePage").then((m) => ({ default: m.GamePage })));
const NewGamePage = lazy(() => import("./pages/NewGamePage").then((m) => ({ default: m.NewGamePage })));
const TvPage = lazy(() => import("./pages/TvPage").then((m) => ({ default: m.TvPage })));
const AwardsPage = lazy(() => import("./pages/AwardsPage").then((m) => ({ default: m.AwardsPage })));
const TournamentsPage = lazy(() => import("./pages/TournamentPages").then((m) => ({ default: m.TournamentsPage })));
const NewTournamentPage = lazy(() => import("./pages/TournamentPages").then((m) => ({ default: m.NewTournamentPage })));
const TournamentPage = lazy(() => import("./pages/TournamentPages").then((m) => ({ default: m.TournamentPage })));

/** Public pages get the full app chrome when someone is logged in, and a minimal shell otherwise. */
function MaybeLayout() {
  const { me, loading } = useCurrentUser();
  if (loading) return <WakingScreen />;
  return me ? <Layout /> : <Outlet />;
}

const L = (node: ReactNode) => <Suspense fallback={<Spinner />}>{node}</Suspense>;

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage />, errorElement: <RouteError /> },
  { path: "/register", element: <RegisterPage />, errorElement: <RouteError /> },
  { path: "/reset-password", element: <ResetPasswordPage />, errorElement: <RouteError /> },
  { element: <MaybeLayout />, errorElement: <RouteError />, children: [{ path: "/feedback", element: <FeedbackPage /> }] },
  {
    element: <RequireAuth />,
    errorElement: <RouteError />,
    children: [
      // The match and game screens are full-bleed (no site chrome) so the scoreboard and keypad get every pixel.
      { path: "/matches/:id", element: <MatchPage />, errorElement: <RouteError /> },
      { path: "/games/:id", element: L(<GamePage />), errorElement: <RouteError /> },
      // TV mode: a read-only scoreboard for a big screen at the venue.
      { path: "/tv", element: L(<TvPage />), errorElement: <RouteError /> },
      {
        element: <Layout />,
        errorElement: <RouteError />,
        children: [
          { path: "/", element: <HomePage /> },
          { path: "/play", element: <NewMatchPage /> },
          { path: "/matches", element: <MatchHistoryPage /> },
          { path: "/players", element: <PlayersPage /> },
          { path: "/players/:username", element: L(<ProfilePage />) },
          { path: "/players/:username/recap", element: L(<RecapPage />) },
          { path: "/games/new", element: L(<NewGamePage />) },
          { path: "/awards", element: L(<AwardsPage />) },
          { path: "/tournaments", element: L(<TournamentsPage />) },
          { path: "/tournaments/new", element: L(<NewTournamentPage />) },
          { path: "/tournaments/:id", element: L(<TournamentPage />) },
          { path: "/settings", element: <SettingsPage /> },
          {
            element: <RequireAdmin />,
            children: [{ path: "/admin", element: L(<AdminPage />) }],
          },
          { path: "*", element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
