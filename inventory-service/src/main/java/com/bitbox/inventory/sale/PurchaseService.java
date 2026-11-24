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

/** Concurrency-safe ticket purchase.
 * decrements the number of available tickets and creates a purchase record.
 * If the number of available tickets is 0, throws SoldOutException.
 * If the event is not found, throws EventNotFoundException.
 * If the user is not found, throws BuyerNotFoundException.
 * If the purchase cannot be completed due to concurrency issues, throws PurchaseTemporarilyUnavailableException
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

            // Reads our own update; the row stays locked by this transaction
            Integer availableTickets = eventRepository.findAvailableTicketsById(eventId);
            return new PurchaseResponse(sale.getId(), eventId, userId, sale.getPurchaseDate(), availableTickets);
        } catch (ConcurrencyFailureException transientFailure) {
            log.warn("Transient concurrency failure buying event {}: {}", eventId, transientFailure.getMessage());
            throw new PurchaseTemporarilyUnavailableException(eventId, transientFailure);
        }
    }
}
