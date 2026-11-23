package com.bitbox.inventory.sale;

import java.time.Clock;
import java.time.LocalDateTime;

import com.bitbox.inventory.event.EventNotFoundException;
import com.bitbox.inventory.event.EventRepository;
import com.bitbox.inventory.sale.PurchaseExceptions.BuyerNotFoundException;
import com.bitbox.inventory.sale.PurchaseExceptions.PurchaseTemporarilyUnavailableException;
import com.bitbox.inventory.sale.PurchaseExceptions.SoldOutException;
import com.bitbox.inventory.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concurrency-safe ticket purchase.
 *
 * <p>Stock is taken with an atomic conditional {@code UPDATE} (see
 * {@link EventRepository#decrementAvailableTickets(Long)}) and the {@link TicketSale} is inserted in the
 * <strong>same transaction</strong>: both commit together or neither does.</p>
 *
 * <p>Two different outcomes are kept apart on purpose:</p>
 * <ul>
 *   <li><b>Real lack of stock</b> → {@link SoldOutException} (definitive, HTTP 409).</li>
 *   <li><b>Transient concurrency failure</b> (lock timeout, deadlock, serialization failure) →
 *       {@link PurchaseTemporarilyUnavailableException} (retryable, HTTP 503). Nothing was sold and the
 *       stock is untouched because the transaction rolled back.</li>
 * </ul>
 */
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final EventRepository eventRepository;
    private final TicketSaleRepository ticketSaleRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public PurchaseService(EventRepository eventRepository,
                           TicketSaleRepository ticketSaleRepository,
                           UserRepository userRepository,
                           Clock clock) {
        this.eventRepository = eventRepository;
        this.ticketSaleRepository = ticketSaleRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public PurchaseResponse purchase(Long eventId, Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BuyerNotFoundException();
        }
        try {
            if (eventRepository.decrementAvailableTickets(eventId) == 0) {
                // 0 rows: either the event does not exist or its stock is exhausted
                if (!eventRepository.existsById(eventId)) {
                    throw new EventNotFoundException(eventId);
                }
                throw new SoldOutException(eventId);
            }

            TicketSale sale = ticketSaleRepository.save(new TicketSale(
                    eventRepository.getReferenceById(eventId),
                    userRepository.getReferenceById(userId),
                    LocalDateTime.now(clock)));

            // Reads our own (still uncommitted) update; the row stays locked by this transaction
            Integer availableTickets = eventRepository.findAvailableTicketsById(eventId);
            return new PurchaseResponse(sale.getId(), eventId, userId, sale.getPurchaseDate(), availableTickets);
        } catch (ConcurrencyFailureException transientFailure) {
            log.warn("Transient concurrency failure buying event {}: {}", eventId, transientFailure.getMessage());
            throw new PurchaseTemporarilyUnavailableException(eventId, transientFailure);
        }
    }
}
