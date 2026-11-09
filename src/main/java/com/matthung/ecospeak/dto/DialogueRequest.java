package com.matthung.ecospeak.dto;

public record DialogueRequest(String scenario) {

    /** Removes exactly the whitespace and line terminators removed by JavaScript trim(). */
    public String trimmedScenario() {
        return scenario == null ? "" : scenario.strip();
    }
}
