package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    List<SubscriptionPlan> findAllByActiveTrueOrderByMonthlyPriceCentavosAscVersionDesc();
    Optional<SubscriptionPlan> findByIdAndActiveTrue(UUID id);
    Optional<SubscriptionPlan> findFirstByCodeAndActiveTrueOrderByVersionDesc(String code);
}
