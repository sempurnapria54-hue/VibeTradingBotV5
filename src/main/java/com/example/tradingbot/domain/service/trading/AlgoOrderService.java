package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.model.okx.AlgoOrderResponse;
import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.service.ExchangeService;
import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.domain.service.OkxProxyService;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.rest.model.request.CreateAlgoOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlgoOrderService {

    private final TradingGuardService tradingGuardService;
    private final AlgoOrderDataService algoOrderDataService;
    private final OkxProxyService okxProxyService;
    private final ExchangeService exchangeService;
    private final InstrumentService instrumentService;

    @Transactional
    public AlgoOrderEntity createAlgoOrder(String exchangeInternalId, String instrumentInternalId, CreateAlgoOrderRequest request) {
        ExchangeEntity exchange = exchangeService.getRequiredByInternalId(exchangeInternalId);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndInternalId(exchange.getId(), instrumentInternalId);
        tradingGuardService.assertTradingAllowed(exchange, instrument);

        AlgoOrderEntity algoOrderEntity = new AlgoOrderEntity();
        algoOrderEntity.initOnCreate(instrument, request);
        algoOrderDataService.save(algoOrderEntity);

        AlgoOrderResponse responseAlgoOrder = extractFirstAlgoOrder(okxProxyService.createAlgoOrder(algoOrderEntity));
        algoOrderEntity.applyAlgoOrderResponse(responseAlgoOrder);
        return algoOrderDataService.save(algoOrderEntity);
    }

    public AlgoOrderEntity cancelAlgoOrder(String exchangeInternalId, String instrumentInternalId, String orderId) {
        ExchangeEntity exchange = exchangeService.getRequiredByInternalId(exchangeInternalId);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndInternalId(exchange.getId(), instrumentInternalId);
        tradingGuardService.assertTradingAllowed(exchange, instrument);

        AlgoOrderEntity algoOrderEntity =
                algoOrderDataService.findRequiredByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchange.getId(), instrument.getId(), orderId);

        AlgoOrderResponse responseAlgoOrder = extractFirstAlgoOrder(okxProxyService.cancelAlgoOrder(algoOrderEntity));
        algoOrderEntity.applyAlgoOrderResponse(responseAlgoOrder);
        return algoOrderDataService.save(algoOrderEntity);
    }

    private AlgoOrderResponse extractFirstAlgoOrder(List<AlgoOrderResponse> orders) {
        if (orders.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE", "OKX returned empty algo order response");
        }
        return orders.getFirst();
    }
}
