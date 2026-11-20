package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matthung.ecospeak.controller.DialogueController;
import com.matthung.ecospeak.controller.HistoryController;
import com.matthung.ecospeak.dto.DialogueLine;
import com.matthung.ecospeak.dto.DialogueResponse;
import com.matthung.ecospeak.service.DialogueService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:history-tests;DB_CLOSE_DELAY=0;TRACE_LEVEL_FILE=0",
            "ecospeak.audio.directory=target/history-audio-tests"
        })
class HistoryIntegrationTests {
    @Autowired
    HistoryService history;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DialogueController generation;
    @Autowired
    HistoryController reads;
    @MockitoBean
    DialogueService provider;
    @MockitoSpyBean
    HistoryRepository repository;
    @MockitoSpyBean
    com.matthung.ecospeak.quota.QuotaService quota;
    @Autowired
    com.matthung.ecospeak.audio.AudioService audio;
    @MockitoBean
    com.matthung.ecospeak.audio.SpeechProvider speech;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM dialogue_history");
        jdbc.update("DELETE FROM dialogue_generation_request");
        mvc = MockMvcBuilders.standaloneSetup(generation, reads).build();
    }

    @Test
    void idempotencyReplaysAfterExhaustionAndHistoryEviction() throws Exception {
        when(provider.generate(anyString())).thenReturn(content("Coffee"));
        var request =
                post("/api/dialogs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "first")
                        .content("{\"scenario\":\"Coffee\"}");
        String original =
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"Tea\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"Third\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("dialogue_quota_exceeded"));
        jdbc.update("DELETE FROM dialogue_history");
        String replay =
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(original, replay);
        verify(provider, times(2)).generate(anyString());
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "first")
                                .content("{\"scenario\":\"Different\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_conflict"));
    }

    @Test
    void pendingDuplicateDoesNotInvokeProviderAgain() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(provider.generate("Coffee"))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            release.await();
                            return content("Coffee");
                        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first =
                    pool.submit(
                            () ->
                                    mvc.perform(
                                                    post("/api/dialogs/generate")
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .header("Idempotency-Key", "pending")
                                                            .content("{\"scenario\":\"Coffee\"}"))
                                            .andExpect(status().isOk()));
            try {
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                mvc.perform(
                                post("/api/dialogs/generate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "pending")
                                        .content("{\"scenario\":\"Coffee\"}"))
                        .andExpect(status().isConflict())
                        .andExpect(header().string("Retry-After", "1"))
                        .andExpect(jsonPath("$.code").value("generation_in_progress"));
                // Provider work must not hold either lock.
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM dialogue_generation_request WHERE"
                                    + " state='reserved'",
                                Integer.class));
                history.save("other", content("other"), Instant.now());
            } finally {
                release.countDown();
            }
            first.get();
        }
        verify(provider, times(1)).generate("Coffee");
    }

    @Test
    void failedAttemptCanRetrySameKeyAndUnkeyedRequestsAreIndependent() throws Exception {
        when(provider.generate("Coffee"))
                .thenThrow(new IllegalStateException("provider failure"))
                .thenReturn(content("Coffee"));
        assertThrows(
                jakarta.servlet.ServletException.class,
                () ->
                        mvc.perform(
                                post("/api/dialogs/generate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", "retry")
                                        .content("{\"scenario\":\"Coffee\"}")));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dialogue_generation_request WHERE state='reserved'",
                        Integer.class));
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "retry")
                                .content("{\"scenario\":\"Coffee\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"Coffee\"}"))
                .andExpect(status().isOk());
        verify(provider, times(3)).generate("Coffee");
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dialogue_generation_request WHERE state='succeeded'",
                        Integer.class));
    }

    @Test
    void finalizationFailureRollsBackHistoryAndPreservesAudio() throws Exception {
        when(speech.synthesize(anyString(), anyString(), any(), anyInt()))
                .thenReturn(new byte[] {1, 2});
        var oldest = history.save("scenario", content("old"), Instant.EPOCH);
        audio.prepare(oldest);
        awaitAudio(oldest);
        byte[] beforeAudio = audio.read(oldest);
        for (int i = 0; i < 9; i++) {
            history.save("scenario", content("existing"), Instant.now());
        }
        var before = history.list();
        when(provider.generate("Coffee")).thenReturn(content("new"));
        doThrow(new IllegalStateException("finalization failed"))
                .when(quota)
                .finalizeAttempt(any(), any(), anyString());
        assertThrows(
                jakarta.servlet.ServletException.class,
                () ->
                        mvc.perform(
                                post("/api/dialogs/generate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"scenario\":\"Coffee\"}")));
        assertEquals(before, history.list());
        assertArrayEquals(beforeAudio, audio.read(oldest));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dialogue_generation_request WHERE state IN"
                            + " ('reserved','succeeded')",
                        Integer.class));
    }

    static DialogueResponse content(String topic) {
        var response = new DialogueResponse();
        response.setTopic(topic);
        response.setTargetPhrase("Let's try 咖啡 ☕");
        var a = new DialogueLine();
        a.setSpeaker("A");
        a.setText("<b>Let's try</b> \"coffee\"\nplease.");
        var b = new DialogueLine();
        b.setSpeaker("B");
        b.setText("Sure!");
        response.setDialogue(List.of(a, b));
        return response;
    }

    @Test
    void roundTripAndDuplicateTopics() {
        assertTrue(history.list().isEmpty());
        var time = Instant.parse("2026-11-16T10:00:00.123456789Z");
        var first = history.save("Ordering  coffee", content("同じ topic"), time);
        var second = history.save("Other scenario", content("同じ topic"), time.plusSeconds(1));
        var detail = history.find(first).orElseThrow();
        assertEquals(time, detail.createdTime());
        assertEquals("Ordering  coffee", detail.scenario());
        assertEquals(content("同じ topic").getDialogue(), detail.dialogue());
        assertEquals("Let's try 咖啡 ☕", detail.targetPhrase());
        assertEquals(
                List.of(second, first), history.list().stream().map(HistorySummary::id).toList());
        assertNotEquals(first, second);
    }

    @Test
    void retainsNewestTenAndStableUuidTies() {
        var time = Instant.parse("2026-11-16T10:00:00Z");
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 10; i++) {
            ids.add(history.save("scenario", content("topic"), time.plusSeconds(i)));
        }
        assertEquals(10, history.list().size());
        history.save("scenario", content("eleventh"), time.plusSeconds(10));
        assertTrue(history.find(ids.getFirst()).isEmpty());
        assertEquals("eleventh", history.list().getFirst().topic());
        jdbc.update("DELETE FROM dialogue_history");
        ids.clear();
        for (int i = 0; i < 30; i++) {
            ids.add(history.save("scenario", content("tie"), time));
        }
        var expected =
                ids.stream()
                        .sorted(Comparator.comparing(UUID::toString).reversed())
                        .limit(10)
                        .toList();
        assertEquals(expected, history.list().stream().map(HistorySummary::id).toList());
        assertEquals(expected, history.list().stream().map(HistorySummary::id).toList());
    }

    @Test
    void concurrentWritersAndReadersNeverExposeElevenRows() throws Exception {
        var time = Instant.parse("2026-11-16T10:00:00Z");
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 50; i++) {
                final int number = i;
                futures.add(
                        pool.submit(
                                () -> {
                                    history.save(
                                            "scenario",
                                            content("topic " + number),
                                            time.plusSeconds(number));
                                    assertTrue(history.list().size() <= 10);
                                }));
                futures.add(pool.submit(() -> assertTrue(history.list().size() <= 10)));
            }
            for (var future : futures) {
                future.get();
            }
        }
        assertEquals(10, history.list().size());
        assertEquals(
                java.util.stream.IntStream.iterate(49, i -> i - 1)
                        .limit(10)
                        .mapToObj(i -> "topic " + i)
                        .toList(),
                history.list().stream().map(HistorySummary::topic).toList());
    }

    @Test
    void cleanupFailureRollsBackInsertionAndDeletion() {
        for (int i = 0; i < 10; i++) {
            history.save("scenario", content("old " + i), Instant.now());
        }
        var before = history.list();
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            throw new IllegalStateException("cleanup failure");
                        })
                .when(repository)
                .trim();
        assertThrows(
                IllegalStateException.class,
                () -> history.save("scenario", content("new"), Instant.now()));
        assertEquals(before, history.list());
    }

    @Test
    void generationPersistsAndReadApisNeverCallProvider() throws Exception {
        when(provider.generate("Ordering coffee")).thenReturn(content("Coffee"));
        mvc.perform(
                        post("/api/dialogs/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scenario\":\"  Ordering coffee  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historySaved").value(true))
                .andExpect(jsonPath("$.historyId").isString())
                .andExpect(jsonPath("$.topic").value("Coffee"));
        var id = history.list().getFirst().id();
        clearInvocations(provider);
        mvc.perform(get("/api/dialogs/history"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].createdTime").isString());
        mvc.perform(get("/api/dialogs/history/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("Ordering coffee"))
                .andExpect(jsonPath("$.dialogue[1].text").value("Sure!"));
        mvc.perform(get("/api/dialogs/history/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/dialogs/history/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dialogs/history/1-1-1-1-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(provider);
    }

    @Test
    void emptyListEvictedIdAndReadFailures() throws Exception {
        mvc.perform(get("/api/dialogs/history"))
                .andExpect(status().isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .json("[]"));
        var oldest = history.save("scenario", content("old"), Instant.EPOCH);
        for (int i = 0; i < 10; i++) {
            history.save("scenario", content("new"), Instant.now());
        }
        mvc.perform(get("/api/dialogs/history/" + oldest)).andExpect(status().isNotFound());
        doThrow(new IllegalStateException("db unavailable")).when(repository).list();
        mvc.perform(get("/api/dialogs/history")).andExpect(status().is5xxServerError());
        doThrow(new IllegalStateException("db unavailable")).when(repository).find(any());
        mvc.perform(get("/api/dialogs/history/" + oldest)).andExpect(status().is5xxServerError());
        verifyNoInteractions(provider);
    }

    @Test
    void corruptStoredContentIsNotReturnedAsSuccess() throws Exception {
        var id = history.save("scenario", content("topic"), Instant.now());
        jdbc.update("UPDATE dialogue_history SET dialogues = ? WHERE id = ?", "[]", id.toString());
        mvc.perform(get("/api/dialogs/history/" + id)).andExpect(status().is5xxServerError());
    }

    private void awaitAudio(UUID id) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if ("ready".equals(audio.status(id).status())) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Audio did not become ready");
    }

    @Test
    void audioSurvivesRollbackAndExpiresOnlyAfterCommittedRetention() throws Exception {
        when(speech.synthesize(anyString(), anyString(), any(), anyInt()))
                .thenReturn(new byte[] {1, 2});
        var oldest = history.save("scenario", content("old"), Instant.EPOCH);
        audio.prepare(oldest);
        awaitAudio(oldest);
        byte[] before = audio.read(oldest);
        for (int i = 0; i < 9; i++) {
            history.save("scenario", content("retained"), Instant.now());
        }
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            throw new IllegalStateException("rollback");
                        })
                .when(repository)
                .trim();
        assertThrows(
                IllegalStateException.class,
                () -> history.save("scenario", content("failed"), Instant.now()));
        assertArrayEquals(before, audio.read(oldest));
        assertTrue(history.find(oldest).isPresent());
        doCallRealMethod().when(repository).trim();
        history.save("scenario", content("committed"), Instant.now());
        assertEquals(10, history.list().size());
        assertEquals(
                404,
                assertThrows(
                                com.matthung.ecospeak.audio.AudioFailure.class,
                                () -> audio.read(oldest))
                        .status());
        assertFalse(
                java.nio.file.Files.exists(
                        java.nio.file.Path.of("target/history-audio-tests", oldest + ".wav")));
    }

    @Test
    void committedEvictionCancelsInFlightProviderAndNeverPublishes() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var exited = new java.util.concurrent.CountDownLatch(1);
        when(speech.synthesize(anyString(), anyString(), any(), anyInt()))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            try {
                                release.await();
                                return new byte[] {1, 2};
                            } finally {
                                exited.countDown();
                            }
                        });
        var oldest = history.save("scenario", content("old"), Instant.EPOCH);
        try {
            audio.prepare(oldest);
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            for (int i = 0; i < 10; i++) {
                history.save("scenario", content("new"), Instant.now());
            }
            assertTrue(exited.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(
                    java.nio.file.Files.exists(
                            java.nio.file.Path.of("target/history-audio-tests", oldest + ".wav")));
            assertEquals(
                    404,
                    assertThrows(
                                    com.matthung.ecospeak.audio.AudioFailure.class,
                                    () -> audio.status(oldest))
                            .status());
        } finally {
            release.countDown();
        }
    }
}
