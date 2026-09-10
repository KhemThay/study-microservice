package mptc.khay.domain.port.input;

import mptc.khay.domain.port.dto.CreateOrderRequest;

public interface CreateOrderUseCase {

    void execute(CreateOrderRequest createOrderRequest);

}
