package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SubscriptionUsageEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionUsageEventRepository extends JpaRepository<SubscriptionUsageEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO subscription_usage_events (
                id, organization_id, institution_id, user_id, event_type,
                quantity, unit, idempotency_key, source_type, source_id,
                occurred_at, created_at
            ) VALUES (
                :id, :organizationId, :institutionId, :userId, :eventType,
                :quantity, :unit, :idempotencyKey, :sourceType, :sourceId,
                :occurredAt, NOW()
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("institutionId") UUID institutionId,
            @Param("userId") UUID userId,
            @Param("eventType") String eventType,
            @Param("quantity") long quantity,
            @Param("unit") String unit,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("sourceType") String sourceType,
            @Param("sourceId") UUID sourceId,
            @Param("occurredAt") Instant occurredAt);

    @Query(value = """
            SELECT event_type, COALESCE(SUM(quantity), 0)
            FROM subscription_usage_events
            WHERE organization_id = :organizationId
              AND occurred_at >= :periodStart
              AND occurred_at < :periodEnd
              AND event_type IN ('SUBMISSION', 'AI_CAPTION', 'AI_MEDIA_SUGGESTION')
            GROUP BY event_type
            """, nativeQuery = true)
    List<Object[]> aggregateMonthlyConsumption(
            @Param("organizationId") UUID organizationId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    @Query(value = """
            SELECT COALESCE(SUM(quantity), 0)
            FROM subscription_usage_events
            WHERE organization_id = :organizationId
              AND event_type = :eventType
              AND occurred_at >= :periodStart
              AND occurred_at < :periodEnd
            """, nativeQuery = true)
    long sumConsumption(
            @Param("organizationId") UUID organizationId,
            @Param("eventType") String eventType,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    @Query(value = """
            SELECT COALESCE(SUM(quantity), 0)
            FROM subscription_usage_events
            WHERE organization_id = :organizationId
              AND user_id = :userId
              AND event_type = :eventType
              AND occurred_at >= :periodStart
              AND occurred_at < :periodEnd
            """, nativeQuery = true)
    long sumUserConsumption(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId,
            @Param("eventType") String eventType,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    @Query(value = """
            SELECT institution.id,
                   institution.name,
                   COALESCE((
                       SELECT COUNT(*)
                       FROM users user_account
                       WHERE user_account.institution_id = institution.id
                         AND user_account.account_state = 'active'
                   ), 0),
                   COALESCE((
                       SELECT SUM(media.file_size_bytes)
                       FROM media_assets media
                       WHERE media.institution_id = institution.id
                         AND media.deleted_at IS NULL
                         AND media.status <> 'DELETED'
                   ), 0),
                   COALESCE((
                       SELECT SUM(event.quantity)
                       FROM subscription_usage_events event
                       WHERE event.institution_id = institution.id
                         AND event.event_type = 'SUBMISSION'
                         AND event.occurred_at >= :periodStart
                         AND event.occurred_at < :periodEnd
                   ), 0),
                   COALESCE((
                       SELECT SUM(event.quantity)
                       FROM subscription_usage_events event
                       WHERE event.institution_id = institution.id
                         AND event.event_type = 'AI_CAPTION'
                         AND event.occurred_at >= :periodStart
                         AND event.occurred_at < :periodEnd
                   ), 0),
                   COALESCE((
                       SELECT SUM(event.quantity)
                       FROM subscription_usage_events event
                       WHERE event.institution_id = institution.id
                         AND event.event_type = 'AI_MEDIA_SUGGESTION'
                         AND event.occurred_at >= :periodStart
                         AND event.occurred_at < :periodEnd
                   ), 0)
            FROM institutions institution
            WHERE institution.organization_id = :organizationId
            ORDER BY institution.name
            """, nativeQuery = true)
    List<Object[]> institutionUsage(
            @Param("organizationId") UUID organizationId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    @Query(value = """
            SELECT COUNT(*)
            FROM institutions
            WHERE organization_id = :organizationId
            """, nativeQuery = true)
    long currentInstitutionCount(@Param("organizationId") UUID organizationId);

    @Query(value = """
            SELECT COUNT(*)
            FROM users user_account
            WHERE user_account.account_state = 'active'
              AND (
                  user_account.institution_id IN (
                      SELECT institution.id
                      FROM institutions institution
                      WHERE institution.organization_id = :organizationId
                  )
                  OR (
                      user_account.institution_id IS NULL
                      AND :organizationId = CAST('00000000-0000-0000-0000-000000000001' AS UUID)
                  )
              )
            """, nativeQuery = true)
    long currentActiveUserCount(@Param("organizationId") UUID organizationId);

    @Query(value = """
            SELECT COUNT(*)
            FROM users user_account
            WHERE (
                user_account.institution_id IN (
                    SELECT institution.id
                    FROM institutions institution
                    WHERE institution.organization_id = :organizationId
                )
                OR (
                    user_account.institution_id IS NULL
                    AND :organizationId = CAST('00000000-0000-0000-0000-000000000001' AS UUID)
                )
            )
              AND (
                  user_account.account_state = 'active'
                  OR (
                      user_account.account_state IN ('pending', 'pending_email_undelivered')
                      AND EXISTS (
                          SELECT 1
                          FROM invitation_tokens token
                          WHERE LOWER(token.recipient_email) = LOWER(user_account.email)
                            AND token.used_at IS NULL
                            AND token.expires_at > :now
                      )
                  )
              )
            """, nativeQuery = true)
    long currentSeatCommitmentCount(
            @Param("organizationId") UUID organizationId,
            @Param("now") Instant now);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM users user_account
                WHERE LOWER(user_account.email) = LOWER(:email)
                  AND (
                      user_account.institution_id IN (
                          SELECT institution.id
                          FROM institutions institution
                          WHERE institution.organization_id = :organizationId
                      )
                      OR (
                          user_account.institution_id IS NULL
                          AND :organizationId = CAST('00000000-0000-0000-0000-000000000001' AS UUID)
                      )
                  )
                  AND (
                      user_account.account_state = 'active'
                      OR (
                          user_account.account_state IN ('pending', 'pending_email_undelivered')
                          AND EXISTS (
                              SELECT 1
                              FROM invitation_tokens token
                              WHERE LOWER(token.recipient_email) = LOWER(user_account.email)
                                AND token.used_at IS NULL
                                AND token.expires_at > :now
                          )
                      )
                  )
            )
            """, nativeQuery = true)
    boolean hasSeatCommitment(
            @Param("organizationId") UUID organizationId,
            @Param("email") String email,
            @Param("now") Instant now);

    @Query(value = """
            SELECT COALESCE(SUM(media.file_size_bytes), 0)
            FROM media_assets media
            JOIN institutions institution ON institution.id = media.institution_id
            WHERE institution.organization_id = :organizationId
              AND media.deleted_at IS NULL
              AND media.status <> 'DELETED'
            """, nativeQuery = true)
    long currentMediaStorageBytes(@Param("organizationId") UUID organizationId);

    @Query(value = """
            SELECT COALESCE(SUM(quantity), 0)
            FROM subscription_usage_events
            WHERE organization_id = :organizationId
              AND event_type = 'MEDIA_STORAGE_BYTES'
            """, nativeQuery = true)
    long ledgerMediaStorageBytes(@Param("organizationId") UUID organizationId);

    Page<SubscriptionUsageEvent> findByOrganizationIdOrderByOccurredAtDesc(
            UUID organizationId, Pageable pageable);
}
