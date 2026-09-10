package mptc.khay.domain.port.dto;

import mptc.khay.domain.valueobject.BusinessId;
import mptc.khay.domain.valueobject.CustomerId;
import mptc.khay.domain.valueobject.Money;
import mptc.khay.domain.valueobject.StreetAddress;

public record CreateOrderRequest(
        CustomerId customerId,
        BusinessId businessId,
        StreetAddress deliveryAddress,
        Money price
) {

}
