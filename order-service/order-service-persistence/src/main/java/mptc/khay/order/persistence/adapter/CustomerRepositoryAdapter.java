package mptc.khay.order.persistence.adapter;

import lombok.RequiredArgsConstructor;
import mptc.khay.domain.entity.Customer;
import mptc.khay.order.domain.port.output.CustomerRepository;
import mptc.khay.order.persistence.mapper.OrderPersistenceMapper;
import mptc.khay.order.persistence.repository.CustomerJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerJpaRepository customerJpaRepository;
    private final OrderPersistenceMapper orderPersistenceMapper;

    @Override
    public Optional<Customer> findCustomer(UUID customerID) {
        return customerJpaRepository.findById(customerID)
                .map(orderPersistenceMapper::customerEntityToCustomer);
    }
}
