package mptc.khay.order.restapi.mapper;

import mptc.khay.order.domain.dto.CreateOrderCommand;
import mptc.khay.order.domain.dto.CreateOrderResult;
import mptc.khay.order.restapi.dtos.OrderCreateRequest;
import mptc.khay.order.restapi.dtos.OrderCreateResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderWebMapper {

    // source = OrderCreateRequest
    // Target = CreateOrderCommand

    CreateOrderCommand orderCreateRequestToCreateOrderCommand(
            OrderCreateRequest orderCreateRequest
    );

    OrderCreateResponse createOrderResultToOrderCreateResponse(
            CreateOrderResult createOrderResult
    );
}
