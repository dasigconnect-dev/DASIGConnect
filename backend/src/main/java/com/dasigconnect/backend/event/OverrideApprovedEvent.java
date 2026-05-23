package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;

/** T9 — Administrator approved a guard rail override request. */
public record OverrideApprovedEvent(Submission submission, User contributor) {}
