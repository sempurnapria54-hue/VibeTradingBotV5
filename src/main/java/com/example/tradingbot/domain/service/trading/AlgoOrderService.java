package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.model.okx.AlgoOrderResponse;
import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.service.ExchangeService;
import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.domain.service.OkxProxyService;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
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
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;

    @Transactional
    public AlgoOrderEntity createAlgoOrder(String exchangeInternalId, String instrumentInternalId, CreateAlgoOrderRequest request) {
        ExchangeEntity exchangeEntity = exchangeDataService.findRequiredByInternalId(exchangeInternalId);
        InstrumentEntity instrumentEntity = instrumentDataService.findRequiredByExchangeIdAndInternalId(exchangeEntity.getId(), instrumentInternalId);
        tradingGuardService.assertTradingAllowed(exchangeEntity, instrumentEntity);

        AlgoOrderEntity algoOrderEntity = new AlgoOrderEntity();
        algoOrderEntity.initOnCreate(instrumentEntity, request);
        algoOrderDataService.save(algoOrderEntity);

        AlgoOrderResponse responseAlgoOrder = extractFirstAlgoOrder(okxProxyService.createAlgoOrder(algoOrderEntity, instrumentEntity));
        algoOrderEntity.applyAlgoOrderResponse(responseAlgoOrder);
        return algoOrderDataService.save(algoOrderEntity);
    }

    public AlgoOrderEntity cancelAlgoOrder(String exchangeInternalId, String instrumentInternalId, String orderId) {
        ExchangeEntity exchangeEntity = exchangeDataService.findRequiredByInternalId(exchangeInternalId);
        InstrumentEntity instrumentEntity = instrumentDataService.findRequiredByExchangeIdAndInternalId(exchangeEntity.getId(), instrumentInternalId);
        tradingGuardService.assertTradingAllowed(exchangeEntity, instrumentEntity);

        AlgoOrderEntity algoOrderEntity =
                algoOrderDataService.findRequiredByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchangeEntity.getId(), instrumentEntity.getId(), orderId);

        AlgoOrderResponse responseAlgoOrder = extractFirstAlgoOrder(okxProxyService.cancelAlgoOrder(algoOrderEntity, instrumentEntity));
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
