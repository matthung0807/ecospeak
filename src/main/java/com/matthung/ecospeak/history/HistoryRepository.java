package com.matthung.ecospeak.history;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.matthung.ecospeak.dto.DialogueResponse;

@Repository
public class HistoryRepository {
    private final JdbcTemplate jdbc;
    public HistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID id, Instant time, String scenario, DialogueResponse response, String json) {
        jdbc.update("INSERT INTO dialogue_history (id, created_time, topic, target_phrase, scenario, dialogues) VALUES (?, ?, ?, ?, ?, ?)",
                id.toString(), time.atOffset(ZoneOffset.UTC), response.getTopic(), response.getTargetPhrase(), scenario, json);
    }

    public List<UUID> trim() {
        var ids = jdbc.queryForList("SELECT id FROM dialogue_history ORDER BY created_time DESC, id DESC", String.class);
        for (int i = 10; i < ids.size(); i++) {
            jdbc.update("DELETE FROM dialogue_history WHERE id = ?", ids.get(i));
        }
        return ids.stream().skip(10).map(UUID::fromString).toList();
    }

    public List<HistorySummary> list() {
        return jdbc.query("SELECT id, created_time, topic FROM dialogue_history ORDER BY created_time DESC, id DESC",
                (rs, row) -> new HistorySummary(UUID.fromString(rs.getString("id")),
                    rs.getObject("created_time", OffsetDateTime.class).toInstant(), rs.getString("topic")));
    }

    public Optional<StoredDialogue> find(UUID id) {
        return jdbc.query("SELECT * FROM dialogue_history WHERE id = ?", (rs, row) -> new StoredDialogue(
                UUID.fromString(rs.getString("id")), rs.getObject("created_time", OffsetDateTime.class).toInstant(),
                rs.getString("topic"), rs.getString("target_phrase"), rs.getString("scenario"), rs.getString("dialogues")),
                id.toString()).stream().findFirst();
    }

    public record StoredDialogue(UUID id, Instant time, String topic, String phrase, String scenario, String json) {}
}
