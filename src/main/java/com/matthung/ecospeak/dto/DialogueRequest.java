package com.matthung.ecospeak.dto;

public record DialogueRequest(String scenario) {

    /** Removes exactly the whitespace and line terminators removed by JavaScript trim(). */
    public String trimmedScenario() {
        return scenario == null ? "" : scenario.replaceAll(
                "^[\\x09-\\x0D\\x20\\x{00A0}\\x{1680}\\x{2000}-\\x{200A}\\x{2028}\\x{2029}\\x{202F}\\x{205F}\\x{3000}\\x{FEFF}]+|[\\x09-\\x0D\\x20\\x{00A0}\\x{1680}\\x{2000}-\\x{200A}\\x{2028}\\x{2029}\\x{202F}\\x{205F}\\x{3000}\\x{FEFF}]+$", "");
    }
}
