package com.matthung.ecospeak.history;

import com.matthung.ecospeak.dto.DialogueResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryTransaction {
    private final HistoryRepository repository;

    public HistoryTransaction(HistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public List<UUID> save(
            UUID id,
            Instant time,
            String scenario,
            DialogueResponse response,
            String json,
            Consumer<UUID> beforeCommit) {
        repository.insert(id, time, scenario, response, json);
        var removed = repository.trim();
        beforeCommit.accept(id);
        return removed;
    }
}
