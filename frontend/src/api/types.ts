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
  /** The member deleted their account: shown as "Deleted player", never linked. */
  deleted?: boolean;
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
  deleted: boolean;
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

// ------------------------------------------------------------------ monthly awards

export type AwardType = "PLAYER_OF_MONTH" | "MOST_IMPROVED" | "MOST_ACTIVE" | "MOST_180S" | "HIGHEST_CHECKOUT" | "PLAYER_OF_YEAR";

export interface AwardView {
  period: string; // YYYY-MM, or YYYY for Player of the Year
  award: AwardType;
  player: PlayerSummary | null;
  value: number;
}

export interface Standing {
  rank: number;
  player: PlayerSummary;
  eligible: boolean;
  stats: {
    matches: number;
    wins: number;
    ratingChange: number | null;
    monthAverage: number;
    priorAverage: number | null;
    count180: number;
    tonCheckouts: number;
    highestCheckout: number;
    kudos: number;
  };
  points: { rating: number; activity: number; wins: number; improvement: number; highlights: number; kudos: number; total: number };
}

export interface AwardRace {
  period: string;
  startsAt: string;
  endsAt: string;
  minMatches: number;
  standings: Standing[];
  leaders: { award: AwardType; player: PlayerSummary; value: number }[];
  reigning: AwardView | null;
}

export interface MonthResult {
  period: string;
  awards: AwardView[];
}

// ---------------------------------------------------------------------------------------------- 1.4.0

export interface YearStanding {
  rank: number;
  player: PlayerSummary;
  eligible: boolean;
  matches: number;
  points: number;
  monthsPlayed: number;
  monthsWon: number;
}

export interface YearRace {
  year: number;
  minMatches: number;
  standings: YearStanding[];
  reigning: AwardView | null;
}

export interface YearResult {
  year: number;
  award: AwardView | null;
}

export type GameType = "X01" | "CRICKET" | "KILLER" | "AROUND_THE_CLOCK";
export type GameStatus = "IN_PROGRESS" | "COMPLETED" | "ABANDONED";

export interface PartySettings {
  startScore: number | null;
  inRule: "STRAIGHT" | "DOUBLE" | null;
  outRule: "STRAIGHT" | "DOUBLE" | "MASTER" | null;
  legsToWin: number | null;
  lives: number | null;
  multiplierSkips: boolean | null;
  roundLimit: number | null;
  killerNumbers: number[] | null;
}

export interface PlayerSlot {
  slot: number;
  side: number;
  player: PlayerSummary;
}

export interface SideState {
  side: number;
  legsWon: number;
  remaining: number | null;
  opened: boolean | null;
  marks: Record<string, number> | null;
  points: number | null;
  number: number | null;
  lives: number | null;
  killer: boolean | null;
  out: boolean | null;
  target: number | null;
  finished: boolean | null;
}

export interface SlotStats {
  slot: number;
  side: number;
  darts: number;
  /** X01 points, Cricket marks, Killer lives taken, Around the Clock targets hit */
  score: number;
  count180: number;
  highestCheckout: number;
  placement: number;
}

export interface GameView {
  id: string;
  type: GameType;
  teams: boolean;
  rated: false;
  status: GameStatus;
  settings: PartySettings;
  players: PlayerSlot[];
  sides: SideState[];
  slots: SlotStats[];
  currentSlot: number;
  leg: number;
  round: number;
  turnDarts: string[];
  checkout: string[] | null;
  lastDart: string | null;
  winnerSide: number | null;
  dartCount: number;
  createdBy: number;
  createdAt: string;
  completedAt: string | null;
}

export interface GameSummary {
  id: string;
  type: GameType;
  teams: boolean;
  status: GameStatus;
  players: PlayerSlot[];
  winnerSide: number | null;
  createdAt: string;
  completedAt: string | null;
}

export interface TypeStats {
  type: GameType;
  played: number;
  won: number;
  darts: number;
  average: number | null;
  best: number | null;
}

export interface GameStats {
  player: PlayerSummary;
  types: TypeStats[];
}

export type AchievementCode =
  | "FIRST_WIN" | "MATCHES_10" | "MATCHES_50" | "MATCHES_100" | "WINS_25" | "STREAK_5" | "FIRST_180" | "MAX_COLLECTOR"
  | "TON_PLUS_CHECKOUT" | "BIG_FISH" | "BULL_FINISH" | "SHORT_LEG" | "NINE_DARTER" | "AVERAGE_50" | "AVERAGE_70"
  | "GIANT_KILLER" | "TOURNAMENT_WIN" | "PLAYER_OF_MONTH" | "PLAYER_OF_YEAR" | "CRICKET_WIN" | "KILLER_WIN"
  | "CLOCK_UNDER_40" | "DARTS_10K";

export interface Achievement {
  code: AchievementCode;
  unlocked: boolean;
  unlockedAt: string | null;
  matchId: string | null;
  gameId: string | null;
  progress: number | null;
  target: number | null;
}

export interface Cabinet {
  unlocked: number;
  total: number;
  items: Achievement[];
}

export interface MatchHighlight {
  matchId: string;
  opponent: PlayerSummary | null;
  won: boolean;
  average: number;
  opponentRating: number | null;
  at: string;
}

export interface Rival {
  player: PlayerSummary;
  played: number;
  won: number;
  lost: number;
}

export interface Recap {
  year: number;
  soFar: boolean;
  player: PlayerSummary;
  matches: { played: number; won: number; lost: number };
  legsWon: number;
  legsPlayed: number;
  dartsThrown: number;
  average: number;
  count180: number;
  count140: number;
  count100: number;
  highestCheckout: number;
  highestTurn: number;
  bestMatch: MatchHighlight | null;
  biggestWin: MatchHighlight | null;
  nemesis: Rival | null;
  favouriteVictim: Rival | null;
  favouriteFinish: { label: string; count: number } | null;
  favouriteNumber: { label: string; count: number } | null;
  longestWinStreak: number;
  busiestWeekday: number | null;
  bestMonth: { period: string; points: number; rank: number } | null;
  monthsWon: number;
  yearRank: number | null;
  yearPoints: number | null;
  playerOfTheYear: boolean;
  games: { played: number; won: number; byType: { type: GameType; played: number; won: number }[] };
  achievements: AchievementCode[];
}

export type ChallengeStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "CANCELLED" | "EXPIRED" | "STARTED";

export interface ChallengeView {
  id: number;
  challenger: PlayerSummary;
  opponent: PlayerSummary;
  startScore: number;
  inRule: "STRAIGHT" | "DOUBLE";
  outRule: "STRAIGHT" | "DOUBLE" | "MASTER";
  legsToWin: number;
  rated: boolean;
  message: string | null;
  status: ChallengeStatus;
  matchId: string | null;
  createdAt: string;
  expiresAt: string;
  respondedAt: string | null;
}

export interface ChallengeList {
  incoming: ChallengeView[];
  outgoing: ChallengeView[];
}

// ------------------------------------------------------------------ Trafo, the practice bot

export type BotLevel = "BEGINNER" | "CASUAL" | "CLUB" | "LEAGUE" | "PRO";
export type BotMatchStatus = "IN_PROGRESS" | "COMPLETED" | "FORFEITED";

/** A practice match: the same scoreboard as a match (you are player 0, Trafo player 1), plus Trafo's latest turns. */
export interface BotMatch extends Omit<LiveMatch, "status"> {
  status: BotMatchStatus;
  level: BotLevel;
  typicalAverage: number;
  /** Your latest turn, finished or not (null before your first dart). */
  playerTurn: TurnView | null;
  /** Trafo's turns since your last dart, to show dart by dart. */
  botTurns: TurnView[];
}

export interface BotRecord {
  player: PlayerSummary;
  played: number;
  won: number;
  lost: number;
  winRate: number;
  currentStreak: number;
  bestStreak: number;
  hardestBeaten: BotLevel | null;
  levels: { level: BotLevel; typicalAverage: number; played: number; won: number }[];
  throwing: {
    average: number;
    first9Average: number;
    bestAverage: number;
    count180: number;
    count140: number;
    count100: number;
    highestTurn: number;
    highestCheckout: number;
    checkoutAttempts: number;
    checkoutsHit: number;
    checkoutPercentage: number;
    trafoAverage: number;
  };
  recent: {
    id: string;
    level: BotLevel;
    status: BotMatchStatus;
    won: boolean;
    legsWon: number;
    legsLost: number;
    average: number;
    trafoAverage: number;
    startScore: number;
    completedAt: string;
  }[];
  /** Your own unfinished match against Trafo; always null on someone else's record. */
  openMatchId: string | null;
}
