package com.example.strategies.persistence.repository;

import com.example.strategies.persistence.model.StrategyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по определению стратегии у его владельца. */
public interface StrategyRepository extends JpaRepository<StrategyEntity, Long> {

    Optional<StrategyEntity> findByInternalId(String internalId);

    /**
     * Определение со всем деревом одним запросом — вход чтения и снимка,
     * который поедет в событие активации.
     *
     * <p>Дерево тянется {@code join fetch} по множествам — без N+1 и без
     * {@code MultipleBagFetchException}; порядок шагов и действий
     * восстанавливает маппер.
     */
    @Query("""
            select distinct s from StrategyEntity s
            left join fetch s.marketPhaseSetting
            left join fetch s.indicatorSettings
            left join fetch s.marketStructureSettings
            left join fetch s.details d
            left join fetch d.steps st
            left join fetch st.actions
            left join fetch d.tranches tr
            left join fetch tr.steps tst
            left join fetch tst.actions
            where s.internalId = :internalId
            """)
    Optional<StrategyEntity> findByInternalIdWithTree(@Param("internalId") String internalId);

    /**
     * Определения тенанта без дерева — поверхность перечня работает в его
     * контексте и деталей не отдаёт.
     *
     * <p><b>Окно обязательно.</b> Число определений тенанта сверху ничем
     * не ограничено, а «вытащить всё» на растущей таблице кладёт и базу, и
     * ответ (.claude/rules/codestyle.md §«Выборка данных»). Порядок —
     * от новых к старым: усечение отрезает наименее интересный хвост.
     */
    List<StrategyEntity> findByTenantInternalIdOrderByIdDesc(String tenantInternalId, Pageable pageable);

    /**
     * Идентичность активного определения пары — операнд инварианта «одна
     * активная на паре».
     *
     * <p><b>Проекция поля, а не сущность:</b> проверке нужен ответ «есть
     * ли и какое», а не дерево (.claude/rules/codestyle.md §«Выборка
     * данных: не тянем сущность ради одного поля»).
     */
    @Query("""
            select s.internalId from StrategyEntity s
            where s.exchangeAccountInternalId = :exchangeAccountInternalId
              and s.instrumentInternalId = :instrumentInternalId
              and s.status = :status
            """)
    Optional<String> findActiveInternalIdOnPair(
            @Param("exchangeAccountInternalId") String exchangeAccountInternalId,
            @Param("instrumentInternalId") String instrumentInternalId,
            @Param("status") String status);
}
