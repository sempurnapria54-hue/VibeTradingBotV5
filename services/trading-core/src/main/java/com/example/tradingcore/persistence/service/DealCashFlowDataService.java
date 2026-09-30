package com.example.tradingcore.persistence.service;

import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.mapping.DealCashFlowMapper;
import com.example.tradingcore.persistence.model.DealCashFlowEntity;
import com.example.tradingcore.persistence.repository.DealCashFlowRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;
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

    /**
     * Потолок идентификаторов в одном запросе отсечки: перечень параметров
     * запроса у драйвера конечен, а окно движений счёта — нет.
     */
    private static final int LANDED_LOOKUP_CHUNK = 1000;

    private final DealCashFlowRepository repository;
    private final DealCashFlowMapper mapper;
    private final PointWriteAudit audit;

    @Transactional
    public DealCashFlow save(DealCashFlow flow) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(flow)));
    }

    /**
     * Завести строку по ключу идемпотентности «счёт, идентификатор записи»,
     * если её ещё нет; {@code true} — строка легла этим вызовом, {@code false}
     * — запись уже приземлена другим ходом, и следствий у вызова нет.
     *
     * <p><b>Решает вставка по ключу, а не проверка перед ней</b>
     * (docs/rules/idempotency-via-unique.md): проверка и сохранение не
     * атомарны, и конкурентный писатель той же записи падал бы нарушением
     * ключа вместо поглощения. Автор и момент кладутся этой границей: нативная
     * вставка слушателей аудита не проходит.
     */
    @Transactional
    public Boolean saveIfAbsent(DealCashFlow flow) {
        DealCashFlowEntity row = mapper.domainToPersistence(flow);
        return repository.insertIfAbsent(row.getExchangeAccountId(), row.getDealId(), row.getCategory(),
                row.getAmount(), row.getCcy(), row.getExternalFee(), row.getPositionBalanceChange(),
                row.getAppliedRate(), row.getRateStatus(), row.getAppliedRateCandleInstrument(),
                row.getAppliedRateCandleTimeframe(), row.getAppliedRateCandleOpenTime(),
                row.getExternalInstrumentId(), row.getExternalBillId(), row.getExternalType(),
                row.getExternalSubType(), row.getExternalOrderId(), audit.moment(), audit.writer(),
                row.getExternalCreatedAt(), row.getExternalModifiedAt()) > 0;
    }

    /**
     * Какие из НАЗВАННЫХ записей счёта уже приземлены — <b>отсечка объёма
     * прохода, а не дедуп</b>. Повторный проход перечитывает окно целиком, и
     * без отсечки каждая старая строка заново проходила бы лестницу курса —
     * платные вызовы источника за уже добытое. Дедуп при этом держит
     * {@link #saveIfAbsent}: запись, приземлённая между отсечкой и вставкой,
     * поглощается ключом.
     */
    @Transactional(readOnly = true)
    public Set<String> findLandedBillIds(Long exchangeAccountId, List<String> externalBillIds) {
        Set<String> landed = new HashSet<>();
        for (List<String> chunk : ListUtils.partition(externalBillIds, LANDED_LOOKUP_CHUNK)) {
            landed.addAll(repository.findLandedBillIds(exchangeAccountId, chunk));
        }
        return landed;
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
