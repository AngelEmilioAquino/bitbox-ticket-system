package com.bitbox.inventory.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.bitbox.inventory.event.Event;
import com.bitbox.inventory.support.IntegrationTest;
import com.bitbox.inventory.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The stock decrement and the TicketSale insert are one transaction: if registering the sale fails,
 * the decrement must be rolled back (no ticket "lost" without a sale).
 */
class PurchaseAtomicityTest extends IntegrationTest {

    @Autowired
    private PurchaseService purchaseService;

    @MockitoSpyBean
    private TicketSaleRepository ticketSaleRepository;

    @Test
    void failingToRegisterTheSaleRollsBackTheStockDecrement() {
        User user = createUser("ana@bitbox.com");
        Event event = createEvent("Concierto", 3);
        doThrow(new DataIntegrityViolationException("simulated insert failure"))
                .when(ticketSaleRepository).save(any(TicketSale.class));

        assertThatThrownBy(() -> purchaseService.purchase(event.getId(), user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(availableTickets(event.getId())).isEqualTo(3);
        assertThat(salesCount(event.getId())).isZero();
    }
}
