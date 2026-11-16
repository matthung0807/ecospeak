package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import com.matthung.ecospeak.controller.DialogueController;
import com.matthung.ecospeak.controller.HistoryController;
import com.matthung.ecospeak.dto.DialogueLine;
import com.matthung.ecospeak.dto.DialogueResponse;
import com.matthung.ecospeak.service.DialogueService;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:history-tests;DB_CLOSE_DELAY=0;TRACE_LEVEL_FILE=0")
class HistoryIntegrationTests {
    @Autowired HistoryService history;
    @Autowired JdbcTemplate jdbc;
    @Autowired DialogueController generation;
    @Autowired HistoryController reads;
    @MockitoBean DialogueService provider;
    @MockitoSpyBean HistoryRepository repository;
    MockMvc mvc;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM dialogue_history");
        mvc = MockMvcBuilders.standaloneSetup(generation, reads).build();
    }

    static DialogueResponse content(String topic) {
        var response = new DialogueResponse();
        response.setTopic(topic);
        response.setTargetPhrase("Let's try 咖啡 ☕");
        var a = new DialogueLine(); a.setSpeaker("A"); a.setText("<b>Let's try</b> \"coffee\"\nplease.");
        var b = new DialogueLine(); b.setSpeaker("B"); b.setText("Sure!");
        response.setDialogue(List.of(a, b));
        return response;
    }

    @Test void roundTripAndDuplicateTopics() {
        assertTrue(history.list().isEmpty());
        var time = Instant.parse("2026-11-16T10:00:00.123456789Z");
        var first = history.save("Ordering  coffee", content("同じ topic"), time);
        var second = history.save("Other scenario", content("同じ topic"), time.plusSeconds(1));
        var detail = history.find(first).orElseThrow();
        assertEquals(time, detail.createdTime());
        assertEquals("Ordering  coffee", detail.scenario());
        assertEquals(content("同じ topic").getDialogue(), detail.dialogue());
        assertEquals("Let's try 咖啡 ☕", detail.targetPhrase());
        assertEquals(List.of(second, first), history.list().stream().map(HistorySummary::id).toList());
        assertNotEquals(first, second);
    }

    @Test void retainsNewestTenAndStableUuidTies() {
        var time = Instant.parse("2026-11-16T10:00:00Z");
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 10; i++) ids.add(history.save("scenario", content("topic"), time.plusSeconds(i)));
        assertEquals(10, history.list().size());
        history.save("scenario", content("eleventh"), time.plusSeconds(10));
        assertTrue(history.find(ids.getFirst()).isEmpty());
        assertEquals("eleventh", history.list().getFirst().topic());
        jdbc.update("DELETE FROM dialogue_history");
        ids.clear();
        for (int i = 0; i < 30; i++) ids.add(history.save("scenario", content("tie"), time));
        var expected = ids.stream().sorted(Comparator.comparing(UUID::toString).reversed()).limit(10).toList();
        assertEquals(expected, history.list().stream().map(HistorySummary::id).toList());
        assertEquals(expected, history.list().stream().map(HistorySummary::id).toList());
    }

    @Test void concurrentWritersAndReadersNeverExposeElevenRows() throws Exception {
        var time = Instant.parse("2026-11-16T10:00:00Z");
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 50; i++) {
                final int number = i;
                futures.add(pool.submit(() -> {
                    history.save("scenario", content("topic " + number), time.plusSeconds(number));
                    assertTrue(history.list().size() <= 10);
                }));
                futures.add(pool.submit(() -> assertTrue(history.list().size() <= 10)));
            }
            for (var future : futures) future.get();
        }
        assertEquals(10, history.list().size());
        assertEquals(java.util.stream.IntStream.iterate(49, i -> i - 1).limit(10).mapToObj(i -> "topic " + i).toList(),
                history.list().stream().map(HistorySummary::topic).toList());
    }

    @Test void cleanupFailureRollsBackInsertionAndDeletion() {
        for (int i = 0; i < 10; i++) history.save("scenario", content("old " + i), Instant.now());
        var before = history.list();
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("cleanup failure"); }).when(repository).trim();
        assertThrows(IllegalStateException.class, () -> history.save("scenario", content("new"), Instant.now()));
        assertEquals(before, history.list());
    }

    @Test void generationPersistsAndReadApisNeverCallProvider() throws Exception {
        when(provider.generate("Ordering coffee")).thenReturn(content("Coffee"));
        mvc.perform(post("/api/dialogs/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenario\":\"  Ordering coffee  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.historySaved").value(true))
                .andExpect(jsonPath("$.historyId").isString()).andExpect(jsonPath("$.topic").value("Coffee"));
        var id = history.list().getFirst().id();
        clearInvocations(provider);
        mvc.perform(get("/api/dialogs/history")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].createdTime").isString());
        mvc.perform(get("/api/dialogs/history/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("Ordering coffee"))
                .andExpect(jsonPath("$.dialogue[1].text").value("Sure!"));
        mvc.perform(get("/api/dialogs/history/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/dialogs/history/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dialogs/history/1-1-1-1-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(provider);
    }

    @Test void emptyListEvictedIdAndReadFailures() throws Exception {
        mvc.perform(get("/api/dialogs/history")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));
        var oldest = history.save("scenario", content("old"), Instant.EPOCH);
        for (int i = 0; i < 10; i++) history.save("scenario", content("new"), Instant.now());
        mvc.perform(get("/api/dialogs/history/" + oldest)).andExpect(status().isNotFound());
        doThrow(new IllegalStateException("db unavailable")).when(repository).list();
        mvc.perform(get("/api/dialogs/history")).andExpect(status().is5xxServerError());
        doThrow(new IllegalStateException("db unavailable")).when(repository).find(any());
        mvc.perform(get("/api/dialogs/history/" + oldest)).andExpect(status().is5xxServerError());
        verifyNoInteractions(provider);
    }

    @Test void corruptStoredContentIsNotReturnedAsSuccess() throws Exception {
        var id = history.save("scenario", content("topic"), Instant.now());
        jdbc.update("UPDATE dialogue_history SET dialogues = ? WHERE id = ?", "[]", id.toString());
        mvc.perform(get("/api/dialogs/history/" + id)).andExpect(status().is5xxServerError());
    }
}
