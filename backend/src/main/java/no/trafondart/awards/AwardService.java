package no.trafondart.awards;

import no.trafondart.awards.AwardEntities.Award;
import no.trafondart.common.ApiException;
import no.trafondart.config.AppProperties;
import no.trafondart.match.LegRecordRepository;
import no.trafondart.match.Match;
import no.trafondart.match.MatchEvents;
import no.trafondart.match.MatchRecords;
import no.trafondart.match.MatchRepository;
import no.trafondart.match.PlayerMatchStatsRepository;
import no.trafondart.rating.RatingHistory;
import no.trafondart.rating.RatingHistoryRepository;
import no.trafondart.social.KudosRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Player of the Month and the monthly category awards.
 *
 * <p>The current month is a live "race" computed on request. Past months are <b>finalised</b> once and stored:
 * by a cron job just after midnight on the 1st, on start-up, and lazily on the first request after a month ends
 * (the free Render tier sleeps, so the cron alone can't be trusted). If an admin voids a match, the affected
 * months are re-decided.
 */
@Service
public class AwardService {

    private static final Logger log = LoggerFactory.getLogger(AwardService.class);

    public record Standing(int rank, PlayerSummary player, boolean eligible, MonthlyScore.Input stats,
                           MonthlyScore.Breakdown points) {
    }

    public record AwardView(String period, Award award, PlayerSummary player, double value) {
    }

    public record CategoryLeader(Award award, PlayerSummary player, double value) {
    }

    public record Race(String period, Instant startsAt, Instant endsAt, int minMatches, List<Standing> standings,
                       List<CategoryLeader> leaders, AwardView reigning) {
    }

    public record MonthResult(String period, List<AwardView> awards) {
    }

    private final MatchRepository matches;
    private final PlayerMatchStatsRepository stats;
    private final LegRecordRepository legs;
    private final RatingHistoryRepository ratingHistory;
    private final KudosRepository kudos;
    private final UserRepository users;
    private final AwardPeriodRepository periods;
    private final MonthlyAwardRepository awards;
    private final AwardYearRepository years;
    private final YearlyAwardRepository yearly;
    private final ZoneId zone;
    private final int minMatches;
    private final Clock clock;

    public AwardService(MatchRepository matches, PlayerMatchStatsRepository stats, LegRecordRepository legs,
                        RatingHistoryRepository ratingHistory, KudosRepository kudos, UserRepository users,
                        AwardPeriodRepository periods, MonthlyAwardRepository awards, AwardYearRepository years,
                        YearlyAwardRepository yearly, AppProperties props) {
        this.matches = matches;
        this.stats = stats;
        this.legs = legs;
        this.ratingHistory = ratingHistory;
        this.kudos = kudos;
        this.users = users;
        this.periods = periods;
        this.awards = awards;
        this.years = years;
        this.yearly = yearly;
        this.zone = ZoneId.of(props.awards().zone());
        this.minMatches = props.awards().minMatches();
        this.clock = Clock.system(zone);
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant start(YearMonth m) {
        return m.atDay(1).atStartOfDay(zone).toInstant();
    }

    // ------------------------------------------------------------------ scoring

    /** Everybody who finished a match in the month, ranked by month points. */
    @Transactional(readOnly = true)
    public List<Standing> standings(YearMonth month) {
        Instant from = start(month);
        Instant to = start(month.plusMonths(1));
        List<Match> played = matches.findRatedCompletedBetween(from, to);
        if (played.isEmpty()) return List.of();
        List<UUID> ids = played.stream().map(Match::getId).toList();
        Map<UUID, Match> byId = played.stream().collect(Collectors.toMap(Match::getId, Function.identity()));

        // per-user accumulators: matches, wins, points, darts, 180s, ton checkouts, highest checkout, kudos
        Map<Long, int[]> acc = new HashMap<>();
        for (MatchRecords.PlayerMatchStats s : stats.findAllByIdMatchIdIn(ids)) {
            Long uid = s.getId().getUserId();
            int[] a = acc.computeIfAbsent(uid, k -> new int[8]);
            a[0]++;
            if (uid.equals(byId.get(s.getId().getMatchId()).getWinnerId())) a[1]++;
            a[2] += s.getPointsScored();
            a[3] += s.getDartsThrown();
            a[4] += s.getCount180();
            a[6] = Math.max(a[6], s.getHighestCheckout());
        }
        for (MatchRecords.LegRecord l : legs.findAllByMatchIdIn(ids)) {
            if (l.getCheckout() >= 100 && acc.containsKey(l.getWinnerId())) acc.get(l.getWinnerId())[5]++;
        }
        for (Object[] row : kudos.countByMatchIds(ids)) {
            Match m = byId.get((UUID) row[0]);
            int n = ((Number) row[1]).intValue();
            for (Long uid : List.of(m.getPlayer1Id(), m.getPlayer2Id())) {
                if (acc.containsKey(uid)) acc.get(uid)[7] += n;
            }
        }

        Map<Long, Double> prior = new HashMap<>();
        for (Object[] row : stats.totalsBefore(from)) {
            long darts = ((Number) row[2]).longValue();
            if (darts > 0) prior.put((Long) row[0], ((Number) row[1]).doubleValue() * 3.0 / darts);
        }

        Map<Long, Double> firstBefore = new LinkedHashMap<>();
        Map<Long, Double> lastAfter = new HashMap<>();
        for (RatingHistory h : ratingHistory.findAllByRatedAtGreaterThanEqualAndRatedAtLessThanOrderByRatedAtAscIdAsc(from, to)) {
            firstBefore.putIfAbsent(h.getUserId(), h.getRatingBefore());
            lastAfter.put(h.getUserId(), h.getRatingAfter());
        }

        Map<Long, User> userById = users.findAllById(acc.keySet()).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<Standing> rows = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : acc.entrySet()) {
            User u = userById.get(e.getKey());
            if (u == null || !u.isEnabled()) continue;
            int[] a = e.getValue();
            Double change = firstBefore.containsKey(e.getKey())
                    ? Math.round((lastAfter.get(e.getKey()) - firstBefore.get(e.getKey())) * 10.0) / 10.0 : null;
            double avg = a[3] == 0 ? 0 : Math.round(a[2] * 300.0 / a[3]) / 100.0;
            Double priorAvg = prior.containsKey(e.getKey()) ? Math.round(prior.get(e.getKey()) * 100.0) / 100.0 : null;
            MonthlyScore.Input in = new MonthlyScore.Input(a[0], a[1], change, avg, priorAvg, a[4], a[5], a[6], a[7]);
            rows.add(new Standing(0, PlayerSummary.of(u), a[0] >= minMatches, in, MonthlyScore.score(in)));
        }
        rows.sort(Comparator.comparing(Standing::eligible).reversed()
                .thenComparing((Standing s) -> s.points().total(), Comparator.reverseOrder())
                .thenComparing((Standing s) -> s.stats().ratingChange() == null ? 0 : s.stats().ratingChange(), Comparator.reverseOrder())
                .thenComparing((Standing s) -> s.stats().matches(), Comparator.reverseOrder())
                .thenComparing(s -> s.player().username()));
        List<Standing> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Standing s = rows.get(i);
            ranked.add(new Standing(i + 1, s.player(), s.eligible(), s.stats(), s.points()));
        }
        return ranked;
    }

    /** Category winners from a standings list. A category is only awarded when someone actually achieved it. */
    static Map<Award, Standing> decide(List<Standing> standings) {
        Map<Award, Standing> out = new EnumMap<>(Award.class);
        List<Standing> eligible = standings.stream().filter(Standing::eligible).toList();
        if (!eligible.isEmpty()) out.put(Award.PLAYER_OF_MONTH, eligible.getFirst());
        best(eligible, s -> s.stats().ratingChange() == null ? 0 : s.stats().ratingChange()).filter(s -> s.stats().ratingChange() != null && s.stats().ratingChange() > 0)
                .ifPresent(s -> out.put(Award.MOST_IMPROVED, s));
        best(eligible, s -> s.stats().matches()).ifPresent(s -> out.put(Award.MOST_ACTIVE, s));
        best(standings, s -> s.stats().count180()).filter(s -> s.stats().count180() > 0).ifPresent(s -> out.put(Award.MOST_180S, s));
        best(standings, s -> s.stats().highestCheckout()).filter(s -> s.stats().highestCheckout() > 0).ifPresent(s -> out.put(Award.HIGHEST_CHECKOUT, s));
        return out;
    }

    /** Highest metric; ties go to the better month-points rank (the list order). */
    private static Optional<Standing> best(List<Standing> list, java.util.function.ToDoubleFunction<Standing> metric) {
        Standing top = null;
        for (Standing s : list) {
            if (top == null || metric.applyAsDouble(s) > metric.applyAsDouble(top)) top = s;
        }
        return Optional.ofNullable(top);
    }

    static double valueFor(Award award, Standing s) {
        return switch (award) {
            case PLAYER_OF_MONTH -> s.points().total();
            case MOST_IMPROVED -> s.stats().ratingChange();
            case MOST_ACTIVE -> s.stats().matches();
            case MOST_180S -> s.stats().count180();
            case HIGHEST_CHECKOUT -> s.stats().highestCheckout();
            case PLAYER_OF_YEAR -> throw new IllegalArgumentException("Player of the Year is not a monthly award");
        };
    }

    // ------------------------------------------------------------------ live race

    @Transactional
    public Race race() {
        ensureFinalized();
        YearMonth now = currentMonth();
        List<Standing> s = standings(now);
        List<CategoryLeader> leaders = decide(s).entrySet().stream()
                .map(e -> new CategoryLeader(e.getKey(), e.getValue().player(), valueFor(e.getKey(), e.getValue())))
                .toList();
        AwardView reigning = monthResult(now.minusMonths(1)).awards().stream()
                .filter(a -> a.award() == Award.PLAYER_OF_MONTH).findFirst().orElse(null);
        return new Race(now.toString(), start(now), start(now.plusMonths(1)), minMatches, s, leaders, reigning);
    }

    // ------------------------------------------------------------------ finalising past months

    /** Decides every finished month that hasn't been decided yet. Idempotent and cheap when up to date. */
    @Transactional
    public synchronized int ensureFinalized() {
        Optional<Instant> first = matches.firstCompletedAt();
        if (first.isEmpty()) return 0;
        YearMonth m = YearMonth.from(first.get().atZone(zone));
        YearMonth current = currentMonth();
        int decided = 0;
        for (; m.isBefore(current); m = m.plusMonths(1)) {
            if (periods.existsById(m.toString())) continue;
            finalizeMonth(m);
            decided++;
        }
        if (decided > 0) log.info("Decided monthly awards for {} month(s)", decided);
        return decided;
    }

    /** Stores the winners of {@code month}. Package-visible for tests. */
    @Transactional
    public void finalizeMonth(YearMonth month) {
        String key = month.toString();
        periods.save(new AwardEntities.Period(key));
        decide(standings(month)).forEach((award, s) ->
                awards.save(new AwardEntities.MonthlyAward(key, award, s.player().id(), valueFor(award, s))));
    }

    /** Forget decisions from {@code month} onwards (ratings after it may have changed too); they're re-decided lazily. */
    @Transactional
    public void invalidateFrom(YearMonth month) {
        awards.deleteFrom(month.toString());
        periods.deleteFrom(month.toString());
        // the year's points are made of month points, so the year is decided again too
        yearly.deleteFrom(month.getYear());
        years.deleteFrom(month.getYear());
    }

    @Transactional
    public int recomputeAll() {
        invalidateFrom(YearMonth.of(2000, 1));
        return ensureFinalized();
    }

    @EventListener
    public void onMatchVoided(MatchEvents.MatchVoided event) {
        Instant at = event.match().getCompletedAt();
        if (at != null) invalidateFrom(YearMonth.from(at.atZone(zone)));
    }

    @Scheduled(cron = "0 5 0 1 * *", zone = "${app.awards.zone:Europe/Oslo}")
    public void monthlyJob() {
        ensureFinalized();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            ensureFinalized();
        } catch (RuntimeException e) {
            log.warn("Could not decide monthly awards on start-up: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ queries

    @Transactional
    public List<MonthResult> hallOfFame(int limit) {
        ensureFinalized();
        List<String> keys = periods.findAllNewestFirst().stream().map(AwardEntities.Period::getPeriod).limit(Math.clamp(limit, 1, 120)).toList();
        Map<String, List<AwardEntities.MonthlyAward>> byPeriod = awards.findByPeriods(keys).stream()
                .collect(Collectors.groupingBy(AwardEntities.MonthlyAward::getPeriod));
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return keys.stream()
                .map(k -> new MonthResult(k, byPeriod.getOrDefault(k, List.of()).stream()
                        .sorted(Comparator.comparing(AwardEntities.MonthlyAward::getAward))
                        .map(a -> view(a, userById)).toList()))
                .toList();
    }

    @Transactional
    public MonthResult monthResult(YearMonth month) {
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return new MonthResult(month.toString(), awards.findByPeriods(List.of(month.toString())).stream()
                .sorted(Comparator.comparing(AwardEntities.MonthlyAward::getAward))
                .map(a -> view(a, userById)).toList());
    }

    @Transactional
    public List<AwardView> badges(String username) {
        ensureFinalized();
        User user = users.findByUsernameIgnoreCase(username).orElseThrow(ApiException::notFound);
        Map<Long, User> self = Map.of(user.getId(), user);
        List<AwardView> out = new java.util.ArrayList<>();
        for (YearEntities.YearlyAward y : yearly.findByUser(user.getId())) {
            out.add(new AwardView(String.valueOf(y.getYear()), Award.PLAYER_OF_YEAR, PlayerSummary.of(user), y.getValue()));
        }
        awards.findByUser(user.getId()).stream().map(a -> view(a, self)).forEach(out::add);
        return out;
    }

    private static AwardView view(AwardEntities.MonthlyAward a, Map<Long, User> userById) {
        User u = userById.get(a.getUserId());
        return new AwardView(a.getPeriod(), a.getAward(), u == null ? null : PlayerSummary.of(u), a.getValue());
    }
}
