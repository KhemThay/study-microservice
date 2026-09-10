package mptc.khay.persistence.entity;

import jakarta.persistence.*;
import mptc.khay.domain.valueobject.OrderStatus;
import mptc.khay.domain.valueobject.TrackingId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID orderId;
    private UUID customerId;
    private BigDecimal price;

    @OneToMany(mappedBy = "order")
    private List<OrderItemEntity> items;

    @OneToOne
    private StreetAddressEntity streetAddress;

    private OrderStatus orderStatus;
    private UUID trackingId;

}
