package mptc.khay.domain.event;

import mptc.khay.domain.entity.Order;

import java.time.ZonedDateTime;

public class OrderCreatedEvent extends OrderEvent{
    public OrderCreatedEvent(Order order, ZonedDateTime createdAt){
        super(order, createdAt);
    }
}
