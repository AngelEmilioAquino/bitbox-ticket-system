package com.bitbox.inventory.sale;

import com.bitbox.inventory.config.OpenApiConfig;
import com.bitbox.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Purchases")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/{id}/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buy one ticket for the event (buyer taken from the JWT)")
    @ApiResponse(responseCode = "201", description = "Ticket purchased")
    @ApiResponse(responseCode = "404", description = "Event not found")
    @ApiResponse(responseCode = "409", description = "SOLD_OUT, or PURCHASE_CONFLICT under extreme contention (retryable)")
    public PurchaseResponse purchase(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return purchaseService.purchase(id, AuthenticatedUser.idOf(jwt));
    }
}
