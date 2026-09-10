package mptc.khay.domain.port.output;

import mptc.khay.domain.entity.Order;

public interface OrderRepository {

    Order saveOrder(Order order);

}
