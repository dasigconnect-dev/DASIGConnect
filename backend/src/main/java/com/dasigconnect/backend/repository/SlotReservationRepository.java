package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SlotReservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotReservationRepository extends JpaRepository<SlotReservation, UUID> {
}
