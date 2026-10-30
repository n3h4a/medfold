package com.medfold.records;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares a printed lab value with the reference range printed next to it on the same report.
 *
 * This is a plain numeric comparison, not a medical judgment, and it is deliberately strict:
 * anything it can't read with certainty (qualitative results like "Negative", values such as
 * "&lt;0.5", ranges that differ by sex or age, ...) gets no flag rather than a guessed one.
 */
public final class LabFlagCalculator {

    /** Parsed numbers and the resulting flag; any part can be null. */
    public record Comparison(BigDecimal value, BigDecimal low, BigDecimal high, LabFlag flag) {
    }

    private record Range(BigDecimal low, boolean lowInclusive, BigDecimal high, boolean highInclusive) {
    }

    private static final Comparison NOTHING = new Comparison(null, null, null, null);

    // 7,400 or 7400 or 7400.5 or .5 ; a single comma with 1-2 digits after it is a decimal comma (5,6).
    private static final String NUMBER = "(?:\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:[.,]\\d+)?|\\.\\d+)";
    private static final String SIGNED = "[-+]?" + NUMBER;
    // Units or notes after the numbers ("g/dL", "%", "mg/dL (fasting)") are allowed, further numbers are not.
    private static final String TAIL = "\\s*[^\\d]*";

    private static final Pattern VALUE = Pattern.compile("(" + SIGNED + ")\\s*\\*?");
    private static final Pattern BETWEEN = Pattern.compile("(" + SIGNED + ")\\s*(?:-|–|—|to)\\s*(" + SIGNED + ")" + TAIL);
    private static final Pattern UPPER = Pattern.compile("(<=|≤|<|up\\s*to|less\\s+than|below)\\s*(" + SIGNED + ")" + TAIL);
    private static final Pattern LOWER = Pattern.compile("(>=|≥|>|more\\s+than|greater\\s+than|above)\\s*(" + SIGNED + ")" + TAIL);

    private LabFlagCalculator() {
    }

    public static Comparison compare(String valueText, String referenceText) {
        BigDecimal value = parseValue(valueText);
        Range range = parseRange(referenceText);
        if (range == null) {
            return new Comparison(value, null, null, null);
        }
        if (value == null) {
            return new Comparison(null, range.low(), range.high(), null);
        }
        return new Comparison(value, range.low(), range.high(), flag(value, range));
    }

    /** Applies the comparison to a stored result. */
    public static void apply(LabResult result) {
        Comparison c = compare(result.getValueText(), result.getRefText());
        result.applyComparison(c.value(), c.low(), c.high(), c.flag());
    }

    static BigDecimal parseValue(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = VALUE.matcher(text.strip());
        return m.matches() ? number(m.group(1)) : null;
    }

    private static Range parseRange(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.strip().toLowerCase(Locale.ROOT);
        Matcher m = BETWEEN.matcher(t);
        if (m.matches()) {
            BigDecimal low = number(m.group(1));
            BigDecimal high = number(m.group(2));
            return low.compareTo(high) <= 0 ? new Range(low, true, high, true) : null;
        }
        m = UPPER.matcher(t);
        if (m.matches()) {
            return new Range(null, false, number(m.group(2)), !m.group(1).equals("<") && !m.group(1).startsWith("less")
                    && !m.group(1).equals("below"));
        }
        m = LOWER.matcher(t);
        if (m.matches()) {
            return new Range(number(m.group(2)), !m.group(1).equals(">") && !m.group(1).startsWith("more")
                    && !m.group(1).startsWith("greater") && !m.group(1).equals("above"), null, false);
        }
        return null;
    }

    private static LabFlag flag(BigDecimal value, Range range) {
        if (range.low() != null) {
            int c = value.compareTo(range.low());
            if (c < 0 || (c == 0 && !range.lowInclusive())) {
                return LabFlag.LOW;
            }
        }
        if (range.high() != null) {
            int c = value.compareTo(range.high());
            if (c > 0 || (c == 0 && !range.highInclusive())) {
                return LabFlag.HIGH;
            }
        }
        return LabFlag.NORMAL;
    }

    private static BigDecimal number(String text) {
        String digits = text.startsWith("+") ? text.substring(1) : text;
        String unsigned = digits.startsWith("-") ? digits.substring(1) : digits;
        if (unsigned.matches("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?")) {
            digits = digits.replace(",", "");     // thousands separators: 7,400
        } else {
            digits = digits.replace(',', '.');    // decimal comma: 5,6
        }
        return new BigDecimal(digits);
    }
}
