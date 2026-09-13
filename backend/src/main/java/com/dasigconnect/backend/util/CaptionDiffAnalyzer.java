package com.dasigconnect.backend.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Word-level change ratio between two versions of a text field (used for the
 * caption "minor vs. substantive" governance threshold — A10).
 *
 * <p>The ratio is a Levenshtein edit distance over <em>word</em> tokens divided
 * by the longer token count, so it answers "what fraction of the words changed?"
 * regardless of where in the text the change happened. Pure reordering counts as
 * change (a moved word is one deletion + one insertion); casing and surrounding
 * whitespace do not.
 */
public final class CaptionDiffAnalyzer {

    private CaptionDiffAnalyzer() {}

    /**
     * @return fraction of words that changed, 0.0 (identical) .. 1.0 (nothing in common).
     *         Two blank/empty inputs return 0.0; blank ↔ non-blank returns 1.0.
     */
    public static double changedWordRatio(String original, String current) {
        List<String> a = tokenize(original);
        List<String> b = tokenize(current);
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 1.0;
        }
        int distance = levenshtein(a, b);
        int denominator = Math.max(a.size(), b.size());
        return Math.min(1.0, (double) distance / denominator);
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        for (String raw : text.trim().toLowerCase().split("\\s+")) {
            if (!raw.isEmpty()) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    private static int levenshtein(List<String> a, List<String> b) {
        int[] prev = new int[b.size() + 1];
        int[] curr = new int[b.size() + 1];
        for (int j = 0; j <= b.size(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.size(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.size(); j++) {
                int cost = a.get(i - 1).equals(b.get(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.size()];
    }
}
