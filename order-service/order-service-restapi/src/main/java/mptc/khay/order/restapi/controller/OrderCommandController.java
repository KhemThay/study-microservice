package mptc.khay.order.restapi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mptc.khay.order.domain.dto.CreateOrderCommand;
import mptc.khay.order.domain.dto.CreateOrderResult;
import mptc.khay.order.domain.usecase.CreateOrderUseCase;
import mptc.khay.order.restapi.dto.OrderCreateRequest;
import mptc.khay.order.restapi.dto.OrderCreateResponse;
import mptc.khay.order.restapi.mapper.OrderWebMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderCommandController {

    //Declare required dependency
    private final CreateOrderUseCase createOrderUseCase;
    private final OrderWebMapper orderWebMapper;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public OrderCreateResponse createOrder(
            @Valid @RequestBody OrderCreateRequest orderCreateRequest
    ){

        CreateOrderCommand createOrderCommand = orderWebMapper
                .orderCreateRequestToCreateOrderCommand(orderCreateRequest);

        CreateOrderResult createOrderResult = createOrderUseCase.execute(createOrderCommand);

        return orderWebMapper.createOrderResultToOrderCreateResponse(createOrderResult);
    }

}
