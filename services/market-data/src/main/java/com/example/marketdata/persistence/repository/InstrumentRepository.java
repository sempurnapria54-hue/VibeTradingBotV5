package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.InstrumentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы каталога инструментов. */
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByInternalId(String internalId);

    Optional<InstrumentEntity> findByExchangeCodeAndExternalId(String exchangeCode, String externalId);

    List<InstrumentEntity> findByStatusIn(Collection<String> statuses);

    /**
     * Действующий листинг площадки стабильным порядком и ограниченным
     * окном — популяция прохода сбора срезов. Порядок по идентификатору:
     * при нехватке бюджета усечённым оказывается один и тот же хвост, а
     * не случайные инструменты (docs/processes/snapshot-collection.md).
     */
    List<InstrumentEntity> findByExchangeCodeAndStatusInOrderByIdAsc(String exchangeCode,
                                                                    Collection<String> statuses,
                                                                    Pageable pageable);

    /**
     * Окно листинга ЗА курсором — популяция обновления справочных правил.
     * Правила площадка отдаёт поинструментно, полный обход не помещается
     * в бюджет лимитов, а окно от начала обновляло бы вечно один и тот же
     * префикс: за курсором обходится весь листинг по кругу.
     */
    List<InstrumentEntity> findByExchangeCodeAndStatusInAndIdGreaterThanOrderByIdAsc(String exchangeCode,
                                                                                     Collection<String> statuses,
                                                                                     Long cursorId,
                                                                                     Pageable pageable);

    /** Проекция навеса справочных правил — без вытягивания всей сущности. */
    @Query("select i.externalRules from InstrumentEntity i where i.id = :id")
    Optional<String> findExternalRulesById(@Param("id") Long id);

    /** Проекция internalId по числовому идентификатору. */
    @Query("select i.internalId from InstrumentEntity i where i.id = :id")
    Optional<String> findInternalIdById(@Param("id") Long id);

    /** Проекция числового идентификатора по internalId. */
    @Query("select i.id from InstrumentEntity i where i.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);
}
