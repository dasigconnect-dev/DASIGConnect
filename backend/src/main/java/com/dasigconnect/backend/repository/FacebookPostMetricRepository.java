package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.FacebookPostMetric;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacebookPostMetricRepository extends JpaRepository<FacebookPostMetric, UUID> {
}
