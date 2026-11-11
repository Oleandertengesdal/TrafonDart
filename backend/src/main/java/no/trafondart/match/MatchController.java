package no.trafondart.match;

import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService service;

    public MatchController(MatchService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchViews.LiveMatchView create(@Valid @RequestBody MatchViews.CreateMatchRequest req) {
        return service.create(AuthUser.current(), req);
    }

    @GetMapping("/live")
    public List<MatchViews.MatchSummary> live() {
        return service.live();
    }

    @GetMapping("/recent")
    public List<MatchViews.MatchSummary> recent(@RequestParam(required = false) String user,
                                                @RequestParam(defaultValue = "20") int limit) {
        return service.recent(user, limit);
    }

    @GetMapping("/{id}")
    public MatchViews.LiveMatchView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/throws")
    public MatchViews.LiveMatchView throwDart(@PathVariable UUID id, @Valid @RequestBody MatchViews.ThrowRequest req) {
        return service.throwDart(id, AuthUser.current(), req);
    }

    @PostMapping("/{id}/undo")
    public MatchViews.LiveMatchView undo(@PathVariable UUID id, @Valid @RequestBody MatchViews.UndoRequest req) {
        return service.undo(id, AuthUser.current(), req);
    }

    @PostMapping("/{id}/abandon")
    public MatchViews.LiveMatchView abandon(@PathVariable UUID id) {
        return service.abandon(id, AuthUser.current());
    }

    @PostMapping("/{id}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public MatchViews.DisputeView dispute(@PathVariable UUID id, @Valid @RequestBody MatchViews.DisputeRequest req) {
        return service.raiseDispute(id, AuthUser.current(), req);
    }
}
