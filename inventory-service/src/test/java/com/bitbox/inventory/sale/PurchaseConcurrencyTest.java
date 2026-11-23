package com.bitbox.inventory.sale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.bitbox.inventory.common.ApiException;
import com.bitbox.inventory.event.Event;
import com.bitbox.inventory.support.IntegrationTest;
import com.bitbox.inventory.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;

/**
 * Proves that simultaneous purchases can never oversell and never drive availableTickets below zero.
 * All buyers are released at the same instant by a {@link CountDownLatch} to maximise contention.
 */
class PurchaseConcurrencyTest extends IntegrationTest {

    @Autowired
    private PurchaseService purchaseService;

    @LocalServerPort
    private int port;

    @Test
    void manyBuyersForFewTicketsSellExactlyTheStock() throws Exception {
        int tickets = 10;
        int buyers = 50;
        Event event = createEvent("Last tickets", tickets);
        List<User> users = createUsers(buyers);

        Map<String, AtomicInteger> outcomes = runConcurrently(buyers, i -> {
            try {
                purchaseService.purchase(event.getId(), users.get(i).getId());
                return "SOLD";
            } catch (ApiException ex) {
                return ex.getCode();
            }
        });

        assertThat(count(outcomes, "SOLD")).isEqualTo(tickets);
        assertThat(count(outcomes, "SOLD_OUT")).isEqualTo(buyers - tickets);
        assertThat(availableTickets(event.getId())).isZero();
        assertThat(salesCount(event.getId())).isEqualTo(tickets);
    }

    /** Only one ticket left and many simultaneous buyers: exactly one wins. */
    @Test
    void theLastTicketIsSoldExactlyOnce() throws Exception {
        Event event = createEvent("One left", 1);
        List<User> users = createUsers(20);

        Map<String, AtomicInteger> outcomes = runConcurrently(20, i -> {
            try {
                purchaseService.purchase(event.getId(), users.get(i).getId());
                return "SOLD";
            } catch (ApiException ex) {
                return ex.getCode();
            }
        });

        assertThat(count(outcomes, "SOLD")).isEqualTo(1);
        assertThat(count(outcomes, "SOLD_OUT")).isEqualTo(19);
        assertThat(availableTickets(event.getId())).isZero();
        assertThat(salesCount(event.getId())).isEqualTo(1);
    }

    /**
     * As many buyers as tickets: every purchase must succeed. Guards against the retry strategy
     * losing legitimate sales (an optimistic conflict must never be reported as "sold out").
     */
    @Test
    void retriesDoNotLoseLegitimateSales() throws Exception {
        int tickets = 20;
        Event event = createEvent("Exact demand", tickets);
        List<User> users = createUsers(tickets);

        Map<String, AtomicInteger> outcomes = runConcurrently(tickets, i -> {
            try {
                purchaseService.purchase(event.getId(), users.get(i).getId());
                return "SOLD";
            } catch (ApiException ex) {
                return ex.getCode();
            }
        });

        assertThat(count(outcomes, "SOLD")).isEqualTo(tickets);
        assertThat(availableTickets(event.getId())).isZero();
        assertThat(salesCount(event.getId())).isEqualTo(tickets);
    }

    /** End-to-end through HTTP (security filter, JWT, controller, error handler): 201s plus 409s. */
    @Test
    void concurrentHttpPurchasesReturn201ForStockAnd409ForTheRest() throws Exception {
        int tickets = 5;
        int buyers = 30;
        Event event = createEvent("HTTP rush", tickets);
        List<User> users = createUsers(buyers);
        List<String> tokens = users.stream().map(this::bearerTokenFor).toList();
        HttpClient http = HttpClient.newHttpClient();
        URI uri = URI.create("http://localhost:" + port + "/api/events/" + event.getId() + "/purchase");

        Map<String, AtomicInteger> outcomes = runConcurrently(buyers, i -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header(HttpHeaders.AUTHORIZATION, tokens.get(i))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            return String.valueOf(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        });

        assertThat(count(outcomes, "201")).isEqualTo(tickets);
        assertThat(count(outcomes, "409")).isEqualTo(buyers - tickets);
        assertThat(availableTickets(event.getId())).isZero();
        assertThat(salesCount(event.getId())).isEqualTo(tickets);
    }

    private List<User> createUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(createUser("buyer" + i + "@bitbox.com"));
        }
        return users;
    }

    private interface IndexedTask {
        String run(int index) throws Exception;
    }

    /** Runs {@code n} tasks that all start at the same moment and tallies their outcomes. */
    private static Map<String, AtomicInteger> runConcurrently(int n, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, AtomicInteger> outcomes = new ConcurrentHashMap<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                Callable<Void> callable = () -> {
                    ready.countDown();
                    start.await();
                    String outcome = task.run(index);
                    outcomes.computeIfAbsent(outcome, key -> new AtomicInteger()).incrementAndGet();
                    return null;
                };
                futures.add(executor.submit(callable));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return outcomes;
    }

    private static int count(Map<String, AtomicInteger> outcomes, String key) {
        AtomicInteger value = outcomes.get(key);
        return value == null ? 0 : value.get();
    }
}
