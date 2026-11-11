// TypeScript mirror of the backend DTOs (see backend/src/main/java/no/trafondart/**/…Views.java and docs/API.md).

export type Role = "USER" | "ADMIN";
export type InRule = "STRAIGHT" | "DOUBLE";
export type OutRule = "STRAIGHT" | "DOUBLE" | "MASTER";
export type MatchStatus = "IN_PROGRESS" | "COMPLETED" | "ABANDONED" | "VOIDED";
export type Locale = "nb" | "en";

export interface PlayerSummary {
  id: number;
  username: string;
  realName: string;
  rating: number;
  rd: number;
  provisional: boolean;
}

export interface Me extends Omit<PlayerSummary, "rd"> {
  role: Role;
  preferredLocale: Locale | null;
  rd: number;
}

export interface ApiErrorBody {
  code: string;
  status: number;
  fieldErrors?: { field: string; code: string }[];
  details?: unknown;
}

export interface ConfigView {
  startScore: number;
  inRule: InRule;
  outRule: OutRule;
  legsToWin: number;
}

export interface DartView {
  segment: number;
  multiplier: number;
  label: string;
  points: number;
}

export interface TurnView {
  playerIndex: number;
  darts: DartView[];
  total: number;
  bust: boolean;
  checkout: boolean;
}

export interface LivePlayerStats {
  average: number;
  first9Average: number;
  dartsThrown: number;
  pointsScored: number;
  count180: number;
  count140: number;
  count100: number;
  highestTurn: number;
  highestCheckout: number;
  checkoutAttempts: number;
  checkoutsHit: number;
  checkoutPercentage: number;
}

export interface LegView {
  legNo: number;
  starterIndex: number;
  winnerIndex: number | null;
  winnerDarts: number;
  checkout: number | null;
}

export type MatchEvent = "NONE" | "DART" | "BUST" | "LEG_WON" | "MATCH_WON";

export interface LiveMatch {
  id: string;
  status: MatchStatus;
  config: ConfigView;
  rated: boolean;
  tournament: { id: string; name: string } | null;
  players: [PlayerSummary, PlayerSummary];
  scorerIds: number[];
  currentLeg: number;
  currentPlayerIndex: number;
  legsWon: [number, number];
  remaining: [number, number];
  opened: [boolean, boolean];
  currentTurn: TurnView | null;
  lastCompletedTurn: TurnView | null;
  turnJustEnded: boolean;
  stats: [LivePlayerStats, LivePlayerStats];
  checkout: string[] | null;
  dartsLeftInTurn: number;
  lastEvent: MatchEvent;
  winnerIndex: number | null;
  dartCount: number;
  legs: LegView[];
  createdAt: string;
  completedAt: string | null;
  disputed: boolean;
}

export interface MatchSummary {
  id: string;
  status: MatchStatus;
  config: ConfigView;
  rated: boolean;
  players: [PlayerSummary, PlayerSummary];
  legsWon: [number, number];
  averages: [number, number];
  winnerIndex: number | null;
  createdAt: string;
  completedAt: string | null;
  tournament: { id: string; name: string } | null;
  kudos: number;
  comments: number;
}

export interface RatingPoint {
  at: string;
  rating: number;
  rd: number;
  matchId: string;
  opponent: string | null;
  won: boolean;
}

export interface AveragePoint {
  at: string;
  matchId: string;
  average: number;
  first9Average: number;
  won: boolean;
}

export interface ProfileStats {
  player: PlayerSummary;
  memberSince: string;
  matchesPlayed: number;
  matchesWon: number;
  matchesLost: number;
  winRate: number;
  legsWon: number;
  legsLost: number;
  legWinRate: number;
  average: number;
  first9Average: number;
  bestMatchAverage: number;
  count180: number;
  count140: number;
  count100: number;
  highestTurn: number;
  highestCheckout: number;
  checkoutAttempts: number;
  checkoutsHit: number;
  checkoutPercentage: number;
  peakRating: number;
  recentForm: ("W" | "L")[];
  currentStreak: number;
  ratingHistory: RatingPoint[];
  averageTrend: AveragePoint[];
}

export interface HeadToHeadRow {
  opponent: PlayerSummary;
  played: number;
  won: number;
  lost: number;
  legsWon: number;
  legsLost: number;
  myAverage: number;
  theirAverage: number;
  lastPlayed: string | null;
}

export interface LeaderboardRow {
  rank: number;
  player: PlayerSummary;
  played: number;
  won: number;
  winRate: number;
  average: number;
  count180: number;
  highestCheckout: number;
}

export type TournamentFormat = "ROUND_ROBIN" | "SINGLE_ELIMINATION";
export type TournamentStatus = "DRAFT" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
export type FixtureStatus = "PENDING" | "READY" | "IN_PROGRESS" | "COMPLETED" | "BYE";

export interface TournamentSummary {
  id: string;
  name: string;
  format: TournamentFormat;
  status: TournamentStatus;
  config: ConfigView;
  rated: boolean;
  participantCount: number;
  organiser: PlayerSummary | null;
  winner: PlayerSummary | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface FixtureView {
  id: number;
  round: number;
  position: number;
  player1: PlayerSummary | null;
  player2: PlayerSummary | null;
  matchId: string | null;
  matchStatus: MatchStatus | null;
  winnerSlot: 1 | 2 | null;
  status: FixtureStatus;
  legsWon: [number, number] | null;
}

export interface StandingRow {
  player: PlayerSummary;
  played: number;
  won: number;
  lost: number;
  legsFor: number;
  legsAgainst: number;
  legDiff: number;
  points: number;
}

export interface TournamentDetail {
  summary: TournamentSummary;
  organiserId: number;
  participants: { player: PlayerSummary; seed: number }[];
  fixtures: FixtureView[];
  standings: StandingRow[];
  rounds: number;
}

export type InviteStatus = "ACTIVE" | "EXPIRED" | "USED_UP" | "REVOKED";

export interface InviteView {
  id: number;
  hint: string;
  maxUses: number;
  uses: number;
  expiresAt: string;
  status: InviteStatus;
  note: string | null;
  createdBy: string | null;
  createdAt: string;
  usedBy: string[];
}

export interface AdminUserView {
  id: number;
  username: string;
  realName: string;
  role: Role;
  enabled: boolean;
  rating: number;
  createdAt: string;
}

export interface DisputeView {
  id: number;
  matchId: string;
  raisedBy: string | null;
  reason: string;
  status: "OPEN" | "REJECTED" | "UPHELD";
  resolvedBy: string | null;
  resolutionNote: string | null;
  createdAt: string;
  resolvedAt: string | null;
  match: MatchSummary | null;
}

// ------------------------------------------------------------------ social

export interface KudosView {
  count: number;
  mine: boolean;
  canGive: boolean;
  givers: PlayerSummary[];
}

export interface CommentView {
  id: number;
  author: PlayerSummary | null;
  body: string;
  deleted: boolean;
  createdAt: string;
  editedAt: string | null;
  likes: number;
  likedByMe: boolean;
  likedBy: PlayerSummary[];
  canEdit: boolean;
  canDelete: boolean;
  replies: CommentView[];
}

export interface SocialView {
  matchId: string;
  kudos: KudosView;
  commentCount: number;
  comments: CommentView[];
}

// ------------------------------------------------------------------ analysis

export interface HeatCell {
  segment: number;
  multiplier: number;
  count: number;
}

export interface Band {
  label: string;
  count: number;
}

export interface LabelCount {
  label: string;
  count: number;
}

export interface TurnRow {
  playerIndex: number;
  turnInLeg: number;
  darts: string[];
  scored: number;
  remainingAfter: number;
  bust: boolean;
  checkout: boolean;
}

export interface LegBreakdown {
  legNo: number;
  starterIndex: number;
  winnerIndex: number | null;
  dartsUsed: [number, number];
  averages: [number, number];
  turns: TurnRow[];
}

export interface PlayerAnalysis {
  heatmap: HeatCell[];
  bands: Band[];
  trebleRate: number;
  doubleRate: number;
  missRate: number;
  busts: number;
  bestLeg: number | null;
  finishes: LabelCount[];
}

export interface MatchBreakdown {
  matchId: string;
  winProbability: [number, number];
  legs: LegBreakdown[];
  players: [PlayerAnalysis, PlayerAnalysis];
}

export interface DartStats {
  dartsThrown: number;
  pointsScored: number;
  turns: number;
  legsPlayed: number;
  legsWon: number;
  trebleRate: number;
  doubleRate: number;
  bullRate: number;
  missRate: number;
  busts: number;
  bustRate: number;
  bestLeg: number | null;
  avgDartsPerLegWon: number;
  hold: { won: number; of: number };
  breaks: { won: number; of: number };
  mostCommonTurn: number | null;
  favouriteSegment: number | null;
  bands: Band[];
  heatmap: HeatCell[];
  finishes: LabelCount[];
  topCheckouts: { score: number; finisher: string; matchId: string; at: string }[];
  kudosReceived: number;
}
