package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

public record ProofreadResponseDto(List<ProofreadIssueDto> issues) {}
