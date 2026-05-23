package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Institution;

/** T15 — Institution workspace transitioned ONBOARDING → ACTIVE. */
public record InstitutionOnboardedEvent(Institution institution) {}
