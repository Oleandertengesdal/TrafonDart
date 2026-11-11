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

// Heavier pages (charts, brackets, admin) are split into their own chunks.
const ProfilePage = lazy(() => import("./pages/ProfilePage").then((m) => ({ default: m.ProfilePage })));
const AdminPage = lazy(() => import("./pages/AdminPage").then((m) => ({ default: m.AdminPage })));
const TournamentsPage = lazy(() => import("./pages/TournamentPages").then((m) => ({ default: m.TournamentsPage })));
const NewTournamentPage = lazy(() => import("./pages/TournamentPages").then((m) => ({ default: m.NewTournamentPage })));
const TournamentPage = lazy(() => import("./pages/TournamentPages").then((m) => ({ default: m.TournamentPage })));

/** Public pages get the full app chrome when someone is logged in, and a minimal shell otherwise. */
function MaybeLayout() {
  const { me, loading } = useCurrentUser();
  if (loading) return <Spinner />;
  return me ? <Layout /> : <Outlet />;
}

const L = (node: ReactNode) => <Suspense fallback={<Spinner />}>{node}</Suspense>;

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  { path: "/reset-password", element: <ResetPasswordPage /> },
  { element: <MaybeLayout />, children: [{ path: "/feedback", element: <FeedbackPage /> }] },
  {
    element: <RequireAuth />,
    children: [
      // The match screen is full-bleed (no site chrome) so the scoreboard and keypad get every pixel.
      { path: "/matches/:id", element: <MatchPage /> },
      {
        element: <Layout />,
        children: [
          { path: "/", element: <HomePage /> },
          { path: "/play", element: <NewMatchPage /> },
          { path: "/matches", element: <MatchHistoryPage /> },
          { path: "/players", element: <PlayersPage /> },
          { path: "/players/:username", element: L(<ProfilePage />) },
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
