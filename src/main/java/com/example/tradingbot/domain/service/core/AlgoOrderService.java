package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.search_params.AlgoOrderSearchParams;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshAlgoOrderExecutor;
import com.example.tradingbot.domain.service.deal.command.refresh.SyncAlgoOrderExecutor;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SyncAlgoOrderExecutor syncAlgoOrderExecutor;

    @Transactional
    public AlgoOrder createAlgoOrder(String dealInternalId, AlgoOrder request) {
        Deal deal = dealDataService.findRequiredByInternalId(dealInternalId);

        AlgoOrder algoOrder = new AlgoOrder();
        algoOrderMapper.domainToDomainOnCreate(request, algoOrder);
        algoOrder.setStatus(AlgoOrder.Status.CREATED);
        return algoOrderDataService.save(algoOrder);
    }

    public AlgoOrder syncAlgoOrder(String exchangeInternalId, String internalId) {
        Exchange exchange = exchangeDataService.findRequiredByInternalId(exchangeInternalId);
        AlgoOrder algoOrder = algoOrderDataService.findRequiredByInternalId(internalId);
        return syncAlgoOrderExecutor.execute(exchange, algoOrder);
    }

    public AlgoOrder getByInternalId(String algoOrderInternalId) {
        return algoOrderDataService.findRequiredByInternalId(algoOrderInternalId);
    }

    public Page<AlgoOrder> getByParams(AlgoOrderSearchParams searchParams, Pageable pageable) {
        return algoOrderDataService.search(searchParams, pageable);
    }

    public void createMainProtection(Deal deal) {

    }

    public void cancelAttachedProtection(Deal deal) {

    }

    public void amendMainProtection(Deal deal) {

    }


//    TODO: Нужен StatusResolver, когда получаем ответ от биржи;
}
