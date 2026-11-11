package no.trafondart.tournament;

import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService service;

    public TournamentController(TournamentService service) {
        this.service = service;
    }

    @GetMapping
    public List<TournamentViews.TournamentSummary> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TournamentViews.TournamentDetail create(@Valid @RequestBody TournamentViews.CreateTournamentRequest req) {
        return service.create(AuthUser.current(), req);
    }

    @GetMapping("/{id}")
    public TournamentViews.TournamentDetail get(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PutMapping("/{id}/participants")
    public TournamentViews.TournamentDetail participants(@PathVariable UUID id,
                                                         @Valid @RequestBody TournamentViews.ParticipantsRequest req) {
        return service.updateParticipants(id, AuthUser.current(), req.participantIds());
    }

    @PostMapping("/{id}/start")
    public TournamentViews.TournamentDetail start(@PathVariable UUID id) {
        return service.start(id, AuthUser.current());
    }

    @PostMapping("/{id}/cancel")
    public TournamentViews.TournamentDetail cancel(@PathVariable UUID id) {
        return service.cancel(id, AuthUser.current());
    }

    @PostMapping("/{id}/fixtures/{fixtureId}/start")
    public TournamentViews.StartedFixture startFixture(@PathVariable UUID id, @PathVariable Long fixtureId) {
        return service.startFixture(id, fixtureId, AuthUser.current());
    }
}
