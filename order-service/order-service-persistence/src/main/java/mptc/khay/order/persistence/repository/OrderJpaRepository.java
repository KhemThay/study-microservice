package mptc.khay.order.persistence.repository;

import mptc.khay.order.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Benefit of using Spring Data JPA
// 1. Boilerplate code
// 2. Abstraction Repository
// 3. Derived Query Method (Auto generate SQL)
// 4. Object Relational Mapping (ORM)
// 5. Specification (dynamic query)

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {
}
