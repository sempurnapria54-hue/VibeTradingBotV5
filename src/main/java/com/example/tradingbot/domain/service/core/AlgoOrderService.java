package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.search_params.AlgoOrderSearchParams;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshAlgoOrderExecutor;
import com.example.tradingbot.exception.TradingCommandException;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.util.Constant.ErrorCode.ALGO_ORDER_NOT_FOUND_ON_EXCHANGE;

@Service
@RequiredArgsConstructor
public class AlgoOrderService {

    private final AlgoOrderDataService algoOrderDataService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final DealDataService dealDataService;
    private final AlgoOrderMapper algoOrderMapper;
    private final ClientManager clientManager;
    private final RefreshAlgoOrderExecutor refreshAlgoOrderExecutor;

    @Transactional
    public AlgoOrder createAlgoOrder(String dealInternalId, AlgoOrder request) {
        Deal deal = dealDataService.findRequiredByInternalId(dealInternalId);

        AlgoOrder algoOrder = new AlgoOrder();
        algoOrderMapper.domainToDomainOnCreate(request, algoOrder);
        algoOrder.setStatus(AlgoOrder.Status.CREATED);
        return algoOrderDataService.save(algoOrder);
    }

    @Transactional
    public AlgoOrder createOnExchange(String algoOrderInternalId) {
        AlgoOrder algoOrder = algoOrderDataService.findRequiredByInternalId(algoOrderInternalId);
        Deal deal = dealDataService.findRequiredById(algoOrder.getDealId());
        Instrument instrument = instrumentDataService.findRequiredByDealId(algoOrder.getDealId());
        Exchange exchange = exchangeDataService.findRequiredById(instrument.getExchangeId());
        Position position = deal.getPositions()
                                .getFirst();

        List<AlgoOrder> externalAlgoOrders = clientManager.getClientService(exchange.getName())
                                                          .createAlgoOrder(algoOrder, instrument, position);

        AlgoOrder externalAlgoOrder = getRequiredByExternalId(externalAlgoOrders, algoOrder.getExternalId());
        algoOrderMapper.domainToDomainOnUpdate(externalAlgoOrder, algoOrder);
        algoOrder.setStatus(AlgoOrder.Status.PENDING);
        return algoOrderDataService.save(algoOrder);
    }

    public AlgoOrder syncAlgoOrder(String exchangeInternalId, String internalId) {
        Exchange exchange = exchangeDataService.findRequiredByInternalId(exchangeInternalId);
        AlgoOrder algoOrder = algoOrderDataService.findRequiredByInternalId(internalId);

        List<AlgoOrder> externalAlgoOrders = clientManager.getClientService(exchange.getName())
                                                          .createAlgoOrder(algoOrder);
        AlgoOrder externalAlgoOrder = getRequiredByExternalId(externalAlgoOrders, algoOrder.getExternalId());

        algoOrderMapper.domainToDomainOnUpdate(externalAlgoOrder, algoOrder);
        return algoOrderDataService.save(algoOrder);
    }

    public AlgoOrder cancelAlgoOrder(String exchangeInternalId, String instrumentInternalId,
                                     String algoOrderInternalId) {
        Exchange exchange = exchangeDataService.findRequiredByInternalId(exchangeInternalId);
        Instrument instrument = instrumentDataService.findRequiredByInternalId(instrumentInternalId);
        AlgoOrder algoOrder = algoOrderDataService.findRequiredByInternalId(algoOrderInternalId);

        List<AlgoOrder> externalAlgoOrders = clientManager.getClientService(exchange.getName())
                                                          .cancelAlgoOrder(algoOrder, instrument.getExternalId());

        AlgoOrder externalAlgoOrder = getRequiredByExternalId(externalAlgoOrders, algoOrder.getExternalId());
        algoOrderMapper.domainToDomainOnUpdate(externalAlgoOrder, algoOrder);
        algoOrder.setStatus(AlgoOrder.Status.CLOSED);
        return algoOrderDataService.save(algoOrder);
    }

    public AlgoOrder getByInternalId(String algoOrderInternalId) {
        return algoOrderDataService.findRequiredByInternalId(algoOrderInternalId);
    }

    public Page<AlgoOrder> getByParams(AlgoOrderSearchParams searchParams, Pageable pageable) {
        return algoOrderDataService.search(searchParams, pageable);
    }

    private AlgoOrder getRequiredByExternalId(List<AlgoOrder> orders, String externalId) {
        return orders.stream()
                     .filter(ord -> Objects.equals(externalId, ord.getExternalId()))
                     .findFirst()
                     .orElseThrow(() -> new TradingCommandException(HttpStatus.BAD_GATEWAY,
                                                                    "EMPTY_RESPONSE",
                                                                    ALGO_ORDER_NOT_FOUND_ON_EXCHANGE)
                     );

    }

    public void refreshActiveAlgoOrders(Deal deal) {
        Instrument instrument = instrumentDataService.findRequiredByDealId(deal.getId());
        Exchange exchange = exchangeDataService.findRequiredById(instrument.getExchangeId());
        refreshAlgoOrderExecutor.execute(exchange, instrument, deal.getId());
    }

    public void createMainProtection(Deal deal) {

    }

    public void cancelAttachedProtection(Deal deal) {

    }

    public void amendMainProtection(Deal deal) {

    }


//    TODO: Нужен StatusResolver, когда получаем ответ от биржи;
}
