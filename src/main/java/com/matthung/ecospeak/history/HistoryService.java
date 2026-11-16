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
            transaction.executeWithoutResult(status -> {
                repository.insert(id, time, scenario, response, json);
                repository.trim();
            });
        }
        return id;
    }

    public List<HistorySummary> list() { return repository.list(); }

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
