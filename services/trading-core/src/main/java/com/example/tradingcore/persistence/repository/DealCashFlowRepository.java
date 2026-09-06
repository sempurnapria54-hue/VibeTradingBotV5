package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.DealCashFlowEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы по строке разбивки движений средств. */
public interface DealCashFlowRepository extends JpaRepository<DealCashFlowEntity, Long> {

    /** Ключ идемпотентности: запись счёта уже приземлена. */
    Boolean existsByExchangeAccountIdAndExternalBillId(Long exchangeAccountId, String externalBillId);

    /** Строки сделки — вход сверки разбивки и догона курса. */
    List<DealCashFlowEntity> findByDealId(Long dealId);

    /**
     * Строки сделки окном по убыванию времени события — вход сборки
     * контекста прохода.
     *
     * <p>Окно обязательно, и это единственная коллекция контекста, чья
     * мощность не задана конструкцией сделки: у долгой сделки с частым
     * начислением финансирования она растёт со временем жизни
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    List<DealCashFlowEntity> findByDealIdOrderByExternalCreatedAtDesc(Long dealId, Pageable pageable);

    /** Строки сделки, севшие в принимающую корзину: вход перерезолва по текущему отображению. */
    List<DealCashFlowEntity> findByDealIdAndCategory(Long dealId, String category);

    /**
     * Принимающая корзина счёта непуста. Операнд дедупа журнального отчёта:
     * он про СОСТОЯНИЕ корзины, а не про строку, и без этого запроса
     * «состояние держится» было бы неотличимо от «возникло заново».
     */
    Boolean existsByExchangeAccountIdAndCategory(Long exchangeAccountId, String category);
}
