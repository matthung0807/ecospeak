package com.matthung.ecospeak.history;

import com.matthung.ecospeak.dto.DialogueResponse;
import com.matthung.ecospeak.entity.DialogueHistoryEntity;
import com.matthung.ecospeak.repository.DialogueHistoryJpaRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HistoryRepository {
    private final DialogueHistoryJpaRepository repository;

    public HistoryRepository(DialogueHistoryJpaRepository repository) {
        this.repository = repository;
    }

    public void insert(
            UUID id, Instant time, String scenario, DialogueResponse response, String json) {
        var row = new DialogueHistoryEntity();
        row.setId(id.toString());
        row.setCreatedTime(time.atOffset(ZoneOffset.UTC));
        row.setTopic(response.getTopic());
        row.setTargetPhrase(response.getTargetPhrase());
        row.setScenario(scenario);
        row.setDialogues(json);
        repository.saveAndFlush(row);
    }

    public List<UUID> trim() {
        var rows = repository.findAllByOrderByCreatedTimeDescIdDesc();
        var removed = rows.stream().skip(10).toList();
        repository.deleteAll(removed);
        return removed.stream().map(row -> UUID.fromString(row.getId())).toList();
    }

    public List<HistorySummary> list() {
        return repository.findAllByOrderByCreatedTimeDescIdDesc().stream()
                .map(row -> new HistorySummary(
                        UUID.fromString(row.getId()),
                        row.getCreatedTime().toInstant(),
                        row.getTopic()))
                .toList();
    }

    public Optional<StoredDialogue> find(UUID id) {
        return repository.findById(id.toString()).map(row -> new StoredDialogue(
                UUID.fromString(row.getId()),
                row.getCreatedTime().toInstant(),
                row.getTopic(),
                row.getTargetPhrase(),
                row.getScenario(),
                row.getDialogues()));
    }

    public record StoredDialogue(
            UUID id, Instant time, String topic, String phrase, String scenario, String json) {
    }
}
