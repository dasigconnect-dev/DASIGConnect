package com.dasigconnect.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.dto.resolution.FailedPublicationDto;
import com.dasigconnect.backend.model.dto.resolution.FailedPublicationPageDto;
import com.dasigconnect.backend.model.entity.PublicationAttempt;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

@Service
@Transactional(readOnly = true)
public class ResolutionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final SubmissionRepository submissionRepository;
    private final PublicationAttemptRepository publicationAttemptRepository;

    public ResolutionService(
            SubmissionRepository submissionRepository,
            PublicationAttemptRepository publicationAttemptRepository) {
        this.submissionRepository = submissionRepository;
        this.publicationAttemptRepository = publicationAttemptRepository;
    }

    public List<FailedPublicationDto> getFailures() {
        return toDtos(submissionRepository.findPublishFailures());
    }

    public FailedPublicationPageDto getFailurePage(int page, int pageSize, String search) {
        int safePage = Math.max(page, 0);
        int safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();

        Page<Submission> result = submissionRepository.findPublishFailurePage(
                normalizedSearch,
                PageRequest.of(safePage, safePageSize));

        return new FailedPublicationPageDto(
                toDtos(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                submissionRepository.countPublishFailures());
    }

    private List<FailedPublicationDto> toDtos(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            return List.of();
        }

        List<UUID> submissionIds = submissions.stream().map(Submission::getId).toList();
        Map<UUID, PublicationAttempt> latestAttempts = new LinkedHashMap<>();
        publicationAttemptRepository.findLatestBySubmissionIds(submissionIds)
                .forEach(attempt -> latestAttempts.putIfAbsent(
                        attempt.getSubmission().getId(),
                        attempt));

        return submissions.stream()
                .map(submission -> FailedPublicationDto.from(
                        submission,
                        latestAttempts.get(submission.getId())))
                .toList();
    }
}
