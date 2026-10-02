package dev.justine.proxyvote.voting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.justine.proxyvote.audit.AuditService;
import dev.justine.proxyvote.common.ConflictException;
import dev.justine.proxyvote.config.Tenant;
import dev.justine.proxyvote.meeting.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoteServiceTest {
    @Mock ProposalRepository proposals;
    @Mock VoteRepository votes;
    @Mock RecommendationRepository recommendations;
    @Mock Tenant tenant;
    @Mock AuditService audit;

    private final Instant deadline = Instant.parse("2026-11-10T21:00:00Z");
    private Organization org;
    private Proposal proposal;

    @BeforeEach
    void setUp() {
        org = new Organization("STEWARD", "Stewardship");
        ReflectionTestUtils.setField(org, "id", 1L);
        Meeting meeting = new Meeting("M1", new Company("ACME", "Acme", "US"));
        meeting.schedule(LocalDate.of(2026, 11, 12), deadline, "America/New_York", "AGM", deadline);
        proposal = meeting.upsertProposal(1);
        proposal.update(ProposalCategory.AUDITOR, "Auditor", null, VoteDecision.FOR, null, null);
        ReflectionTestUtils.setField(proposal, "id", 10L);
        when(tenant.current()).thenReturn(org);
        when(proposals.findWithMeetingById(10L)).thenReturn(Optional.of(proposal));
    }

    private VoteService at(Instant now) {
        return new VoteService(proposals, votes, recommendations, tenant, audit, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void rejectsVotesAtOrAfterTheDeadline() {
        assertThatThrownBy(() -> at(deadline).cast(10L, VoteDecision.FOR, null)).isInstanceOf(ConflictException.class);
        verify(votes, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void acceptsAVoteJustBeforeTheDeadlineAndAuditsIt() {
        when(votes.findByOrganizationIdAndProposalId(1L, 10L)).thenReturn(Optional.empty());
        at(deadline.minusSeconds(1)).cast(10L, VoteDecision.AGAINST, null);
        verify(votes).saveAndFlush(any(Vote.class));
        verify(audit).record(eq(org), eq("VOTE_CAST"), eq("Proposal"), eq(10L), any());
    }

    @Test
    void staleVersionIsAConflict() {
        Vote existing = new Vote(org, proposal);
        existing.cast(VoteDecision.FOR, "vic", deadline.minusSeconds(100));
        ReflectionTestUtils.setField(existing, "version", 3L);
        when(votes.findByOrganizationIdAndProposalId(1L, 10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> at(deadline.minusSeconds(10)).cast(10L, VoteDecision.AGAINST, 2L))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
