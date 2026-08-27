package com.dasigconnect.backend.event;

import java.util.List;

/** T-12 — Weekly embedding reconciliation failure digest. */
public record EmbeddingFailureDigestEvent(
        long failedCount,
        List<String> sampleFilenames
) {}
