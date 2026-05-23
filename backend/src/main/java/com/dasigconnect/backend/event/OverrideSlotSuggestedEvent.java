package com.dasigconnect.backend.event;

import java.time.Instant;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;

/** T17 — Administrator suggested an alternative slot for a guard rail override request. */
public record OverrideSlotSuggestedEvent(Submission submission, User contributor, Instant suggestedSlot) {}
