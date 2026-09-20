package mptc.khay.order.domain.port.output;

import mptc.khay.domain.entity.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Optional<Customer> findCustomer(UUID customerID);
}
