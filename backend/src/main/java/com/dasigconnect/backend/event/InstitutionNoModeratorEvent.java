package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Institution;

/** T14 — All Validators at an institution became inactive. */
public record InstitutionNoModeratorEvent(Institution institution) {}
