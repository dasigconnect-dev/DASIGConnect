package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

public record EventHypothesisDto(String eventType, double confidence, List<String> evidence) {
}
