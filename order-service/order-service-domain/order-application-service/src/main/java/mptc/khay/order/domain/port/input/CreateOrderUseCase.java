package mptc.khay.order.domain.port.input;

import mptc.khay.order.domain.dto.CreateOrderRequest;

public interface CreateOrderUseCase {

    void execute(CreateOrderRequest createOrderRequest);

}
