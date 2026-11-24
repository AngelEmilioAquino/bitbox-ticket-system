package com.bitbox.inventory.support;

import com.bitbox.inventory.event.Event;
import com.bitbox.inventory.event.EventRepository;
import com.bitbox.inventory.security.JwtService;
import com.bitbox.inventory.user.User;
import com.bitbox.inventory.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.LocalDateTime;

/**
 * Base for integration tests: real PostgreSQL (Testcontainers) so that Liquibase migrations, native SQL
 * (CTE / window functions) and row-level locking behave exactly as in production. H2 would not prove any of it.
 *The container is started once per JVM (singleton pattern) and shared by every test class,
 * which also lets Spring reuse a single application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // The application has no default secrets; tests supply throwaway values.
        // The datasource values are placeholders: @ServiceConnection points the app at the Testcontainers DB.
        "security.jwt.secret=test-only-secret-not-used-anywhere-else-0123456789",
        "spring.datasource.url=jdbc:postgresql://overridden-by-testcontainers/db",
        "spring.datasource.username=overridden",
        "spring.datasource.password=overridden",
        "spring.rabbitmq.username=overridden",
        "spring.rabbitmq.password=overridden"
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    /** Real broker so the after-commit publishing can be verified end to end. */
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine");

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE ticket_sales, events, users RESTART IDENTITY CASCADE");
    }

    /** BCrypt is deliberately slow: hash once and reuse it for fixture users. */
    private String fixturePasswordHash;

    protected User createUser(String email) {
        if (fixturePasswordHash == null) {
            fixturePasswordHash = passwordEncoder.encode("irrelevant-password");
        }
        return userRepository.save(new User(email, fixturePasswordHash));
    }

    protected Event createEvent(String name, int totalTickets) {
        return eventRepository.save(new Event(name, LocalDateTime.of(2027, 1, 1, 20, 0), totalTickets));
    }

    protected String bearerTokenFor(User user) {
        return "Bearer " + jwtService.issueToken(user).accessToken();
    }

    protected int availableTickets(Long eventId) {
        return jdbcTemplate.queryForObject("SELECT available_tickets FROM events WHERE id = ?", Integer.class, eventId);
    }

    protected long salesCount(Long eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_sales WHERE event_id = ?", Long.class, eventId);
    }
}
