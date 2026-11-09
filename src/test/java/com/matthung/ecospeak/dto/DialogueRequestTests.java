package com.matthung.ecospeak.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DialogueRequestTests {
    private static final String TRIM_WHITESPACE = "\t\n\u000B\f\r \u00A0\u1680"
            + "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A"
            + "\u2028\u2029\u202F\u205F\u3000\uFEFF";

    @Test
    void matchesJavascriptTrimWhitespace() {
        assertEquals("", new DialogueRequest(TRIM_WHITESPACE).trimmedScenario());
        assertEquals("Ordering  coffee", new DialogueRequest(
                TRIM_WHITESPACE + "Ordering  coffee" + TRIM_WHITESPACE).trimmedScenario());
    }

    @Test
    void preservesCharactersNotRemovedByJavascriptTrim() {
        for (String value : new String[] {"\u0085", "\u180E", "\u200B", "\u0000"}) {
            assertEquals(value, new DialogueRequest(value).trimmedScenario());
        }
    }
}
