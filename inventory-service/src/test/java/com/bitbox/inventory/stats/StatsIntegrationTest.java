package com.bitbox.inventory.stats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bitbox.inventory.event.Event;
import com.bitbox.inventory.sale.PurchaseService;
import com.bitbox.inventory.support.IntegrationTest;
import com.bitbox.inventory.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

class StatsIntegrationTest extends IntegrationTest {

    @Autowired
    private PurchaseService purchaseService;

    /**
     * Rock:   4 tickets sold by 2 unique buyers out of 10 -> 40.00 %, rank 1
     * Jazz:   2 sold by 2 buyers out of 4                  -> 50.00 %, rank 2 (tie)
     * Opera:  2 sold by 1 buyer  out of 8                  -> 25.00 %, rank 2 (tie)
     * Empty:  no sales                                     ->  0.00 %, rank 4 (still listed)
     */
    @Test
    void returnsPerEventStatisticsComputedByTheNativeQuery() throws Exception {
        User ana = createUser("ana@bitbox.com");
        User bob = createUser("bob@bitbox.com");
        Event rock = createEvent("Rock", 10);
        Event jazz = createEvent("Jazz", 4);
        Event opera = createEvent("Opera", 8);
        createEvent("Empty", 5);

        buy(rock, ana, 3);
        buy(rock, bob, 1);
        buy(jazz, ana, 1);
        buy(jazz, bob, 1);
        buy(opera, bob, 2);

        mockMvc.perform(get("/api/events/stats").header(HttpHeaders.AUTHORIZATION, bearerTokenFor(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))

                .andExpect(jsonPath("$[0].eventName").value("Rock"))
                .andExpect(jsonPath("$[0].ticketsSold").value(4))
                .andExpect(jsonPath("$[0].uniqueBuyers").value(2))
                .andExpect(jsonPath("$[0].occupancyPercentage").value(40.00))
                .andExpect(jsonPath("$[0].availableTickets").value(6))
                .andExpect(jsonPath("$[0].salesRank").value(1))

                .andExpect(jsonPath("$[1].eventName").value("Jazz"))
                .andExpect(jsonPath("$[1].ticketsSold").value(2))
                .andExpect(jsonPath("$[1].uniqueBuyers").value(2))
                .andExpect(jsonPath("$[1].occupancyPercentage").value(50.00))
                .andExpect(jsonPath("$[1].salesRank").value(2))

                .andExpect(jsonPath("$[2].eventName").value("Opera"))
                .andExpect(jsonPath("$[2].ticketsSold").value(2))
                .andExpect(jsonPath("$[2].uniqueBuyers").value(1))
                .andExpect(jsonPath("$[2].occupancyPercentage").value(25.00))
                .andExpect(jsonPath("$[2].salesRank").value(2))

                .andExpect(jsonPath("$[3].eventName").value("Empty"))
                .andExpect(jsonPath("$[3].ticketsSold").value(0))
                .andExpect(jsonPath("$[3].uniqueBuyers").value(0))
                .andExpect(jsonPath("$[3].occupancyPercentage").value(0.00))
                .andExpect(jsonPath("$[3].salesRank").value(4));
    }

    private void buy(Event event, User user, int times) {
        for (int i = 0; i < times; i++) {
            purchaseService.purchase(event.getId(), user.getId());
        }
    }
}
