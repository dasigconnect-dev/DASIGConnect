package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Institution;

/** T11 — Administrator posted directly to the DASIG Facebook Page. */
public record AdminDirectPostEvent(Institution institution, String postTitle, String postUrl) {}
