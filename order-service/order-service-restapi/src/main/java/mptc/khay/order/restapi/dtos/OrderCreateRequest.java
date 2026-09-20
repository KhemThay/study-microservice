package mptc.khay.order.restapi.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderCreateRequest(
        @NotNull
        UUID customerId,
        @NotNull
        UUID businessId,
        @NotNull
        @Valid
        OrderAddressRequest orderAddress,
        @NotNull
        @Valid
        List<OrderItemRequest> items,
        @NotNull
        BigDecimal price
) {

}
