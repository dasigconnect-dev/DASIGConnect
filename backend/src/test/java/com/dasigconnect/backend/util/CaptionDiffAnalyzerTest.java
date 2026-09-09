package com.dasigconnect.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CaptionDiffAnalyzerTest {

    @Test
    void identicalText_isZero() {
        assertThat(CaptionDiffAnalyzer.changedWordRatio("hello world", "hello world")).isEqualTo(0.0);
    }

    @Test
    void casingAndWhitespaceOnly_isZero() {
        assertThat(CaptionDiffAnalyzer.changedWordRatio("  Hello   World  ", "hello world")).isEqualTo(0.0);
    }

    @Test
    void bothBlank_isZero() {
        assertThat(CaptionDiffAnalyzer.changedWordRatio(null, "")).isEqualTo(0.0);
        assertThat(CaptionDiffAnalyzer.changedWordRatio("   ", null)).isEqualTo(0.0);
    }

    @Test
    void blankToNonBlank_isOne() {
        assertThat(CaptionDiffAnalyzer.changedWordRatio("", "brand new caption")).isEqualTo(1.0);
        assertThat(CaptionDiffAnalyzer.changedWordRatio("some words here", "  ")).isEqualTo(1.0);
    }

    @Test
    void fullRewrite_isOne() {
        assertThat(CaptionDiffAnalyzer.changedWordRatio("alpha beta gamma delta", "one two three four"))
                .isEqualTo(1.0);
    }

    @Test
    void oneWordOfEight_isBelowThreshold() {
        double ratio = CaptionDiffAnalyzer.changedWordRatio(
                "the quick brown fox jumps over the fence",
                "the quick brown fox leaps over the fence");
        assertThat(ratio).isGreaterThan(0.0).isLessThan(0.30);
    }

    @Test
    void halfTheWordsChanged_isAboveThreshold() {
        double ratio = CaptionDiffAnalyzer.changedWordRatio(
                "join us this saturday for outreach",
                "join us next monday at noon");
        assertThat(ratio).isGreaterThanOrEqualTo(0.30);
    }
}
