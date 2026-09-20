package mptc.khay.order.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        UUID customerId,
        UUID businessId,
        CommandOrderAddress deliveryAddress,
        BigDecimal price,
        List<CommandOrderItem> items

) {

}
