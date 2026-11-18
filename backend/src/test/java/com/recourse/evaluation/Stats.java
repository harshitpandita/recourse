package com.recourse.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The few statistics the evaluation needs, written out so each one is easy to check by hand. */
final class Stats {

    private Stats() {}

    static double mean(List<Double> xs) {
        return xs.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    /** Pearson correlation: +1 when y rises exactly in step with x, 0 when unrelated. NaN if either doesn't vary. */
    static double pearson(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.size() < 2) throw new IllegalArgumentException("need two equal lists of 2+ values");
        double mx = mean(x), my = mean(y);
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < x.size(); i++) {
            double dx = x.get(i) - mx, dy = y.get(i) - my;
            sxy += dx * dy;
            sxx += dx * dx;
            syy += dy * dy;
        }
        return sxx == 0 || syy == 0 ? Double.NaN : sxy / Math.sqrt(sxx * syy);
    }

    /** Mean absolute error: the average distance between the two lists, in their own units. */
    static double mae(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.isEmpty()) throw new IllegalArgumentException("need two equal, non-empty lists");
        double sum = 0;
        for (int i = 0; i < x.size(); i++) sum += Math.abs(x.get(i) - y.get(i));
        return sum / x.size();
    }

    /** Jaccard similarity: items in both sets divided by items in either. 1 = identical, 0 = nothing shared. */
    static <T> double jaccard(Set<T> a, Set<T> b) {
        Set<T> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) return 1;
        Set<T> both = new HashSet<>(a);
        both.retainAll(b);
        return (double) both.size() / union.size();
    }

    /** The 1-based position where two sequences first differ (or one ends), e.g. 2 means the first item matched. */
    static <T> int firstDifference(List<T> a, List<T> b) {
        int i = 0;
        while (i < a.size() && i < b.size() && a.get(i).equals(b.get(i))) i++;
        return i + 1;
    }

    /** Careers shared by two top-3 lists, as a fraction of 3. */
    static double overlap(List<String> a, List<String> b) {
        Set<String> both = new HashSet<>(a);
        both.retainAll(new HashSet<>(b));
        return (double) both.size() / Math.max(a.size(), b.size());
    }

    /** n choose k. */
    static double choose(int n, int k) {
        if (k < 0 || k > n) return 0;
        double r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }

    /** Chance that 3 careers drawn at random from {@code total} include at least one of {@code inFamily}. */
    static double chanceTop3(int total, int inFamily) {
        return 1 - choose(total - inFamily, 3) / choose(total, 3);
    }
}
