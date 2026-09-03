package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.DealCashFlowEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
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
     * Принимающая корзина нераспознанного у этой биржи непуста —
     * СТОЯЩЕЕ состояние, по которому дедупится журнальный отчёт. Проекция
     * предиката, а не выборка строк: содержимое корзины дедупу не нужно.
     */
    Boolean existsByExchangeIdAndCategory(Long exchangeId, String category);

    /** Строки сделки, севшие в принимающую корзину, — вход перерезолва по текущему отображению. */
    List<DealCashFlowEntity> findByDealIdAndCategory(Long dealId, String category);

    /**
     * Строки разбивки сделки — операнды предиката остановки звена добычи
     * и догона курса. Окно не ограничивается: звено добычи работает по
     * строкам, которые само же и завело этим проходом, и ограничение
     * выборки здесь означало бы пропуск непогашенного курса.
     */
    List<DealCashFlowEntity> findByDealId(Long dealId);

    /**
     * Строки разбивки сделки ОГРАНИЧЕННЫМ окном — вход контекста
     * прохода. Мощность коллекции не задана конструкцией сделки, поэтому
     * выборка берётся окном; упёршаяся в потолок читается вызывающим как
     * неполнота, а не как усечение
     * (docs/spec/deal-context-load.json §cashFlowsComplete).
     */
    List<DealCashFlowEntity> findByDealIdOrderByExternalCreatedAtDesc(Long dealId, Pageable pageable);
}
