package com.bitbox.inventory.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import com.bitbox.inventory.event.EventRepository;
import com.bitbox.inventory.sale.PurchaseExceptions.PurchaseConflictException;
import com.bitbox.inventory.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/** Unit test of the retry policy, independent of the database. */
class PurchaseServiceRetryTest {

    private final TransactionOperations tx = mock(TransactionOperations.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private PurchaseService service;

    @BeforeEach
    void setUp() {
        when(userRepository.existsById(7L)).thenReturn(true);
        service = new PurchaseService(mock(EventRepository.class), mock(TicketSaleRepository.class), userRepository,
                tx, new PurchaseRetryProperties(3, Duration.ZERO, Duration.ZERO), Clock.systemUTC());
    }

    @Test
    void retriesOptimisticConflictsAndSucceeds() {
        PurchaseResponse ok = new PurchaseResponse(1L, 5L, 7L, LocalDateTime.now(), 0);
        when(tx.execute(any(TransactionCallback.class)))
                .thenThrow(conflict())
                .thenThrow(conflict())
                .thenReturn(ok);

        assertThat(service.purchase(5L, 7L)).isEqualTo(ok);
        verify(tx, times(3)).execute(any(TransactionCallback.class));
    }

    @Test
    void exhaustedRetriesReportRetryableConflictNotSoldOut() {
        when(tx.execute(any(TransactionCallback.class))).thenThrow(conflict());

        assertThatThrownBy(() -> service.purchase(5L, 7L))
                .isInstanceOf(PurchaseConflictException.class)
                .extracting("code").isEqualTo("PURCHASE_CONFLICT");
        verify(tx, times(3)).execute(any(TransactionCallback.class));
    }

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException("Event", 5L);
    }
}
