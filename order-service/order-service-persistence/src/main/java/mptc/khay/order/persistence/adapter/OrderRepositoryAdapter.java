package mptc.khay.order.persistence.adapter;

import mptc.khay.domain.entity.Order;
import mptc.khay.order.domain.port.output.OrderRepository;
import mptc.khay.order.persistence.repository.OrderJpaRepository;

public class OrderRepositoryAdapter implements OrderRepository {


    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order saveOrder(Order order) {
        return null;
    }

}
