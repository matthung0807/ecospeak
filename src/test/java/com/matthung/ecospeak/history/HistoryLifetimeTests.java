package com.matthung.ecospeak.history;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import com.zaxxer.hikari.HikariDataSource;
import tools.jackson.databind.json.JsonMapper;

class HistoryLifetimeTests {
    @TempDir Path directory;

    @TestConfiguration(proxyBeanMethods = false)
    @Import({HistoryService.class, HistoryRepository.class})
    static class StorageContext {
        @Bean(destroyMethod = "close") HikariDataSource dataSource() throws Exception {
            var properties = new java.util.Properties();
            try (var stream = new ClassPathResource("application.properties").getInputStream()) {
                properties.load(stream);
            }
            var datasource = new HikariDataSource();
            datasource.setJdbcUrl(properties.getProperty("spring.datasource.url").replace("mem:ecospeak", "mem:lifetime-test"));
            datasource.setUsername("sa"); datasource.setPassword("");
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(datasource);
            return datasource;
        }
        @Bean JdbcTemplate jdbc(HikariDataSource datasource) { return new JdbcTemplate(datasource); }
        @Bean DataSourceTransactionManager transactions(HikariDataSource datasource) {
            return new DataSourceTransactionManager(datasource);
        }
        @Bean JsonMapper mapper() { return JsonMapper.builder().build(); }
    }

    @Test void closingContextDestroysDatabaseWithoutCreatingFiles() throws Exception {
        String previous = System.getProperty("user.dir");
        UUID id;
        try {
            System.setProperty("user.dir", directory.toString());
            try (var first = new AnnotationConfigApplicationContext(StorageContext.class)) {
                assertTrue(first.getBean(HikariDataSource.class).getJdbcUrl().startsWith("jdbc:h2:mem:"));
                id = first.getBean(HistoryService.class).save("scenario", HistoryIntegrationTests.content("topic"), Instant.now());
                assertTrue(first.getBean(HistoryService.class).find(id).isPresent());
            }
            try (var restarted = new AnnotationConfigApplicationContext(StorageContext.class)) {
                assertTrue(restarted.getBean(HistoryService.class).list().isEmpty());
                assertTrue(restarted.getBean(HistoryService.class).find(id).isEmpty());
            }
            try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        } finally { System.setProperty("user.dir", previous); }
    }
}
