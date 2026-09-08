package mptc.khay.domain.event;

import mptc.khay.domain.entity.Order;

import java.time.ZonedDateTime;

public class OrderPaidEvent extends OrderEvent{
    public OrderPaidEvent(Order order, ZonedDateTime createdAt){
        super (order, createdAt);
    }
}
