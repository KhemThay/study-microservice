package mptc.khay.order.restapi.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record OrderItemRequest(
        @NotNull
        UUID productId,
        @NotNull
        Integer quantity,
        @NotNull
        BigDecimal price,
        @NotNull
        BigDecimal subTotal
) {
}
