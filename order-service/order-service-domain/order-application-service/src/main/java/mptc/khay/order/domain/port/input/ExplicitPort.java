package mptc.khay.order.domain.port.input;

import mptc.khay.order.domain.dto.CreateOrderCommand;

public interface ExplicitPort {
    void execute(CreateOrderCommand createOrderCommand);
}
