package mptc.khay.order.domain.dto;

import mptc.khay.domain.valueobject.OrderId;

import java.util.UUID;

public record CreateOrderResult(
        UUID orderId
) {


}
