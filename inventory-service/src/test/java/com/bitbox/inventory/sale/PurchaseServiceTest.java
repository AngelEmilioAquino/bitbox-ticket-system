package com.bitbox.inventory.sale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import com.bitbox.inventory.event.EventNotFoundException;
import com.bitbox.inventory.event.EventRepository;
import com.bitbox.inventory.sale.PurchaseExceptions.BuyerNotFoundException;
import com.bitbox.inventory.sale.PurchaseExceptions.PurchaseTemporarilyUnavailableException;
import com.bitbox.inventory.sale.PurchaseExceptions.SoldOutException;
import com.bitbox.inventory.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;

/** Unit test: how each database outcome is classified (no database involved). */
class PurchaseServiceTest {

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TicketSaleRepository ticketSaleRepository = mock(TicketSaleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PurchaseService service =
            new PurchaseService(eventRepository, ticketSaleRepository, userRepository,
                    mock(ApplicationEventPublisher.class), Clock.systemUTC());

    @BeforeEach
    void buyerExists() {
        when(userRepository.existsById(7L)).thenReturn(true);
    }

    @Test
    void noRowUpdatedOnExistingEventMeansSoldOut() {
        when(eventRepository.decrementAvailableTickets(5L)).thenReturn(0);
        when(eventRepository.existsById(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.purchase(5L, 7L)).isInstanceOf(SoldOutException.class);
        verify(ticketSaleRepository, never()).save(any());
    }

    @Test
    void noRowUpdatedOnMissingEventMeansNotFound() {
        when(eventRepository.decrementAvailableTickets(5L)).thenReturn(0);
        when(eventRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.purchase(5L, 7L)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void lockTimeoutIsTransientNotSoldOut() {
        when(eventRepository.decrementAvailableTickets(5L))
                .thenThrow(new CannotAcquireLockException("lock timeout"));

        assertThatThrownBy(() -> service.purchase(5L, 7L))
                .isInstanceOf(PurchaseTemporarilyUnavailableException.class)
                .hasCauseInstanceOf(CannotAcquireLockException.class);
        verify(ticketSaleRepository, never()).save(any());
    }

    @Test
    void unknownBuyerIsRejectedBeforeTouchingStock() {
        when(userRepository.existsById(8L)).thenReturn(false);

        assertThatThrownBy(() -> service.purchase(5L, 8L)).isInstanceOf(BuyerNotFoundException.class);
        verify(eventRepository, never()).decrementAvailableTickets(any());
    }
}