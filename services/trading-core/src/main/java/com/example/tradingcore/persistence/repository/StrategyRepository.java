package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.StrategyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по копии определения стратегии. */
public interface StrategyRepository extends JpaRepository<StrategyEntity, Long> {

    Optional<StrategyEntity> findByInternalId(String internalId);

    /**
     * Активная стратегия ПАРЫ «счёт, инструмент» со всем деревом одним
     * запросом — вход сканера входа.
     *
     * <p>Радиус пары, а не инструмента: инструмент принадлежит площадке, и
     * у двух счетов одной площадки он один
     * (docs/architecture/tenant-and-exchange.md §«Торговая строка называет
     * счёт, и радиусы читаются от него»). Инвариант «одна активная на
     * пару» держит частичный уникальный индекс.
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
            where s.exchangeAccountId = :exchangeAccountId
              and s.instrumentId = :instrumentId
              and s.status = :status
            """)
    Optional<StrategyEntity> findByPairAndStatusWithTree(@Param("exchangeAccountId") Long exchangeAccountId,
                                                         @Param("instrumentId") Long instrumentId,
                                                         @Param("status") String status);

    /**
     * Копия-владелец закреплённой детали с объявлениями каталога и
     * клаузами фазы — вход чтения фич прохода сделки.
     *
     * <p>Дерево деталей не тянется: деталь у прохода уже своя, а нужны от
     * копии только объявления, по которым составляются привязки чтения, и
     * клаузы, по которым владелец данных классифицирует фазу.
     */
    @Query("""
            select distinct s from StrategyEntity s
            left join fetch s.marketPhaseSetting
            left join fetch s.indicatorSettings
            left join fetch s.marketStructureSettings
            join s.details d
            where d.id = :detailId
            """)
    Optional<StrategyEntity> findOwnerOfDetailWithSettings(@Param("detailId") Long detailId);

    /**
     * Копия с одними лишь объявлениями каталога — вход тика объявления
     * потребности. Дерево деталей ему не нужно: потребность выражают
     * настройки индикаторов и структуры, а не шаги и действия.
     */
    @Query("""
            select distinct s from StrategyEntity s
            left join fetch s.indicatorSettings
            left join fetch s.marketStructureSettings
            where s.id = :id
            """)
    Optional<StrategyEntity> findByIdWithSettings(@Param("id") Long id);

    /**
     * Идентичность копии-владельца закреплённой детали — ПРОЕКЦИЕЙ одного
     * поля.
     *
     * <p>Читатель — писатель события сделки: наружу едет идентичность, а
     * числовой ключ границу сервиса не пересекает. Загрузка детали с
     * деревом ради одного поля читала бы шаги, действия и транши, которых
     * писателю не нужно ни одного (.claude/rules/codestyle.md §«Выборка
     * данных: не тянем сущность ради одного поля»).
     */
    @Query("select s.internalId from StrategyEntity s join s.details d where d.id = :detailId")
    Optional<String> findStrategyInternalIdByDetailId(@Param("detailId") Long detailId);
}
