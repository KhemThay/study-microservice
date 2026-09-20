package mptc.khay.order.domain.usecase;

import lombok.extern.slf4j.Slf4j;
import mptc.khay.order.domain.dto.CreateOrderCommand;
import mptc.khay.order.domain.dto.CreateOrderResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class CreateOrderUseCase {

    public CreateOrderResult execute(CreateOrderCommand createOrderCommand){
        log.info("executing CreateOrderUseCase: {}", createOrderCommand);

        return new CreateOrderResult(UUID.randomUUID());
    }

}
