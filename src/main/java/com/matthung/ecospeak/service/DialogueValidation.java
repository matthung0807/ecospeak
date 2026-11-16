package com.matthung.ecospeak.service;

import com.matthung.ecospeak.dto.DialogueResponse;

public final class DialogueValidation {
    private DialogueValidation() {}

    public static void requireComplete(DialogueResponse response) {
        if (response == null || !nonblank(response.getTopic()) || !nonblank(response.getTargetPhrase())
                || response.getDialogue() == null || response.getDialogue().isEmpty()
                || response.getDialogue().stream().anyMatch(line -> line == null
                    || !nonblank(line.getSpeaker()) || !nonblank(line.getText()))) {
            throw new IllegalStateException("Incomplete dialogue response");
        }
    }

    private static boolean nonblank(String value) {
        return value != null && value.codePoints().anyMatch(c -> !Character.isWhitespace(c)
                && !Character.isSpaceChar(c) && c != 0xFEFF);
    }
}
