package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;

/** T10 — Administrator denied a guard rail override request. */
public record OverrideDeniedEvent(Submission submission, User contributor, String reason) {}
