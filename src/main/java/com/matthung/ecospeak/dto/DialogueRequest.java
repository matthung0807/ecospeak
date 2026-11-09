package com.matthung.ecospeak.dto;

public record DialogueRequest(String scenario) {

    /** Removes exactly the whitespace and line terminators removed by JavaScript trim(). */
    public String normalizedScenario() {
        if (scenario == null) {
            return "";
        }
        int start = 0;
        int end = scenario.length();
        while (start < end && isTrimWhitespace(scenario.charAt(start))) {
            start++;
        }
        while (end > start && isTrimWhitespace(scenario.charAt(end - 1))) {
            end--;
        }
        return scenario.substring(start, end);
    }

    private static boolean isTrimWhitespace(char value) {
        return switch (value) {
            case '\u0009', '\n', '\u000B', '\f', '\r', ' ', '\u00A0', '\u1680',
                    '\u2028', '\u2029', '\u202F', '\u205F', '\u3000', '\uFEFF' -> true;
            default -> value >= '\u2000' && value <= '\u200A';
        };
    }
}
