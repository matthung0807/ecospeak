package com.matthung.ecospeak.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.matthung.ecospeak.dto.DialogueLine;
import com.matthung.ecospeak.dto.DialogueResponse;
import com.matthung.ecospeak.service.DialogueService;

class DialogueControllerTests {
    private DialogueService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DialogueService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DialogueController(service)).build();
    }

    @Test
    void generatesFromNormalizedScenarioAndKeepsResponseShape() throws Exception {
        var dialogue = new DialogueResponse();
        dialogue.setTopic("At a coffee shop");
        dialogue.setTargetPhrase("Could I get");
        var line = new DialogueLine();
        line.setSpeaker("A");
        line.setText("Could I get a coffee?");
        dialogue.setDialogue(List.of(line));
        when(service.generate("Ordering coffee")).thenReturn(dialogue);

        mvc.perform(post("/api/dialogs/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenario\":\"  Ordering coffee  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("At a coffee shop"))
                .andExpect(jsonPath("$.targetPhrase").value("Could I get"))
                .andExpect(jsonPath("$.dialogue[0].speaker").value("A"))
                .andExpect(jsonPath("$.dialogue[0].text").value("Could I get a coffee?"));
        verify(service).generate("Ordering coffee");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"scenario\":null}", "{\"scenario\":\"\"}",
            "{\"scenario\":\"   \"}", "{\"scenario\":\"\\t\\r\\n\"}",
            "{\"scenario\":\"\u00A0\u3000\uFEFF\"}"})
    void rejectsInvalidInputWithoutCallingService(String body) throws Exception {
        mvc.perform(post("/api/dialogs/generate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void getNoLongerGenerates() throws Exception {
        mvc.perform(get("/api/dialogs/generate")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
