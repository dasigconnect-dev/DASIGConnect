package com.dasigconnect.backend.event;

import java.util.List;

import com.dasigconnect.backend.model.entity.Institution;

/** T-07 — Empty schedule warning for an institution with optional AI suggestions. */
public record EmptyScheduleEvent(
        Institution institution,
        List<String> suggestions
) {
    public EmptyScheduleEvent(Institution institution) {
        this(institution, List.of());
    }
}
