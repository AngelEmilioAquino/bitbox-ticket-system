package com.bitbox.inventory.sale;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import com.bitbox.inventory.event.Event;
import com.bitbox.inventory.event.EventNotFoundException;
import com.bitbox.inventory.event.EventRepository;
import com.bitbox.inventory.sale.PurchaseExceptions.BuyerNotFoundException;
import com.bitbox.inventory.sale.PurchaseExceptions.PurchaseConflictException;
import com.bitbox.inventory.sale.PurchaseExceptions.SoldOutException;
import com.bitbox.inventory.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Concurrency-safe ticket purchase based on optimistic locking ({@code @Version} on {@link Event}).
 *
 * <p>Each attempt runs in its own short transaction: read event, decrement stock, flush (the version check
 * happens here), insert the sale. Stock decrement and sale insert commit or roll back together.
 * If another buyer committed first, the version check fails, the whole attempt is rolled back and
 * retried with fresh data. The retry loop lives <strong>outside</strong> the transaction on purpose:
 * retrying inside it would reuse a stale persistence context and could never succeed.</p>
 *
 * <p>{@link TransactionOperations} is used instead of {@code @Transactional} so each attempt gets a
 * new transaction without relying on proxy self-invocation.</p>
 */
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final EventRepository eventRepository;
    private final TicketSaleRepository ticketSaleRepository;
    private final UserRepository userRepository;
    private final TransactionOperations transactionOperations;
    private final PurchaseRetryProperties retryProperties;
    private final Clock clock;

    public PurchaseService(EventRepository eventRepository,
                           TicketSaleRepository ticketSaleRepository,
                           UserRepository userRepository,
                           TransactionOperations transactionOperations,
                           PurchaseRetryProperties retryProperties,
                           Clock clock) {
        this.eventRepository = eventRepository;
        this.ticketSaleRepository = ticketSaleRepository;
        this.userRepository = userRepository;
        this.transactionOperations = transactionOperations;
        this.retryProperties = retryProperties;
        this.clock = clock;
    }

    public PurchaseResponse purchase(Long eventId, Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BuyerNotFoundException();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionOperations.execute(status -> purchaseOnce(eventId, userId));
            } catch (OptimisticLockingFailureException conflict) {
                if (attempt >= retryProperties.maxAttempts()) {
                    log.warn("Purchase for event {} gave up after {} optimistic-locking conflicts", eventId, attempt);
                    throw new PurchaseConflictException(eventId);
                }
                log.debug("Optimistic-locking conflict on event {} (attempt {}), retrying", eventId, attempt);
                backoff(attempt, eventId);
            }
        }
    }

    private PurchaseResponse purchaseOnce(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (!event.tryReserveTicket()) {
            throw new SoldOutException(eventId);
        }
        // Flush now: UPDATE ... WHERE version = ? fails fast here if someone else bought first,
        // before we insert the sale.
        eventRepository.saveAndFlush(event);

        TicketSale sale = ticketSaleRepository.save(
                new TicketSale(event, userRepository.getReferenceById(userId), LocalDateTime.now(clock)));

        return new PurchaseResponse(sale.getId(), eventId, userId, sale.getPurchaseDate(),
                event.getAvailableTickets());
    }

    private void backoff(int attempt, Long eventId) {
        long cap = Math.min(retryProperties.maxBackoff().toMillis(),
                retryProperties.initialBackoff().toMillis() * (1L << Math.min(attempt - 1, 20)));
        long sleepMillis = ThreadLocalRandom.current().nextLong(cap + 1);
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PurchaseConflictException(eventId);
        }
    }
}
