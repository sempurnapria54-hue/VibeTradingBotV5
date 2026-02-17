package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.domain.model.okxproxy.AlgoOrder;
import com.example.tradingbot.domain.model.okxproxy.CancelAlgoOrderRequest;
import com.example.tradingbot.domain.model.okxproxy.CreateAlgoOrderRequest;
import com.example.tradingbot.domain.model.trading.AlgoOrderCommandResult;
import com.example.tradingbot.domain.model.trading.CancelAlgoOrdersCommand;
import com.example.tradingbot.domain.model.trading.CreateAlgoOrderCommand;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlgoOrderCommandService {

    private static final String ALGO_ORDER_STATUS_CREATED = "CREATED";
    private static final String ALGO_ORDER_STATUS_UPDATED = "UPDATED";
    private static final String DEFAULT_TRADE_MODE = "cross";

    private final TradingGuardService tradingGuardService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final OkxTradeProxyService okxTradeProxyService;

    @Transactional
    public AlgoOrderCommandResult createAlgoOrder(CreateAlgoOrderCommand command) {
        tradingGuardService.assertTradingAllowed(command.getExchangeId(), command.getInstrumentId());

        ExchangeEntity exchange = exchangeDataService.findById(command.getExchangeId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));
        InstrumentEntity instrument = instrumentDataService.findById(command.getInstrumentId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", "Instrument not found"));

        String internalId = UUID.randomUUID().toString();
        AlgoOrderEntity algoOrderEntity = new AlgoOrderEntity();
        algoOrderEntity.setExchange(exchange);
        algoOrderEntity.setInstrument(instrument);
        algoOrderEntity.setClientAlgoOrderId(internalId);
        algoOrderEntity.setStatus(ALGO_ORDER_STATUS_CREATED);
        algoOrderEntity.setAlgoType(command.getOrdType());
        algoOrderEntity.setSz(command.getSz());
        algoOrderEntity.setTriggerPx(command.getTriggerPx());
        algoOrderEntity.setOrdPx(command.getOrdPx());
        algoOrderDataService.save(algoOrderEntity);

        CreateAlgoOrderRequest request = new CreateAlgoOrderRequest();
        request.setInstrumentId(instrument.getInstId());
        request.setTradeMode(DEFAULT_TRADE_MODE);
        request.setSide(command.getSide());
        request.setOrderType(command.getOrdType());
        request.setSize(command.getSz());
        request.setTriggerPrice(command.getTriggerPx());
        request.setOrderPrice(command.getOrdPx());
        request.setClientOrderId(internalId);

        AlgoOrder responseAlgoOrder = extractFirstAlgoOrder(okxTradeProxyService.createAlgoOrder(request));
        applyAlgoOrderResponse(algoOrderEntity, responseAlgoOrder);
        algoOrderDataService.save(algoOrderEntity);

        return new AlgoOrderCommandResult(
            algoOrderEntity.getClientAlgoOrderId(),
            algoOrderEntity.getExchangeAlgoOrderId(),
            algoOrderEntity.getState()
        );
    }

    @Transactional
    public List<AlgoOrderCommandResult> cancelAlgoOrders(CancelAlgoOrdersCommand command) {
        tradingGuardService.assertTradingAllowed(command.getExchangeId(), command.getInstrumentId());

        InstrumentEntity instrument = instrumentDataService.findById(command.getInstrumentId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", "Instrument not found"));

        List<AlgoOrderCommandResult> results = new ArrayList<>();
        for (String internalId : command.getInternalIds()) {
            AlgoOrderEntity algoOrderEntity = algoOrderDataService.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(
                    command.getExchangeId(),
                    command.getInstrumentId(),
                    internalId
                )
                .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "ALGO_ORDER_NOT_FOUND", "Algo order not found"));

            CancelAlgoOrderRequest request = new CancelAlgoOrderRequest();
            request.setInstrumentId(instrument.getInstId());
            request.setAlgoOrderId(algoOrderEntity.getExchangeAlgoOrderId());
            request.setClientOrderId(algoOrderEntity.getClientAlgoOrderId());

            AlgoOrder responseAlgoOrder = extractFirstAlgoOrder(okxTradeProxyService.cancelAlgoOrder(request));
            applyAlgoOrderResponse(algoOrderEntity, responseAlgoOrder);
            algoOrderDataService.save(algoOrderEntity);

            results.add(new AlgoOrderCommandResult(
                algoOrderEntity.getClientAlgoOrderId(),
                algoOrderEntity.getExchangeAlgoOrderId(),
                algoOrderEntity.getState()
            ));
        }

        return results;
    }

    private AlgoOrder extractFirstAlgoOrder(List<AlgoOrder> orders) {
        if (orders.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE", "OKX returned empty algo order response");
        }
        return orders.getFirst();
    }

    private void applyAlgoOrderResponse(AlgoOrderEntity entity, AlgoOrder responseOrder) {
        entity.setExchangeAlgoOrderId(responseOrder.getAlgoOrderId());
        if (Objects.nonNull(responseOrder.getClientOrderId())) {
            entity.setClientAlgoOrderId(responseOrder.getClientOrderId());
        }
        entity.setState(responseOrder.getState());
        entity.setStatus(ALGO_ORDER_STATUS_UPDATED);
        entity.setAlgoType(responseOrder.getOrderType());
        entity.setSz(responseOrder.getSize());
        entity.setTriggerPx(responseOrder.getTriggerPrice());
        entity.setOrdPx(responseOrder.getOrderPrice());
        entity.setTpTriggerPx(responseOrder.getTakeProfitTriggerPrice());
        entity.setTpOrdPx(responseOrder.getTakeProfitOrderPrice());
        entity.setSlTriggerPx(responseOrder.getStopLossTriggerPrice());
        entity.setSlOrdPx(responseOrder.getStopLossOrderPrice());
        entity.setCallbackRatio(responseOrder.getCallbackRatio());
        entity.setCallbackSpread(responseOrder.getCallbackSpread());
        entity.setCTime(parseLongSafe(responseOrder.getCreateTime()));
        entity.setUTime(parseLongSafe(responseOrder.getUpdateTime()));
    }

    private Long parseLongSafe(String source) {
        if (Objects.isNull(source) || source.isBlank()) {
            return null;
        }
        return Long.parseLong(source);
    }
}
