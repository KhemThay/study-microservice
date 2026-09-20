package mptc.khay.order.persistence.adapter;

import lombok.RequiredArgsConstructor;
import mptc.khay.domain.entity.Order;
import mptc.khay.order.domain.port.output.OrderRepository;
import mptc.khay.order.persistence.repository.OrderJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order saveOrder(Order order) {
        return null;
    }

}
