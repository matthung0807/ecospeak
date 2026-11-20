package com.matthung.ecospeak.controller;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matthung.ecospeak.dto.DialogueLine;
import com.matthung.ecospeak.dto.DialogueResponse;
import com.matthung.ecospeak.history.HistoryService;
import com.matthung.ecospeak.quota.QuotaService;
import com.matthung.ecospeak.service.DialogueService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class DialogueControllerTests {
    private DialogueService service;
    private MockMvc mvc;
    private HistoryService history;
    private QuotaService quota;

    @BeforeEach
    void setUp() {
        service = mock(DialogueService.class);
        history = mock(HistoryService.class);
        quota = mock(QuotaService.class);
        when(quota.admit(any(), anyString()))
                .thenReturn(
                        new QuotaService.Admission(
                                "reserved", new QuotaService.Attempt("id", "token"), null, null));
        when(history.save(anyString(), any(), any(), any()))
                .thenReturn(java.util.UUID.randomUUID());
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new DialogueController(
                                        service, history, quota, JsonMapper.builder().build()))
                        .build();
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

        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"  Ordering coffee  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("At a coffee shop"))
                .andExpect(jsonPath("$.targetPhrase").value("Could I get"))
                .andExpect(jsonPath("$.dialogue[0].speaker").value("A"))
                .andExpect(jsonPath("$.dialogue[0].text").value("Could I get a coffee?"));
        verify(service).generate("Ordering coffee");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "null",
                "{}",
                "{\"scenario\":null}",
                "{\"scenario\":\"\"}",
                "{\"scenario\":\"   \"}",
                "{\"scenario\":\"\\t\\r\\n\"}",
                "{\"scenario\":\"\u00A0\u3000\uFEFF\"}"
            })
    void rejectsInvalidInputWithoutCallingService(String body) throws Exception {
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void getNoLongerGenerates() throws Exception {
        mvc.perform(get("/api/dialogs/generate")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    private DialogueResponse validResponse() {
        var response = new DialogueResponse();
        response.setTopic("Coffee");
        response.setTargetPhrase("Could I get");
        var line = new DialogueLine();
        line.setSpeaker("Someone");
        line.setText("Hello");
        response.setDialogue(List.of(line));
        return response;
    }

    @Test
    void saveSuccessReturnsAdditiveMetadata() throws Exception {
        var response = validResponse();
        var id = java.util.UUID.randomUUID();
        when(service.generate("Coffee")).thenReturn(response);
        when(history.save(eq("Coffee"), same(response), any(), any())).thenReturn(id);
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"Coffee\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historySaved").value(true))
                .andExpect(jsonPath("$.historyId").value(id.toString()));
        verify(history).save(eq("Coffee"), same(response), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"serialization", "insert", "cleanup", "commit"})
    void storageFailureDoesNotReturnGeneratedContent(String stage) throws Exception {
        when(service.generate("Coffee")).thenReturn(validResponse());
        when(history.save(anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException(stage));
        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () ->
                        mvc.perform(
                                post("/api/dialogs/generate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"scenario\":\"Coffee\"}")));
        verify(quota).release(any());
    }

    @Test
    void incompleteOutputNeverSaved() {
        var response = validResponse();
        response.setTargetPhrase("\u00a0\ufeff");
        when(service.generate("Coffee")).thenReturn(response);
        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () ->
                        mvc.perform(
                                post("/api/dialogs/generate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"scenario\":\"Coffee\"}")));
        verifyNoInteractions(history);
    }

    @Test
    void providerFailureNeverSaved() {
        when(service.generate("Coffee")).thenThrow(new IllegalStateException("provider failed"));
        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () ->
                        mvc.perform(
                                post("/api/dialogs/generate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"scenario\":\"Coffee\"}")));
        verifyNoInteractions(history);
    }

    @Test
    void invalidIdempotencyKeysFailBeforeAdmission() throws Exception {
        for (String key : List.of(" ", "x".repeat(129))) {
            mvc.perform(
                            post("/api/dialogs/generate")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("Idempotency-Key", key)
                                    .content("{\"scenario\":\"Coffee\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(quota, service);
    }

    @Test
    void storageFailureDoesNotInvokeProvider() throws Exception {
        when(quota.admit(any(), anyString())).thenThrow(new IllegalStateException("offline"));
        when(quota.snapshot()).thenThrow(new IllegalStateException("offline"));
        mvc.perform(get("/api/dialogs/quota"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"Coffee\"}"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(service);
    }

    @Test
    void exhaustionReportsQuotaAndRetryAfter() throws Exception {
        var reset = java.time.Instant.parse("2026-11-22T16:00:00Z");
        var period = new QuotaService.Period(2, 1, 1, 0, reset);
        var snapshot =
                new QuotaService.Snapshot(
                        "Asia/Taipei", period, period, reset, List.of("daily", "weekly"));
        when(quota.snapshot()).thenReturn(snapshot);
        when(quota.admit(any(), anyString()))
                .thenReturn(
                        new QuotaService.Admission(
                                "dialogue_quota_exceeded", null, null, snapshot));
        when(quota.retryAfter(snapshot)).thenReturn(60L);
        mvc.perform(get("/api/dialogs/quota"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.daily.reserved").value(1));
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"Coffee\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.quota.exhaustedPeriods.length()").value(2));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"daily", "weekly", "both", "reserved"})
    void exhaustionReflectsEachBlockingPeriod(String kind) throws Exception {
        var reset = java.time.Instant.parse("2026-11-22T16:00:00Z");
        var available = new QuotaService.Period(10, 0, 0, 10, reset);
        var used = new QuotaService.Period(2, 2, 0, 0, reset);
        var reserved = new QuotaService.Period(2, 0, 2, 0, reset);
        var day = kind.equals("weekly") ? available : kind.equals("reserved") ? reserved : used;
        var week = kind.equals("weekly") || kind.equals("both") ? used : available;
        var exhausted =
                kind.equals("both")
                        ? List.of("daily", "weekly")
                        : List.of(kind.equals("weekly") ? "weekly" : "daily");
        var snapshot = new QuotaService.Snapshot("Asia/Taipei", day, week, reset, exhausted);
        when(quota.admit(any(), anyString()))
                .thenReturn(
                        new QuotaService.Admission(
                                "dialogue_quota_exceeded", null, null, snapshot));
        when(quota.retryAfter(snapshot)).thenReturn(1L);
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"Coffee\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.quota.exhaustedPeriods[0]").value(exhausted.getFirst()))
                .andExpect(jsonPath("$.quota.daily.reserved").value(day.reserved()));
        verifyNoInteractions(service);
    }
}
