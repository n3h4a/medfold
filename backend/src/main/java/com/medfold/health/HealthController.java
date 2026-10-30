package com.medfold.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medfold.ai.AiServiceHealth;
import com.medfold.processing.ProcessingProperties;

/**
 * Lightweight health check used by the frontend and local scripts.
 * Reports whether the database is reachable, which Flyway migration is applied, whether the
 * AI service answers, and whether this instance processes documents.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final String SCHEMA_VERSION_SQL = """
            SELECT version FROM flyway_schema_history
            WHERE success = true AND version IS NOT NULL
            ORDER BY installed_rank DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AiServiceHealth aiServiceHealth;
    private final ProcessingProperties processingProperties;

    public HealthController(JdbcTemplate jdbcTemplate, AiServiceHealth aiServiceHealth,
            ProcessingProperties processingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiServiceHealth = aiServiceHealth;
        this.processingProperties = processingProperties;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "medfold-backend");
        // Reported but not part of "status": the API keeps working while the AI service is down;
        // documents just wait in the queue.
        body.put("aiService", aiServiceHealth.availability().name());
        body.put("processing", processingProperties.enabled() ? "ON" : "OFF");
        try {
            String schemaVersion = jdbcTemplate.queryForObject(SCHEMA_VERSION_SQL, String.class);
            body.put("status", "UP");
            body.put("database", "UP");
            body.put("schemaVersion", schemaVersion);
            return ResponseEntity.ok(body);
        } catch (DataAccessException ex) {
            body.put("status", "DOWN");
            body.put("database", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
