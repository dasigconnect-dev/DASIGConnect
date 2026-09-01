package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.model.dto.calendar.CalendarEventDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private SlotReservationRepository slotReservationRepository;

    @InjectMocks
    private CalendarService calendarService;

    private UUID myInstId;
    private UUID dasigCentralVisayasId;
    private UUID otherInstId;

    private Institution myInstitution;
    private Institution dasigInstitution;
    private Institution otherInstitution;

    @BeforeEach
    void setUp() {
        myInstId = UUID.randomUUID();
        dasigCentralVisayasId = UUID.randomUUID();
        otherInstId = UUID.randomUUID();

        myInstitution = new Institution();
        myInstitution.setId(myInstId);
        myInstitution.setName("Cebu Institute of Technology - University");
        myInstitution.setCode("CIT-U");

        dasigInstitution = new Institution();
        dasigInstitution.setId(dasigCentralVisayasId);
        dasigInstitution.setName("DASIG Central Visayas");
        dasigInstitution.setCode("DASIG-CV");
        dasigInstitution.setProtected(true);

        otherInstitution = new Institution();
        otherInstitution.setId(otherInstId);
        otherInstitution.setName("University of the Philippines Cebu");
        otherInstitution.setCode("UP-Cebu");

        when(slotReservationRepository.findLockedSubmissionIds(any())).thenReturn(List.of());
    }

    private Submission createSubmission(UUID id, String title, Institution institution, SubmissionStatus status) {
        Submission s = new Submission();
        s.setId(id);
        s.setEventTitle(title);
        s.setInstitution(institution);
        s.setStatus(status);
        s.setCaption("Event caption for " + title);
        s.setDescription("Event description for " + title);
        s.setScheduledAt(Instant.parse("2026-08-25T10:00:00Z"));
        return s;
    }

    private JwtUserDetails createPrincipal(UUID userId, String role, UUID instId) {
        return new JwtUserDetails(
                userId,
                "user@example.com",
                role,
                instId);
    }

    @Test
    @DisplayName("Admin receives full event details for all institutions")
    void getCalendarEvents_asSuperAdmin_returnsFullEventsForAll() {
        Submission s1 = createSubmission(UUID.randomUUID(), "CIT Tech Fest", myInstitution, SubmissionStatus.scheduled);
        Submission s2 = createSubmission(UUID.randomUUID(), "UP Hackathon", otherInstitution, SubmissionStatus.scheduled);
        when(submissionRepository.findAllWithScheduledSlot()).thenReturn(List.of(s1, s2));

        JwtUserDetails adminUser = createPrincipal(UUID.randomUUID(), "admin", null);
        List<CalendarEventDto> results = calendarService.getCalendarEvents(adminUser);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTitle()).isEqualTo("CIT Tech Fest");
        assertThat(results.get(0).getCaption()).isEqualTo("Event caption for CIT Tech Fest");
        assertThat(results.get(1).getTitle()).isEqualTo("UP Hackathon");
    }

    @Test
    @DisplayName("Moderator receives full event details for all institutions (network-wide role)")
    void getCalendarEvents_asModerator_returnsFullEventsForAll() {
        Submission s1 = createSubmission(UUID.randomUUID(), "CIT Tech Fest", myInstitution, SubmissionStatus.scheduled);
        Submission s2 = createSubmission(UUID.randomUUID(), "UP Hackathon", otherInstitution, SubmissionStatus.scheduled);
        when(submissionRepository.findAllWithScheduledSlot()).thenReturn(List.of(s1, s2));

        // Moderator accounts always have a null institutionId — this must not
        // fall through to the masked/scoped calendar path.
        JwtUserDetails adminUser = createPrincipal(UUID.randomUUID(), "moderator", null);
        List<CalendarEventDto> results = calendarService.getCalendarEvents(adminUser);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTitle()).isEqualTo("CIT Tech Fest");
        assertThat(results.get(1).getTitle()).isEqualTo("UP Hackathon");
    }

    @Test
    @DisplayName("Locked slot reservations are reflected on the calendar event")
    void getCalendarEvents_lockedReservation_marksEventLocked() {
        Submission locked = createSubmission(UUID.randomUUID(), "CIT Tech Fest", myInstitution, SubmissionStatus.scheduled);
        Submission unlocked = createSubmission(UUID.randomUUID(), "UP Hackathon", otherInstitution, SubmissionStatus.scheduled);
        when(submissionRepository.findAllWithScheduledSlot()).thenReturn(List.of(locked, unlocked));
        when(slotReservationRepository.findLockedSubmissionIds(any())).thenReturn(List.of(locked.getId()));

        JwtUserDetails adminUser = createPrincipal(UUID.randomUUID(), "admin", null);
        List<CalendarEventDto> results = calendarService.getCalendarEvents(adminUser);

        CalendarEventDto lockedEvent = results.stream().filter(e -> e.getId().equals(locked.getId())).findFirst().orElseThrow();
        CalendarEventDto unlockedEvent = results.stream().filter(e -> e.getId().equals(unlocked.getId())).findFirst().orElseThrow();
        assertThat(lockedEvent.isLocked()).isTrue();
        assertThat(unlockedEvent.isLocked()).isFalse();
    }

    @Test
    @DisplayName("Contributor receives full details for own institution and DASIG Central Visayas, masked for other institutions")
    void getCalendarEvents_asContributor_returnsFullForOwnAndDasig_maskedForOthers() {
        Submission mySub = createSubmission(UUID.randomUUID(), "CIT Tech Fest", myInstitution, SubmissionStatus.scheduled);
        Submission dasigSub = createSubmission(UUID.randomUUID(), "DASIG Regional Assembly", dasigInstitution, SubmissionStatus.scheduled);
        Submission otherSub = createSubmission(UUID.randomUUID(), "UP Hackathon", otherInstitution, SubmissionStatus.scheduled);

        when(institutionRepository.findByNameIgnoreCase("DASIG Central Visayas")).thenReturn(Optional.of(dasigInstitution));
        when(submissionRepository.findAllCalendarVisibleSlots()).thenReturn(List.of(mySub, dasigSub, otherSub));

        JwtUserDetails contributorUser = createPrincipal(UUID.randomUUID(), "contributor", myInstId);
        List<CalendarEventDto> results = calendarService.getCalendarEvents(contributorUser);

        assertThat(results).hasSize(3);

        // Own institution: full details
        CalendarEventDto myEvent = results.stream().filter(e -> e.getId().equals(mySub.getId())).findFirst().orElseThrow();
        assertThat(myEvent.getTitle()).isEqualTo("CIT Tech Fest");
        assertThat(myEvent.getCaption()).isEqualTo("Event caption for CIT Tech Fest");
        assertThat(myEvent.getDescription()).isEqualTo("Event description for CIT Tech Fest");
        assertThat(myEvent.getInstitutionId()).isEqualTo(myInstId);

        // DASIG Central Visayas: full details
        CalendarEventDto dasigEvent = results.stream().filter(e -> e.getId().equals(dasigSub.getId())).findFirst().orElseThrow();
        assertThat(dasigEvent.getTitle()).isEqualTo("DASIG Regional Assembly");
        assertThat(dasigEvent.getCaption()).isEqualTo("Event caption for DASIG Regional Assembly");
        assertThat(dasigEvent.getDescription()).isEqualTo("Event description for DASIG Regional Assembly");
        assertThat(dasigEvent.getInstitutionId()).isEqualTo(dasigCentralVisayasId);

        // Other institution: masked (timing and title visible, sensitive content masked)
        CalendarEventDto otherEvent = results.stream().filter(e -> e.getId().equals(otherSub.getId())).findFirst().orElseThrow();
        assertThat(otherEvent.getTitle()).isEqualTo("UP Hackathon");
        assertThat(otherEvent.getCaption()).isNull();
        assertThat(otherEvent.getDescription()).isNull();
        assertThat(otherEvent.getContributorName()).isNull();
        assertThat(otherEvent.getInstitutionId()).isEqualTo(otherInstId);
        assertThat(otherEvent.getScheduledAt()).isEqualTo(Instant.parse("2026-08-25T10:00:00Z"));

        // Network-bucket events are never flagged as the viewer's own.
        assertThat(results).allSatisfy(e -> assertThat(e.isMine()).isFalse());
    }

    @Test
    @DisplayName("Contributor also sees own pending / failed / missed-review submissions in full, flagged mine")
    void getCalendarEvents_asContributor_includesOwnWorkflowSubmissions_markedMine() {
        UUID contributorId = UUID.randomUUID();

        Submission scheduledNetwork =
                createSubmission(UUID.randomUUID(), "CIT Tech Fest", myInstitution, SubmissionStatus.scheduled);
        Submission ownPending =
                createSubmission(UUID.randomUUID(), "My Pending Post", myInstitution, SubmissionStatus.pending);
        Submission ownFailed =
                createSubmission(UUID.randomUUID(), "My Failed Post", myInstitution, SubmissionStatus.publish_failed);
        Submission ownMissedReview =
                createSubmission(UUID.randomUUID(), "My Missed Review", myInstitution, SubmissionStatus.missed_review);

        when(institutionRepository.findByNameIgnoreCase("DASIG Central Visayas")).thenReturn(Optional.of(dasigInstitution));
        when(submissionRepository.findAllCalendarVisibleSlots()).thenReturn(List.of(scheduledNetwork));
        when(submissionRepository.findOwnCalendarWorkflowSlots(contributorId))
                .thenReturn(List.of(ownPending, ownFailed, ownMissedReview));

        JwtUserDetails contributorUser = createPrincipal(contributorId, "contributor", myInstId);
        List<CalendarEventDto> results = calendarService.getCalendarEvents(contributorUser);

        assertThat(results).hasSize(4);

        CalendarEventDto pendingEvent =
                results.stream().filter(e -> e.getId().equals(ownPending.getId())).findFirst().orElseThrow();
        assertThat(pendingEvent.isMine()).isTrue();
        assertThat(pendingEvent.getStatus()).isEqualTo("pending");
        assertThat(pendingEvent.getCaption()).isEqualTo("Event caption for My Pending Post");

        CalendarEventDto missedReviewEvent =
                results.stream().filter(e -> e.getId().equals(ownMissedReview.getId())).findFirst().orElseThrow();
        assertThat(missedReviewEvent.isMine()).isTrue();
        assertThat(missedReviewEvent.getStatus()).isEqualTo("missed_review");

        CalendarEventDto networkEvent =
                results.stream().filter(e -> e.getId().equals(scheduledNetwork.getId())).findFirst().orElseThrow();
        assertThat(networkEvent.isMine()).isFalse();
    }
}
