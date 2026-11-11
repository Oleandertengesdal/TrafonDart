package no.trafondart.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InviteRedemptionRepository extends JpaRepository<InviteRedemption, Long> {

    List<InviteRedemption> findAllByInviteCodeIdIn(Collection<Long> inviteCodeIds);
}
