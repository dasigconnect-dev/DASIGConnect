package com.dasigconnect.backend.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_state", nullable = false, length = 30)
    private UserStatus accountState = UserStatus.pending;

    @Column(name = "is_super_administrator", nullable = false)
    private boolean superAdministrator;

    @Column(name = "super_admin_transfer_requested_by")
    private UUID superAdminTransferRequestedBy;

    @Column(name = "super_admin_transfer_expires_at")
    private Instant superAdminTransferExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "avatar_data")
    private byte[] avatarData;

    @Column(name = "avatar_content_type", length = 40)
    private String avatarContentType;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Institution getInstitution() {
        return institution;
    }

    public void setInstitution(Institution institution) {
        this.institution = institution;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getAccountState() {
        return accountState;
    }

    public void setAccountState(UserStatus accountState) {
        this.accountState = accountState;
    }

    public boolean isSuperAdministrator() {
        return superAdministrator;
    }

    public void setSuperAdministrator(boolean superAdministrator) {
        this.superAdministrator = superAdministrator;
    }

    public UUID getSuperAdminTransferRequestedBy() {
        return superAdminTransferRequestedBy;
    }

    public void setSuperAdminTransferRequestedBy(UUID superAdminTransferRequestedBy) {
        this.superAdminTransferRequestedBy = superAdminTransferRequestedBy;
    }

    public Instant getSuperAdminTransferExpiresAt() {
        return superAdminTransferExpiresAt;
    }

    public void setSuperAdminTransferExpiresAt(Instant superAdminTransferExpiresAt) {
        this.superAdminTransferExpiresAt = superAdminTransferExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public byte[] getAvatarData() {
        return avatarData;
    }

    public void setAvatarData(byte[] avatarData) {
        this.avatarData = avatarData;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public void setAvatarContentType(String avatarContentType) {
        this.avatarContentType = avatarContentType;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(Instant avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }
}
