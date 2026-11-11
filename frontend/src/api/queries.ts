import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client";
import type {
  AdminUserView,
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
};

export const useSocial = (matchId: string) =>
  useQuery({ queryKey: keys.social(matchId), queryFn: () => api.get<SocialView>(`/api/matches/${matchId}/social`) });

export const useBreakdown = (matchId: string, dartCount: number) =>
  useQuery({
    // dartCount in the key keeps the analysis fresh while a match is live
    queryKey: [...keys.breakdown(matchId), dartCount],
    queryFn: () => api.get<MatchBreakdown>(`/api/matches/${matchId}/breakdown`),
    placeholderData: (prev) => prev,
  });

export const useDartStats = (username: string) =>
  useQuery({
    queryKey: keys.dartStats(username),
    queryFn: () => api.get<DartStats>(`/api/users/${encodeURIComponent(username)}/dart-stats`),
  });

export const useMe = () =>
  useQuery({
    queryKey: keys.me,
    queryFn: () => api.get<Me>("/api/auth/me"),
    retry: false,
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
    queryFn: () => api.get<MatchSummary[]>(`/api/matches/recent?limit=${limit}${user ? `&user=${encodeURIComponent(user)}` : ""}`),
  });

export const useMatch = (id: string) =>
  useQuery({ queryKey: keys.match(id), queryFn: () => api.get<LiveMatch>(`/api/matches/${id}`) });

export const useProfile = (username: string) =>
  useQuery({
    queryKey: keys.profile(username),
    queryFn: () => api.get<ProfileStats>(`/api/users/${encodeURIComponent(username)}/stats`),
  });

export const useHeadToHead = (username: string) =>
  useQuery({
    queryKey: keys.h2h(username),
    queryFn: () => api.get<HeadToHeadRow[]>(`/api/users/${encodeURIComponent(username)}/head-to-head`),
  });

export const useTournaments = () =>
  useQuery({ queryKey: keys.tournaments, queryFn: () => api.get<TournamentSummary[]>("/api/tournaments") });

export const useTournament = (id: string) =>
  useQuery({ queryKey: keys.tournament(id), queryFn: () => api.get<TournamentDetail>(`/api/tournaments/${id}`) });

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
