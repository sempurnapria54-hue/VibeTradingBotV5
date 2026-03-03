package com.example.tradingbot.util.factory;

import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.rest.model.request.CreateAlgoOrderRequest;
import lombok.experimental.UtilityClass;

import java.util.UUID;

import static com.example.tradingbot.util.Constant.Status.AlgoOrder.ALGO_ORDER_STATUS_CREATED;

@UtilityClass
public class AlgoOrderFactory {

    public static AlgoOrderEntity createAlgoOrderEntity(InstrumentEntity instrument, CreateAlgoOrderRequest request) {

        AlgoOrderEntity algoOrderEntity = new AlgoOrderEntity();

        algoOrderEntity.setInstrumentId(instrument.getId());
        algoOrderEntity.setInternalId(UUID.randomUUID().toString());
        algoOrderEntity.setStatus(ALGO_ORDER_STATUS_CREATED);
        algoOrderEntity.setType(request.getType());
        algoOrderEntity.setSize(request.getSize());
        algoOrderEntity.setTriggerPrice(request.getTriggerPrice());
        algoOrderEntity.setTriggerExecutionPrice(request.getOrderPrice());
        validateParams(algoOrderEntity);
        return algoOrderEntity;
    }

    private static void validateParams(AlgoOrderEntity algoOrderEntity) {

    }
}
