package no.trafondart.bot;

import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import no.trafondart.match.MatchViews;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Practice matches against Trafo, and each player's record against it. */
@RestController
public class BotController {

    private final BotMatchService service;

    public BotController(BotMatchService service) {
        this.service = service;
    }

    @PostMapping("/api/bot-matches")
    @ResponseStatus(HttpStatus.CREATED)
    public BotViews.BotMatchView create(@Valid @RequestBody BotViews.CreateBotMatchRequest req) {
        return service.create(AuthUser.current(), req);
    }

    @GetMapping("/api/bot-matches/{id}")
    public BotViews.BotMatchView get(@PathVariable UUID id) {
        return service.get(id, AuthUser.current());
    }

    @PostMapping("/api/bot-matches/{id}/throws")
    public BotViews.BotMatchView throwDart(@PathVariable UUID id, @Valid @RequestBody MatchViews.ThrowRequest req) {
        return service.throwDart(id, AuthUser.current(), req);
    }

    @PostMapping("/api/bot-matches/{id}/undo")
    public BotViews.BotMatchView undo(@PathVariable UUID id, @Valid @RequestBody MatchViews.UndoRequest req) {
        return service.undo(id, AuthUser.current(), req);
    }

    /** 200 with the match when it counts as a loss; 204 when nothing was thrown and it was simply removed. */
    @PostMapping("/api/bot-matches/{id}/forfeit")
    public ResponseEntity<BotViews.BotMatchView> forfeit(@PathVariable UUID id) {
        BotViews.BotMatchView view = service.forfeit(id, AuthUser.current());
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    @GetMapping("/api/users/{username}/bot-record")
    public BotViews.BotRecord record(@PathVariable String username) {
        return service.record(username, AuthUser.current());
    }
}
