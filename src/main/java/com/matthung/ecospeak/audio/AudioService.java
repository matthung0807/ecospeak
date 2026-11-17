package com.matthung.ecospeak.audio;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import com.matthung.ecospeak.history.HistoryDetail;
import com.matthung.ecospeak.history.HistoryService;

@Service
public class AudioService {
    public record State(String status, String statusUrl, String audioUrl, String code) {}
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AudioService.class);
    private final HistoryService history;
    private final AudioProperties config;
    private final SpeechProvider provider;
    private final AudioCache cache;
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor timer;
    // All job/state/publication operations share the history lifecycle lock.
    private final Map<UUID, Job> jobs = new HashMap<>();
    private boolean cacheAvailable;
    private boolean closed;
    private static final class Job {
        FutureTask<Void> task;
        ScheduledFuture<?> timeout;
        String status = "generating";
        String code;
    }

    public AudioService(HistoryService history, AudioProperties config, SpeechProvider provider, AudioCache cache) {
        this.history = history; this.config = config; this.provider = provider; this.cache = cache;
        config.validate();
        workers = new ThreadPoolExecutor(config.getWorkers(), config.getWorkers(), 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getPending()), Thread.ofPlatform().daemon().name("dialogue-audio-", 0).factory());
        timer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("audio-deadlines-", 0).factory());
        timer.setRemoveOnCancelPolicy(true);
        history.onEviction(this::evict);
        reconcile();
        timer.scheduleWithFixedDelay(this::reconcile, 30, 30, TimeUnit.SECONDS);
    }
    private HistoryDetail require(UUID id) {
        return history.find(id).orElseThrow(() -> new AudioFailure(404, "dialogue_not_found"));
    }
    private State state(UUID id, String status, String code) {
        String base = "/api/dialogs/history/" + id + "/audio";
        return new State(status, base + "/status", "ready".equals(status) ? base : null, code);
    }
    private State current(UUID id) throws IOException {
        Job job = jobs.get(id);
        if (job != null && "generating".equals(job.status)) return state(id, "generating", null);
        if (cache.ready(id)) return state(id, "ready", null);
        if (job != null && "failed".equals(job.status)) return state(id, "failed", job.code);
        return state(id, "not_generated", null);
    }
    public State status(UUID id) {
        return history.coordinated(() -> {
            require(id);
            try { return current(id); }
            catch (IOException failure) { throw new AudioFailure(503, "audio_storage_unavailable"); }
        });
    }
    public State prepare(UUID id) {
        return history.coordinated(() -> {
            var detail = require(id);
            if (closed || !cacheAvailable) throw new AudioFailure(503, "audio_storage_unavailable");
            try {
                var existing = current(id);
                if ("ready".equals(existing.status()) || "generating".equals(existing.status())) return existing;
            } catch (IOException failure) { throw new AudioFailure(503, "audio_storage_unavailable"); }
            long characters = 0;
            for (var line : detail.dialogue()) {
                if (!Set.of("A", "B").contains(line.getSpeaker())) throw new AudioFailure(422, "unsupported_speaker");
                characters += line.getText().length();
            }
            if (characters > config.getMaxInputCharacters()) throw new AudioFailure(422, "audio_input_too_large");
            var job = new Job();
            long deadline = System.nanoTime() + config.getDeadline().toNanos();
            job.task = new FutureTask<>(() -> { generate(id, detail, job, deadline); return null; });
            jobs.put(id, job);
            try {
                workers.execute(job.task);
                job.timeout = timer.schedule(() -> history.coordinated(() -> {
                    if (jobs.get(id) == job && "generating".equals(job.status)) {
                        job.status = "failed"; job.code = "audio_timeout";
                        job.task.cancel(true); workers.remove(job.task);
                    }
                    return null;
                }), config.getDeadline().toNanos(), TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException full) {
                jobs.remove(id, job); job.task.cancel(true); workers.remove(job.task);
                throw new AudioFailure(503, "audio_capacity_exhausted");
            }
            return state(id, "generating", null);
        });
    }
    private void generate(UUID id, HistoryDetail detail, Job job, long deadline) {
        try {
            byte[] wav = WavAssembler.assemble(detail.dialogue(), provider, config, deadline);
            history.coordinated(() -> {
                if (jobs.get(id) != job || !"generating".equals(job.status) || closed) return null;
                if (deadline - System.nanoTime() <= 0) throw new AudioFailure(504, "audio_timeout");
                require(id);
                try { cache.publish(id, wav); }
                catch (IOException failure) { throw new AudioFailure(503, "audio_storage_unavailable"); }
                job.status = "ready"; job.timeout.cancel(false);
                return null;
            });
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            history.coordinated(() -> {
                if (jobs.get(id) == job && "generating".equals(job.status)) {
                    job.status = "failed";
                    Throwable cause = failure instanceof ExecutionException ? failure.getCause() : failure;
                    job.code = cause instanceof AudioFailure audio ? audio.code() : "audio_generation_failed";
                    if (job.timeout != null) job.timeout.cancel(false);
                    log.warn("Audio preparation failed ({})", job.code);
                }
                return null;
            });
        }
    }
    public byte[] read(UUID id) {
        return history.coordinated(() -> {
            require(id);
            try { return cache.read(id); }
            catch (IOException failure) { throw new AudioFailure(503, "audio_storage_unavailable"); }
        });
    }
    private void evict(List<UUID> ids) {
        for (UUID id : ids) {
            Job job = jobs.remove(id);
            if (job != null) {
                job.task.cancel(true); workers.remove(job.task);
                if (job.timeout != null) job.timeout.cancel(false);
            }
            try { cache.remove(id); }
            catch (IOException failure) { log.warn("Audio cache deletion deferred"); }
        }
    }
    void reconcile() {
        history.coordinated(() -> {
            if (closed) return null;
            try {
                var retained = new HashSet<UUID>();
                history.list().forEach(item -> retained.add(item.id()));
                var expired = jobs.keySet().stream().filter(id -> !retained.contains(id)).toList();
                evict(expired);
                cache.reconcile(retained); cacheAvailable = true;
            } catch (Exception failure) {
                cacheAvailable = false;
                log.warn("Audio cache reconciliation deferred ({})", failure.getClass().getSimpleName());
            }
            return null;
        });
    }
    @PreDestroy public void close() {
        history.coordinated(() -> {
            closed = true;
            jobs.values().forEach(job -> { job.task.cancel(true); if (job.timeout != null) job.timeout.cancel(false); });
            workers.shutdownNow(); timer.shutdownNow();
            return null;
        });
    }
}
