package com.matthung.ecospeak.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matthung.ecospeak.controller.AudioController;
import com.matthung.ecospeak.history.HistoryDetail;
import com.matthung.ecospeak.history.HistoryService;
import com.matthung.ecospeak.history.HistorySummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AudioServiceTests {
    @TempDir
    Path directory;
    final Map<UUID, HistoryDetail> records = new ConcurrentHashMap<>();

    HistoryService history() {
        var history = mock(HistoryService.class);
        var lock = new Object();
        when(history.coordinated(any()))
                .thenAnswer(
                        call -> {
                            synchronized (lock) {
                                return ((java.util.function.Supplier<?>) call.getArgument(0)).get();
                            }
                        });
        when(history.find(any()))
                .thenAnswer(call -> Optional.ofNullable(records.get(call.getArgument(0))));
        when(history.list())
                .thenAnswer(
                        call ->
                                records.values().stream()
                                        .map(
                                                r ->
                                                        new HistorySummary(
                                                                r.id(), r.createdTime(), r.topic()))
                                        .toList());
        return history;
    }

    AudioProperties config() {
        var config = new AudioProperties();
        config.setDirectory(directory);
        return config;
    }

    UUID record(String speaker) {
        var id = UUID.randomUUID();
        records.put(
                id,
                new HistoryDetail(
                        id,
                        Instant.now(),
                        "Topic",
                        "Phrase",
                        "Scenario",
                        List.of(SpeechTests.line(speaker, "Hello"))));
        return id;
    }

    static AudioService.State await(AudioService service, UUID id, String expected)
            throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            var state = service.status(id);
            if (expected.equals(state.status())) {
                return state;
            }
            Thread.sleep(10);
        }
        fail("Expected audio status " + expected);
        return null;
    }

    @Test
    void apiPreparesOneFileSharesJobAndReusesCache() throws Exception {
        var id = record("A");
        var config = config();
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SpeechProvider provider =
                (t, v, d, l) -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    release.await();
                    return new byte[] {1, 2};
                };
        try (var unused =
                new CloseableService(
                        new AudioService(history(), config, provider, new AudioCache(config)))) {
            var service = unused.service;
            var mvc = MockMvcBuilders.standaloneSetup(new AudioController(service)).build();
            String base = "/api/dialogs/history/" + id + "/audio";
            mvc.perform(get(base + "/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("not_generated"));
            mvc.perform(get(base)).andExpect(status().isNotFound());
            assertEquals(0, calls.get());
            mvc.perform(post(base))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("generating"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            mvc.perform(post(base)).andExpect(status().isAccepted());
            assertEquals(1, calls.get());
            release.countDown();
            await(service, id, "ready");
            mvc.perform(get(base))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "audio/wav"))
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(post(base))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.audioUrl").value(base));
            assertEquals(1, calls.get());
            assertTrue(Files.size(directory.resolve(id + ".wav")) > 44);
            Files.delete(directory.resolve(id + ".wav"));
            assertEquals("not_generated", service.status(id).status());
            mvc.perform(post("/api/dialogs/history/invalid/audio"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/dialogs/history/" + UUID.randomUUID() + "/audio"))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/dialogs/history/" + record("Someone") + "/audio"))
                    .andExpect(status().isUnprocessableContent());
        } finally {
            release.countDown();
        }
    }

    @Test
    void failureRetryLimitsAndDeadlineReleaseWork() throws Exception {
        var config = config();
        config.setWorkers(1);
        config.setPending(1);
        config.setDeadline(Duration.ofMillis(100));
        var calls = new AtomicInteger();
        SpeechProvider provider =
                (t, v, d, l) -> {
                    if (calls.incrementAndGet() == 1) {
                        Thread.sleep(5000);
                    }
                    return new byte[] {1, 2};
                };
        try (var close =
                new CloseableService(
                        new AudioService(history(), config, provider, new AudioCache(config)))) {
            var service = close.service;
            var id = record("A");
            service.prepare(id);
            assertEquals("audio_timeout", await(service, id, "failed").code());
            service.prepare(id);
            await(service, id, "ready");
            assertEquals(2, calls.get());
            config.setMaxInputCharacters(1);
            assertEquals(
                    422,
                    assertThrows(AudioFailure.class, () -> service.prepare(record("A"))).status());
        }
    }

    @Test
    void capacityExhaustionIsRetryableAndExpiredWorkCannotPublish() throws Exception {
        var config = config();
        config.setWorkers(1);
        config.setPending(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SpeechProvider provider =
                (t, v, d, l) -> {
                    entered.countDown();
                    release.await();
                    return new byte[] {1, 2};
                };
        var history = history();
        try (var close =
                new CloseableService(
                        new AudioService(history, config, provider, new AudioCache(config)))) {
            var service = close.service;
            var first = record("A");
            var second = record("A");
            service.prepare(first);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            service.prepare(second);
            var mvc = MockMvcBuilders.standaloneSetup(new AudioController(service)).build();
            mvc.perform(post("/api/dialogs/history/" + record("A") + "/audio"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "5"));
            records.remove(first);
            service.reconcile();
            release.countDown();
            await(service, second, "ready");
            assertFalse(Files.exists(directory.resolve(first + ".wav")));
            assertEquals(404, assertThrows(AudioFailure.class, () -> service.read(first)).status());
        } finally {
            release.countDown();
        }
    }

    @Test
    void cacheCleanupAndRetryAfterStorageFailureLeaveOtherFilesAlone() throws Exception {
        var config = config();
        var orphan = UUID.randomUUID();
        Files.write(directory.resolve(orphan + ".wav"), WavAssembler.wrap(new byte[] {1, 2}));
        Files.writeString(directory.resolve("keep.txt"), "unrelated");
        var cache = spy(new AudioCache(config));
        doThrow(new java.io.IOException("locked")).doCallRealMethod().when(cache).reconcile(any());
        try (var close =
                new CloseableService(
                        new AudioService(
                                history(), config, (t, v, d, l) -> new byte[] {1, 2}, cache))) {
            var service = close.service;
            var id = record("A");
            assertEquals(503, assertThrows(AudioFailure.class, () -> service.prepare(id)).status());
            service.reconcile();
            service.prepare(id);
            await(service, id, "ready");
            assertFalse(Files.exists(directory.resolve(orphan + ".wav")));
            assertTrue(Files.exists(directory.resolve("keep.txt")));
            records.remove(id);
            service.reconcile();
            assertFalse(Files.exists(directory.resolve(id + ".wav")));
        }
    }

    @Test
    void cacheDoesNotServeTemporaryOrTraverseSymlinks() throws Exception {
        var config = config();
        var cache = new AudioCache(config);
        var id = UUID.randomUUID();
        Files.write(directory.resolve(id + ".tmp"), WavAssembler.wrap(new byte[] {1, 2}));
        assertFalse(cache.ready(id));
        assertThrows(AudioFailure.class, () -> cache.read(id));
        // Symbolic link creation may require Windows developer mode; test when supported.
        Path external = Files.createTempDirectory("ecospeak-audio-external");
        try {
            try {
                Files.createSymbolicLink(
                        directory.resolve(id + ".wav"), external.resolve("outside.wav"));
            } catch (java.io.IOException | UnsupportedOperationException denied) {
                return;
            }
            assertThrows(
                    java.io.IOException.class,
                    () -> cache.publish(id, WavAssembler.wrap(new byte[] {1, 2})));
            assertFalse(Files.exists(external.resolve("outside.wav")));
        } finally {
            Files.deleteIfExists(external);
        }
    }

    record CloseableService(AudioService service) implements AutoCloseable {
        public void close() {
            service.close();
        }
    }
}
