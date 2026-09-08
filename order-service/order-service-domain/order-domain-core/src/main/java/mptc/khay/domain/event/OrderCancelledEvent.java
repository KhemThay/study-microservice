package mptc.khay.domain.event;

import mptc.khay.domain.entity.Order;

import java.time.ZonedDateTime;

public class OrderCancelledEvent extends OrderEvent{
    public OrderCancelledEvent(Order order, ZonedDateTime createdAt){
        super (order, createdAt);
    }
}
