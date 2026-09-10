package mptc.khay.persistence.adapter;

import mptc.khay.domain.entity.Order;
import mptc.khay.domain.port.output.OrderRepository;
import mptc.khay.persistence.repository.OrderJpaRepository;

public class OrderRepositoryAdapter implements OrderRepository {


    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order saveOrder(Order order) {
        return null;
    }

    public OrderJpaRepository getOrderJpaRepository() {
        return orderJpaRepository;
    }
}
