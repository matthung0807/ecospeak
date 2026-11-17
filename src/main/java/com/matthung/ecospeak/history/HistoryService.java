package com.matthung.ecospeak.history;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.matthung.ecospeak.dto.DialogueLine;
import com.matthung.ecospeak.dto.DialogueResponse;
import com.matthung.ecospeak.service.DialogueValidation;

@Service
public class HistoryService {
    private final HistoryRepository repository;
    private final JsonMapper mapper;
    private final TransactionTemplate transaction;
    private final Object writerLock = new Object();
    private final java.util.List<java.util.function.Consumer<List<UUID>>> evictionListeners = new java.util.ArrayList<>();

    public HistoryService(HistoryRepository repository, JsonMapper mapper, PlatformTransactionManager manager) {
        this.repository = repository;
        this.mapper = mapper;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public UUID save(String scenario, DialogueResponse response, Instant time) {
        DialogueValidation.requireComplete(response);
        String json = mapper.writeValueAsString(response.getDialogue());
        UUID id = UUID.randomUUID();
        // Hold the lock through commit, not merely until the transactional method returns.
        synchronized (writerLock) {
            List<UUID> evicted = transaction.execute(status -> {
                repository.insert(id, time, scenario, response, json);
                return repository.trim();
            });
            // Commit has succeeded. Cache cleanup must never change the outcome of a text save.
            if (evicted != null && !evicted.isEmpty()) {
                for (var listener : evictionListeners) {
                    try { listener.accept(evicted); }
                    catch (RuntimeException failure) {
                        org.slf4j.LoggerFactory.getLogger(HistoryService.class).warn("Audio eviction cleanup deferred ({})", failure.getClass().getSimpleName());
                    }
                }
            }
        }
        return id;
    }

    public List<HistorySummary> list() { return repository.list(); }

    /** Short lifecycle operations only: never make provider calls while holding this lock. */
    public <T> T coordinated(java.util.function.Supplier<T> operation) {
        synchronized (writerLock) { return operation.get(); }
    }

    public void onEviction(java.util.function.Consumer<List<UUID>> listener) {
        synchronized (writerLock) { evictionListeners.add(listener); }
    }

    public Optional<HistoryDetail> find(UUID id) {
        return repository.find(id).map(stored -> {
            List<DialogueLine> lines = mapper.readValue(stored.json(), new TypeReference<List<DialogueLine>>() {});
            var response = new DialogueResponse();
            response.setTopic(stored.topic());
            response.setTargetPhrase(stored.phrase());
            response.setDialogue(lines);
            DialogueValidation.requireComplete(response);
            return new HistoryDetail(stored.id(), stored.time(), stored.topic(), stored.phrase(), stored.scenario(), lines);
        });
    }
}
