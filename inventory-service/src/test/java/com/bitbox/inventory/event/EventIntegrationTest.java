package com.bitbox.inventory.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bitbox.inventory.support.IntegrationTest;
import com.bitbox.inventory.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class EventIntegrationTest extends IntegrationTest {

    private static final String EVENT_JSON = """
            {"name": "Concierto", "eventDate": "2027-03-15T20:00:00", "totalTickets": 100}
            """;

    @Test
    void createEventRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createEventRejectsTamperedToken() throws Exception {
        String token = bearerTokenFor(createUser("ana@bitbox.com"));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, tampered)
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createEventInitialisesInventory() throws Exception {
        User user = createUser("ana@bitbox.com");

        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Concierto"))
                .andExpect(jsonPath("$.eventDate").value("2027-03-15T20:00:00"))
                .andExpect(jsonPath("$.totalTickets").value(100))
                .andExpect(jsonPath("$.availableTickets").value(100));
    }

    @Test
    void createEventValidatesInput() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(createUser("ana@bitbox.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "totalTickets": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.eventDate").exists())
                .andExpect(jsonPath("$.errors.totalTickets").exists());
    }

    @Test
    void getEventIsPublicAndReturnsAvailability() throws Exception {
        Event event = createEvent("Teatro", 50);

        mockMvc.perform(get("/api/events/{id}", event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()))
                .andExpect(jsonPath("$.availableTickets").value(50));
    }

    @Test
    void getUnknownEventReturns404() throws Exception {
        mockMvc.perform(get("/api/events/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    /** Regression guard: "/api/events/stats" must not be swallowed by the public "/api/events/{id}" rule. */
    @Test
    void statsIsNotPublicEvenThoughEventDetailIs() throws Exception {
        mockMvc.perform(get("/api/events/stats")).andExpect(status().isUnauthorized());
    }
}
