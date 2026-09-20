package mptc.khay.order.restapi.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderCreateResponse(
        UUID orderId
) {

}
