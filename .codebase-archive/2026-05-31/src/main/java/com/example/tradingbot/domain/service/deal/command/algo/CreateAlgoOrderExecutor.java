package com.example.tradingbot.domain.service.deal.command.algo;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreateAlgoOrderExecutor {

    private static final BigDecimal DEFAULT_SIZE = BigDecimal.ONE;

    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public AlgoOrder execute(ServiceCommand command) {
        CreateAlgoOrderCommandPayload payload = requirePayload(command);
        Long dealId = requireDealId(command);
        Long strategyActionId = requireStrategyActionId(payload);

        AlgoOrder existing = algoOrderDataService.findByDealIdAndStrategyActionId(dealId, strategyActionId)
                                                 .orElse(null);
        if (Objects.nonNull(existing)) {
            return existing;
        }

        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setDealId(dealId);
        algoOrder.setStrategyActionId(strategyActionId);
        algoOrder.setInternalId(UUID.randomUUID().toString());
        algoOrder.setStatus(AlgoOrder.Status.CREATED);
        algoOrder.setConditionType(payload.getConditionType());
        algoOrder.setSize(resolveSize(payload.getSize()));
        algoOrder.setDirection(payload.getDirection());
        algoOrder.setExternalType(payload.getExternalType());
        algoOrder.setExternalDirection(payload.getExternalDirection());
        algoOrder.setCondition(payload.getCondition());

        return algoOrderDataService.save(algoOrder);
    }

    private CreateAlgoOrderCommandPayload requirePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            throw new IllegalArgumentException("CREATE_ALGO_ORDER payload is required");
        }
        if (command.getPayload() instanceof CreateAlgoOrderCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("CREATE_ALGO_ORDER payload has unsupported type");
    }

    private Long requireDealId(ServiceCommand command) {
        if (Objects.isNull(command.getDealId())) {
            throw new IllegalArgumentException("CREATE_ALGO_ORDER dealId is required");
        }
        return command.getDealId();
    }

    private Long requireStrategyActionId(CreateAlgoOrderCommandPayload payload) {
        if (Objects.isNull(payload.getStrategyActionId())) {
            throw new IllegalArgumentException("CREATE_ALGO_ORDER strategyActionId is required");
        }
        return payload.getStrategyActionId();
    }

    private BigDecimal resolveSize(BigDecimal source) {
        if (Objects.nonNull(source)) {
            return source;
        }
        return DEFAULT_SIZE;
    }
}
