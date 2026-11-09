package com.matthung.ecospeak.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.matthung.ecospeak.dto.DialogueResponse;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputItem;
import com.openai.models.responses.StructuredResponseOutputMessage;
import com.openai.services.blocking.ResponseService;

class DialogueServiceTests {
    @Test
    @SuppressWarnings("unchecked")
    void sendsEachScenarioSeparatelyFromCoachingInstructionsAndReturnsFirstOutput() {
        var client = mock(OpenAIClient.class);
        var responses = mock(ResponseService.class);
        when(client.responses()).thenReturn(responses);
        StructuredResponse<DialogueResponse> response = mock(StructuredResponse.class);
        StructuredResponseOutputItem<DialogueResponse> item = mock(StructuredResponseOutputItem.class);
        StructuredResponseOutputMessage<DialogueResponse> message = mock(StructuredResponseOutputMessage.class);
        StructuredResponseOutputMessage.Content<DialogueResponse> content =
                mock(StructuredResponseOutputMessage.Content.class);
        var expected = new DialogueResponse();
        when(response.output()).thenReturn(List.of(item, item));
        when(item.message()).thenReturn(Optional.of(message));
        when(message.content()).thenReturn(List.of(content));
        when(content.outputText()).thenReturn(Optional.of(expected));
        when(responses.create(any(StructuredResponseCreateParams.class))).thenReturn(response);

        var service = new DialogueService(client);
        assertSame(expected, service.generate("Ordering coffee"));
        assertSame(expected, service.generate("Making an appointment"));

        ArgumentCaptor<StructuredResponseCreateParams<DialogueResponse>> requests =
                ArgumentCaptor.forClass(StructuredResponseCreateParams.class);
        verify(responses, times(2)).create(requests.capture());
        var captured = requests.getAllValues();
        assertEquals("Ordering coffee", captured.get(0).rawParams().input().orElseThrow().asText());
        assertEquals("Making an appointment", captured.get(1).rawParams().input().orElseThrow().asText());
        for (var request : captured) {
            assertEquals(DialogueResponse.class, request.responseType());
            assertEquals("gpt-5-mini", request.rawParams().model().orElseThrow().asString());
            var instructions = request.rawParams().instructions().orElseThrow();
            assertTrue(instructions.contains("Base the conversation on the user-provided scenario"));
            assertTrue(instructions.contains("exactly two participants: A and B"));
            assertTrue(instructions.contains("4 to 5 dialogue lines"));
            assertTrue(instructions.contains("target phrase must appear naturally"));
            assertFalse(instructions.contains("Ordering coffee"));
            assertFalse(instructions.contains("Making an appointment"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsWhenNoParsedOutputExists() {
        var client = mock(OpenAIClient.class);
        var responses = mock(ResponseService.class);
        when(client.responses()).thenReturn(responses);
        StructuredResponse<DialogueResponse> response = mock(StructuredResponse.class);
        when(response.output()).thenReturn(List.of());
        when(responses.create(any(StructuredResponseCreateParams.class))).thenReturn(response);

        var error = assertThrows(IllegalStateException.class,
                () -> new DialogueService(client).generate("Ordering coffee"));
        assertEquals("OpenAI returned no dialogue response", error.getMessage());
    }
}
