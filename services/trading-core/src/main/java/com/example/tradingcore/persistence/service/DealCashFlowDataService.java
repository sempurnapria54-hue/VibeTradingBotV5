package com.example.tradingcore.persistence.service;

import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.mapping.DealCashFlowMapper;
import com.example.tradingcore.persistence.repository.DealCashFlowRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для строки разбивки движений средств.
 *
 * <p>Принимающая корзина адресуется значением категории {@code OTHER}: она
 * и есть состояние «тип отображением не покрыт»
 * (docs/models/mapping/DealCashFlow.md §«Резолв категории»).
 */
@Service
@RequiredArgsConstructor
public class DealCashFlowDataService {

    private static final String UNCLASSIFIED = DealCashFlow.CashFlowCategory.OTHER.name();

    private final DealCashFlowRepository repository;
    private final DealCashFlowMapper mapper;

    @Transactional
    public DealCashFlow save(DealCashFlow flow) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(flow)));
    }

    /** Запись счёта уже приземлена: повторный проход строк не задваивает. */
    @Transactional(readOnly = true)
    public Boolean exists(Long exchangeAccountId, String externalBillId) {
        return repository.existsByExchangeAccountIdAndExternalBillId(exchangeAccountId, externalBillId);
    }

    /** Строки сделки — вход догона курса и сверки разбивки. */
    @Transactional(readOnly = true)
    public List<DealCashFlow> findByDeal(Long dealId) {
        return repository.findByDealId(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /**
     * Строки сделки окном с потолком — вход сборки контекста прохода.
     *
     * <p><b>Читается на одну строку больше потолка, и это не описка:</b>
     * упёршаяся в потолок выборка обязана быть ОТЛИЧИМА от ровно
     * поместившейся. Без лишней строки размер результата равен потолку в
     * обоих случаях, и признак полноты разбивки был бы истинным на
     * усечённом множестве — тихое усечение в разрешающую сторону
     * (docs/spec/deal-context-load.json §cashFlowsComplete).
     */
    @Transactional(readOnly = true)
    public List<DealCashFlow> findByDealWindow(Long dealId, Integer limit) {
        return repository.findByDealIdOrderByExternalCreatedAtDesc(dealId, PageRequest.of(0, limit + 1)).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /** Строки сделки из принимающей корзины — вход перерезолва по текущему отображению. */
    @Transactional(readOnly = true)
    public List<DealCashFlow> findUnclassifiedByDeal(Long dealId) {
        return repository.findByDealIdAndCategory(dealId, UNCLASSIFIED).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    /**
     * Принимающая корзина счёта непуста — операнд дедупа журнального
     * отчёта: он объявляется на ВОЗНИКНОВЕНИЕ состояния, а не на каждую
     * строку.
     */
    @Transactional(readOnly = true)
    public Boolean unclassifiedBasketStands(Long exchangeAccountId) {
        return repository.existsByExchangeAccountIdAndCategory(exchangeAccountId, UNCLASSIFIED);
    }
}
