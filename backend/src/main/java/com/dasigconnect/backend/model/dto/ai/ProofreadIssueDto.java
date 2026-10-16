package com.dasigconnect.backend.model.dto.ai;

/**
 * One proofreading finding.
 *
 * @param kind        spelling, grammar, clarity, or meaning
 * @param excerpt     the exact text it refers to (always a substring of the checked text)
 * @param suggestion  the replacement for {@code excerpt}; empty for a meaning
 *                    change, which has no safe automatic fix
 * @param explanation one short sentence for the user
 */
public record ProofreadIssueDto(String kind, String excerpt, String suggestion, String explanation) {}
