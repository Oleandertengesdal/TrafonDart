import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiPath, shouldRetry } from "./client";
import type {
  AdminUserView,
  AwardRace,
  AwardView,
  MonthResult,
  DartStats,
  MatchBreakdown,
  SocialView,
  DisputeView,
  HeadToHeadRow,
  InviteView,
  LeaderboardRow,
  LiveMatch,
  Me,
  MatchSummary,
  PlayerSummary,
  ProfileStats,
  TournamentDetail,
  TournamentSummary,
  YearRace,
  YearResult,
  GameView,
  GameSummary,
  GameStats,
  Cabinet,
  Recap,
  ChallengeList,
} from "./types";

export const keys = {
  me: ["me"] as const,
  players: ["players"] as const,
  leaderboard: ["leaderboard"] as const,
  liveMatches: ["matches", "live"] as const,
  recentMatches: (user?: string) => ["matches", "recent", user ?? "all"] as const,
  match: (id: string) => ["match", id] as const,
  profile: (u: string) => ["profile", u.toLowerCase()] as const,
  h2h: (u: string) => ["h2h", u.toLowerCase()] as const,
  tournaments: ["tournaments"] as const,
  tournament: (id: string) => ["tournament", id] as const,
  adminUsers: ["admin", "users"] as const,
  invites: ["admin", "invites"] as const,
  disputes: ["admin", "disputes"] as const,
  social: (id: string) => ["social", id] as const,
  breakdown: (id: string) => ["breakdown", id] as const,
  dartStats: (u: string) => ["dart-stats", u.toLowerCase()] as const,
  awardRace: ["awards", "current"] as const,
  hallOfFame: ["awards", "hall"] as const,
  badges: (u: string) => ["awards", "badges", u.toLowerCase()] as const,
  yearRace: ["awards", "year"] as const,
  years: ["awards", "years"] as const,
  game: (id: string) => ["game", id] as const,
  liveGames: ["games", "live"] as const,
  recentGames: (user?: string) => ["games", "recent", user ?? "all"] as const,
  gameStats: (u: string) => ["game-stats", u.toLowerCase()] as const,
  achievements: (u: string) => ["achievements", u.toLowerCase()] as const,
  recap: (u: string, year: number) => ["recap", u.toLowerCase(), year] as const,
  challenges: ["challenges"] as const,
};

export const useYearRace = () =>
  useQuery({ queryKey: keys.yearRace, queryFn: () => api.get<YearRace>("/api/awards/year/current"), staleTime: 60_000 });

export const useYears = () => useQuery({ queryKey: keys.years, queryFn: () => api.get<YearResult[]>("/api/awards/years") });

export const useGame = (id: string) =>
  useQuery({ queryKey: keys.game(id), queryFn: () => api.get<GameView>(apiPath`/api/games/${id}`) });

export const useLiveGames = () =>
  useQuery({ queryKey: keys.liveGames, queryFn: () => api.get<GameSummary[]>("/api/games/live"), refetchInterval: 60_000 });

export const useRecentGames = (user?: string, limit = 10) =>
  useQuery({
    queryKey: [...keys.recentGames(user), limit],
    queryFn: () => api.get<GameSummary[]>(user ? apiPath`/api/games/recent?limit=${limit}&user=${user}` : apiPath`/api/games/recent?limit=${limit}`),
  });

export const useGameStats = (username: string) =>
  useQuery({ queryKey: keys.gameStats(username), queryFn: () => api.get<GameStats>(apiPath`/api/users/${username}/game-stats`) });

export const useAchievements = (username: string) =>
  useQuery({ queryKey: keys.achievements(username), queryFn: () => api.get<Cabinet>(apiPath`/api/users/${username}/achievements`) });

export const useRecap = (username: string, year: number) =>
  useQuery({ queryKey: keys.recap(username, year), queryFn: () => api.get<Recap>(apiPath`/api/users/${username}/recap?year=${year}`) });

/** Challenges are checked every 30 s so an invite shows up on the other tablet without a reload. */
export const useChallenges = (enabled = true) =>
  useQuery({ queryKey: keys.challenges, queryFn: () => api.get<ChallengeList>("/api/challenges"), refetchInterval: 30_000, enabled });

export const useAwardRace = () =>
  useQuery({ queryKey: keys.awardRace, queryFn: () => api.get<AwardRace>("/api/awards/current"), staleTime: 60_000 });

export const useHallOfFame = () =>
  useQuery({ queryKey: keys.hallOfFame, queryFn: () => api.get<MonthResult[]>("/api/awards?limit=24") });

export const useBadges = (username: string) =>
  useQuery({ queryKey: keys.badges(username), queryFn: () => api.get<AwardView[]>(apiPath`/api/users/${username}/awards`) });

export const useSocial = (matchId: string) =>
  useQuery({ queryKey: keys.social(matchId), queryFn: () => api.get<SocialView>(apiPath`/api/matches/${matchId}/social`) });

export const useBreakdown = (matchId: string, dartCount: number) =>
  useQuery({
    // dartCount in the key keeps the analysis fresh while a match is live
    queryKey: [...keys.breakdown(matchId), dartCount],
    queryFn: () => api.get<MatchBreakdown>(apiPath`/api/matches/${matchId}/breakdown`),
    placeholderData: (prev) => prev,
  });

export const useDartStats = (username: string) =>
  useQuery({
    queryKey: keys.dartStats(username),
    queryFn: () => api.get<DartStats>(apiPath`/api/users/${username}/dart-stats`),
  });

export const useMe = () =>
  useQuery({
    queryKey: keys.me,
    queryFn: () => api.get<Me>("/api/auth/me"),
    // A sleeping server is retried (the "waking up" screen shows meanwhile); 401 means logged out, no retry.
    retry: shouldRetry,
    staleTime: 5 * 60_000,
  });

export const usePlayers = () =>
  useQuery({ queryKey: keys.players, queryFn: () => api.get<PlayerSummary[]>("/api/users") });

export const useLeaderboard = () =>
  useQuery({ queryKey: keys.leaderboard, queryFn: () => api.get<LeaderboardRow[]>("/api/users/leaderboard") });

export const useLiveMatches = () =>
  useQuery({ queryKey: keys.liveMatches, queryFn: () => api.get<MatchSummary[]>("/api/matches/live"), refetchInterval: 60_000 });

export const useRecentMatches = (user?: string, limit = 20) =>
  useQuery({
    queryKey: [...keys.recentMatches(user), limit],
    queryFn: () => api.get<MatchSummary[]>(user ? apiPath`/api/matches/recent?limit=${limit}&user=${user}` : apiPath`/api/matches/recent?limit=${limit}`),
  });

export const useMatch = (id: string) =>
  useQuery({ queryKey: keys.match(id), queryFn: () => api.get<LiveMatch>(apiPath`/api/matches/${id}`) });

export const useProfile = (username: string) =>
  useQuery({
    queryKey: keys.profile(username),
    queryFn: () => api.get<ProfileStats>(apiPath`/api/users/${username}/stats`),
  });

export const useHeadToHead = (username: string) =>
  useQuery({
    queryKey: keys.h2h(username),
    queryFn: () => api.get<HeadToHeadRow[]>(apiPath`/api/users/${username}/head-to-head`),
  });

export const useTournaments = () =>
  useQuery({ queryKey: keys.tournaments, queryFn: () => api.get<TournamentSummary[]>("/api/tournaments") });

export const useTournament = (id: string) =>
  useQuery({ queryKey: keys.tournament(id), queryFn: () => api.get<TournamentDetail>(apiPath`/api/tournaments/${id}`) });

export const useAdminUsers = () =>
  useQuery({ queryKey: keys.adminUsers, queryFn: () => api.get<AdminUserView[]>("/api/admin/users") });

export const useInvites = () =>
  useQuery({ queryKey: keys.invites, queryFn: () => api.get<InviteView[]>("/api/admin/invites") });

export const useDisputes = () =>
  useQuery({ queryKey: keys.disputes, queryFn: () => api.get<DisputeView[]>("/api/admin/disputes") });

/** Generic mutation that invalidates the given query keys when it succeeds. */
export function useApiMutation<TVars, TResult>(
  fn: (vars: TVars) => Promise<TResult>,
  invalidate: readonly (readonly unknown[])[] = [],
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      invalidate.forEach((key) => qc.invalidateQueries({ queryKey: key }));
    },
  });
}
