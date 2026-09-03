package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.DealCashFlowEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealCashFlowRepository extends JpaRepository<DealCashFlowEntity, Long> {

    /**
     * Дедуп добычи: движение уже сохранено этой биржей. Ключ — пара
     * (exchange_id, external_bill_id), носитель ключа — уникальное
     * ограничение схемы (docs/rules/idempotency-via-unique.md); проверка
     * даёт идемпотентный повторный проход без ловли нарушения.
     */
    Boolean existsByExchangeIdAndExternalBillId(Long exchangeId, String externalBillId);

    /**
     * Строки разбивки сделки — операнды предиката остановки звена добычи
     * и догона курса. Окно не ограничивается: строк на сделку счётно
     * мало (движения одного окна линковки), большой выборки здесь нет.
     */
    List<DealCashFlowEntity> findByDealId(Long dealId);
}
