package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class QuotaSchemaTests {
    @Test
    void ledgerIsIndependentAndFreshDatabaseIsEmpty() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (var sql = connection.createStatement()) {
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM dialogue_quota_lock")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
                sql.executeUpdate(
                        "INSERT INTO dialogue_history VALUES ('history', CURRENT_TIMESTAMP,"
                            + " 'topic', 'phrase', 'scenario', '[]')");
                String values =
                        "'request', 'key', 'scenario', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
                            + " CURRENT_TIMESTAMP, 'attempt', 'succeeded', 'history', '{}'";
                sql.executeUpdate(
                        "INSERT INTO dialogue_generation_request VALUES (" + values + ")");
                assertThrows(
                        java.sql.SQLException.class,
                        () ->
                                sql.executeUpdate(
                                        "INSERT INTO dialogue_generation_request VALUES ('other',"
                                            + " 'key', 'scenario', CURRENT_TIMESTAMP,"
                                            + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'attempt',"
                                            + " 'reserved', NULL, NULL)"));
                sql.executeUpdate("DELETE FROM dialogue_history");
                try (var rows =
                        sql.executeQuery("SELECT COUNT(*) FROM dialogue_generation_request")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
            }
        }
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (var sql = connection.createStatement();
                    var rows =
                            sql.executeQuery("SELECT COUNT(*) FROM dialogue_generation_request")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
    }
}
