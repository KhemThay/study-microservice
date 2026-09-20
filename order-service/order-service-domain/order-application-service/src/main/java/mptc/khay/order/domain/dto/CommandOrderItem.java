package mptc.khay.order.domain.dto;


import java.math.BigDecimal;
import java.util.UUID;

public record CommandOrderItem(
        UUID productId,
        Integer quantity,
        BigDecimal subtotal,
        BigDecimal price
) {
}
