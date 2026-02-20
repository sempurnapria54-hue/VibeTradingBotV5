package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.okx.dto.AlgoOrderResponse;
import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.trading.CreateAlgoOrderRequest;
import com.example.tradingbot.domain.service.ExchangeService;
import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.tradingbot.util.Constant.Status.AlgoOrder.ALGO_ORDER_STATUS_IN_PROGRESS;

@Service
@RequiredArgsConstructor
public class AlgoOrderService {

    private final TradingGuardService tradingGuardService;
    private final AlgoOrderDataService algoOrderDataService;
    private final OkxTradeProxyService okxTradeProxyService;
    private final ExchangeService exchangeService;
    private final InstrumentService instrumentService;

    @Transactional
    public AlgoOrderEntity createAlgoOrder(String exchangeName, String instrumentName, CreateAlgoOrderRequest request) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndName(exchange.getId(), instrumentName);
        tradingGuardService.assertTradingAllowed(exchange, instrument);

        AlgoOrderEntity algoOrderEntity = new AlgoOrderEntity();
        algoOrderEntity.initOnCreate(instrument, request);
        algoOrderDataService.save(algoOrderEntity);

        AlgoOrderResponse responseAlgoOrder = extractFirstAlgoOrder(okxTradeProxyService.createAlgoOrder(algoOrderEntity));
        applyResponse(algoOrderEntity, responseAlgoOrder);
        return algoOrderDataService.save(algoOrderEntity);
    }

    public AlgoOrderEntity cancelAlgoOrder(String exchangeName, String instrumentName, String orderId) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndName(exchange.getId(), instrumentName);
        tradingGuardService.assertTradingAllowed(exchange, instrument);

        AlgoOrderEntity algoOrderEntity =
                algoOrderDataService.findRequiredByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchange.getId(), instrument.getId(), orderId);

        AlgoOrderResponse responseAlgoOrder = extractFirstAlgoOrder(okxTradeProxyService.cancelAlgoOrder(algoOrderEntity));
        applyResponse(algoOrderEntity, responseAlgoOrder);
        return algoOrderDataService.save(algoOrderEntity);
    }

    private AlgoOrderResponse extractFirstAlgoOrder(List<AlgoOrderResponse> orders) {
        if (orders.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE", "OKX returned empty algo order response");
        }
        return orders.getFirst();
    }

    private void applyResponse(AlgoOrderEntity entity, AlgoOrderResponse response) {
        entity.setExchangeAlgoOrderId(response.getAlgoId());
        if (response.getClOrdId() != null) {
            entity.setClientAlgoOrderId(response.getClOrdId());
        }
        entity.setState(response.getState());
        entity.setStatus(ALGO_ORDER_STATUS_IN_PROGRESS);
        entity.setAlgoType(response.getOrdType());
        entity.setSz(response.getSz());
        entity.setTriggerPx(response.getTriggerPx());
        entity.setOrdPx(response.getOrdPx());
        entity.setTpTriggerPx(response.getTpTriggerPx());
        entity.setTpOrdPx(response.getTpOrdPx());
        entity.setSlTriggerPx(response.getSlTriggerPx());
        entity.setSlOrdPx(response.getSlOrdPx());
        entity.setCallbackRatio(response.getCallbackRatio());
        entity.setCallbackSpread(response.getCallbackSpread());
        entity.setCTime(parseLongSafe(response.getCTime()));
        entity.setUTime(parseLongSafe(response.getUTime()));
    }

    private Long parseLongSafe(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return Long.parseLong(source);
    }
}
