package com.bitbox.inventory.sale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bitbox.inventory.event.Event;
import com.bitbox.inventory.support.IntegrationTest;
import com.bitbox.inventory.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class PurchaseIntegrationTest extends IntegrationTest {

    @Test
    void purchaseRequiresJwt() throws Exception {
        Event event = createEvent("Concierto", 10);

        mockMvc.perform(post("/api/events/{id}/purchase", event.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void purchaseTakesBuyerFromJwtAndDecrementsStock() throws Exception {
        User user = createUser("ana@bitbox.com");
        Event event = createEvent("Concierto", 10);

        mockMvc.perform(post("/api/events/{id}/purchase", event.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saleId").isNumber())
                .andExpect(jsonPath("$.eventId").value(event.getId()))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.availableTickets").value(9));
    }

    @Test
    void purchaseOnSoldOutEventReturns409() throws Exception {
        User user = createUser("ana@bitbox.com");
        Event event = createEvent("Concierto", 1);
        String token = bearerTokenFor(user);

        mockMvc.perform(post("/api/events/{id}/purchase", event.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/events/{id}/purchase", event.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"));
    }

    @Test
    void purchaseOfUnknownEventReturns404() throws Exception {
        mockMvc.perform(post("/api/events/{id}/purchase", 999_999)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(createUser("ana@bitbox.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }
}
