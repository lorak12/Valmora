package org.nakii.valmora.infrastructure.config.diag;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/** "Did you mean ...?" — finds the closest known name to a mistyped one. */
public final class Suggestions {

    private Suggestions() {}

    /**
     * The candidate closest to {@code input} by case-insensitive Damerau-Levenshtein distance, if
     * it is close enough to plausibly be a typo: at most 1 edit for inputs of 4 characters or fewer,
     * at most 2 otherwise. Namespaced candidates ({@code pack:id}) also match on their bare id.
     */
    public static Optional<String> closest(String input, Collection<String> candidates) {
        if (input == null || input.isEmpty() || candidates == null || candidates.isEmpty()) return Optional.empty();
        String needle = input.toLowerCase(Locale.ROOT);
        int limit = needle.length() <= 4 ? 1 : 2;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.equals(needle)) continue;
            int d = distance(needle, lower);
            int colon = lower.indexOf(':');
            if (colon >= 0) d = Math.min(d, distance(needle, lower.substring(colon + 1)));
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return bestDistance <= limit ? Optional.of(best) : Optional.empty();
    }

    /** {@code "did you mean 'x'?"}, or null when nothing is close. */
    public static String hint(String input, Collection<String> candidates) {
        return closest(input, candidates).map(s -> "did you mean '" + s + "'?").orElse(null);
    }

    /** Optimal string alignment distance (Damerau-Levenshtein with adjacent transpositions). */
    static int distance(String a, String b) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > 3) return Math.abs(n - m);
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[n][m];
    }
}
