package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot_reservations")
public class SlotReservation {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotReservationStatus status = SlotReservationStatus.held;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * True only when this reservation was created via an Administrator's
     * explicit guard-rail override (GR-H1 hard block, reschedule or manual
     * publish retry). Exempts the row from the V96 network-wide ±30-minute
     * exclusion constraint's WHERE clause -- without this, the constraint
     * added in V95 to close a real GR-H1 race silently defeated the Admin's
     * pre-existing, intentional override capability, since the DB rejected
     * the insert unconditionally regardless of the override reason.
     */
    @Column(name = "admin_override", nullable = false)
    private boolean adminOverride = false;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Submission getSubmission() {
        return submission;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public Institution getInstitution() {
        return institution;
    }

    public void setInstitution(Institution institution) {
        this.institution = institution;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public SlotReservationStatus getStatus() {
        return status;
    }

    public void setStatus(SlotReservationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isAdminOverride() {
        return adminOverride;
    }

    public void setAdminOverride(boolean adminOverride) {
        this.adminOverride = adminOverride;
    }
}
