package no.trafondart.realtime;

import no.trafondart.match.MatchEvents;
import no.trafondart.social.SocialService;
import no.trafondart.tournament.TournamentService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * Pushes changes to subscribers only after the transaction commits, so nobody ever sees a state that was rolled back.
 *
 * <ul>
 *   <li>{@code /topic/matches/{id}} – the full live scoreboard after every dart</li>
 *   <li>{@code /topic/live} – "something changed" ping for the home feed</li>
 *   <li>{@code /topic/tournaments/{id}} – "refetch" ping for brackets and tables</li>
 * </ul>
 */
@Component
public class Broadcaster {

    public record FeedPing(String matchId) {
    }

    public record TournamentPing(String tournamentId) {
    }

    public record SocialPing(String matchId) {
    }

    private final SimpMessagingTemplate template;

    public Broadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchUpdated(MatchEvents.MatchUpdated event) {
        template.convertAndSend("/topic/matches/" + event.view().id(), (Object) event.view());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedChanged(MatchEvents.FeedChanged event) {
        template.convertAndSend("/topic/live", (Object) new FeedPing(event.matchId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTournamentUpdated(TournamentService.TournamentUpdated event) {
        template.convertAndSend("/topic/tournaments/" + event.tournamentId(),
                (Object) new TournamentPing(event.tournamentId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSocialChanged(SocialService.SocialChanged event) {
        template.convertAndSend("/topic/matches/" + event.matchId() + "/social", (Object) new SocialPing(event.matchId().toString()));
    }
}
