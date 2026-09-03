package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByInternalId(String internalId);

    List<InstrumentEntity> findByStatus(String status);

    List<InstrumentEntity> findByStatusIn(Collection<String> statuses);

    /**
     * Контур целиком, ограниченным окном: статус инструмента выборку
     * <b>не сужает</b>. Проактивная детекция обходит контур по факту
     * живого риска, а не по готовности к торговле — заблокированный
     * инструмент из обхода не выпадает (docs/components/AnomalyJob.md).
     * Окно вместо {@code findAll()} — {@code codestyle} §«Выборка
     * данных»; упор в окно читатель засчитывает неполнотой прохода.
     */
    List<InstrumentEntity> findAllBy(Pageable pageable);

    /**
     * Проекция расчётной валюты инструмента биржи по внешнему id — резолв
     * одного поля без выгрузки сущности (лестница курса чужой валюты,
     * docs/components/RefreshBillsExecutor.md).
     */
    @Query("""
            select i.externalSettlementCurrency from InstrumentEntity i
            where i.exchangeId = :exchangeId and i.externalId = :externalId""")
    Optional<String> findSettlementCurrency(@Param("exchangeId") Long exchangeId,
                                            @Param("externalId") String externalId);

    /** Гардированный statusный переход (только из ожидаемого {@code from}); возвращает число затронутых строк. */
    @Modifying
    @Query("update InstrumentEntity i set i.status = :to where i.id = :id and i.status = :from")
    int updateStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);

    /**
     * Тот же гардированный переход с НЕСКОЛЬКИМИ допустимыми исходными
     * статусами — эскалация ступени, у которой исходных состояний больше
     * одного (мягкая ступень стои́т либо не стои́т).
     */
    @Modifying
    @Query("update InstrumentEntity i set i.status = :to where i.id = :id and i.status in :from")
    int updateStatusFromAny(@Param("id") Long id, @Param("from") Collection<String> from,
                            @Param("to") String to);

    /** Проекция: JSONB-навес внешних правил по id — без вытягивания всей сущности. */
    @Query("select i.externalRules from InstrumentEntity i where i.id = :id")
    Optional<String> findExternalRulesById(@Param("id") Long id);

    @Query("select distinct i from InstrumentEntity i left join fetch i.candleGroups where i.id = :id")
    Optional<InstrumentEntity> findByIdWithCandleGroups(@Param("id") Long id);

    @Query("select i.internalId from InstrumentEntity i where i.id = :id")
    Optional<String> findInternalIdById(@Param("id") Long id);

    /** Проекция: биржа инструмента по id — операнд резолва ставки комиссии. */
    @Query("select i.exchangeId from InstrumentEntity i where i.id = :id")
    Optional<Long> findExchangeIdById(@Param("id") Long id);

    @Query("select i.id from InstrumentEntity i where i.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);
}
