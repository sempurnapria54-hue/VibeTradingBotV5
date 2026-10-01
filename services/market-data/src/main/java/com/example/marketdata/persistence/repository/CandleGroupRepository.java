package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.CandleGroupEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы единиц сбора свечей. */
public interface CandleGroupRepository extends JpaRepository<CandleGroupEntity, Long> {

    Optional<CandleGroupEntity> findByInstrumentIdAndTimeframe(Long instrumentId, String timeframe);

    List<CandleGroupEntity> findByInstrumentId(Long instrumentId);

    List<CandleGroupEntity> findByStatusIn(Collection<String> statuses);

    /**
     * Группы заданного таймфрейма, готовые отдать историю расчёту.
     * Популяция производных: идентичность вычисления заказана глобально, а
     * инструменты приносят те группы, что уже собраны
     * (docs/architecture/market-data-collection.md).
     */
    List<CandleGroupEntity> findByTimeframeAndStatusIn(String timeframe, Collection<String> statuses);

    /** Идентификаторы инструментов, у которых есть хотя бы одна группа не в ACTIVE. */
    @Query("select distinct g.instrumentId from CandleGroupEntity g where g.status <> :activeStatus")
    List<Long> findInstrumentIdsWithUnreadyGroups(@Param("activeStatus") String activeStatus);

    /**
     * Итог шага цикла загрузки — точечной записью, под гардом застанного.
     *
     * <p><b>Горизонт запрос не пишет вовсе</b>: его писатель — приём
     * требования потребителя, а не цикл. <b>Гард — статус и горизонт,
     * застанные в начале шага:</b> требование, углубившее горизонт посреди
     * шага, меняет хотя бы одно из двух, и итог шага тогда не ложится
     * (docs/lifecycles/CandleGroup.md §«Возврат к `BACKFILL` по углублённому
     * требованию»). Горизонт сравнивается через {@code is not distinct from}:
     * пустой горизонт — законное значение «вся история». Аудит проставляется
     * здесь же — запрос мимо сущности слушателей JPA не проходит.
     *
     * @return число записанных строк: {@code 0} — группу переписали посреди шага
     */
    @Modifying
    @Query("""
            update CandleGroupEntity g
               set g.status = :status,
                   g.count = :rowCount,
                   g.actualFirstUtcMillis = :actualFirstUtcMillis,
                   g.actualLastUtcMillis = :actualLastUtcMillis,
                   g.repairAttempts = :repairAttempts,
                   g.modifiedAt = :modifiedAt, g.modifiedBy = :modifiedBy
             where g.id = :id
               and g.status = :loadedStatus
               and g.plannedFirstUtcMillis is not distinct from :loadedHorizon""")
    Integer applyLoadingStep(@Param("id") Long id,
                             @Param("status") String status,
                             @Param("rowCount") Long rowCount,
                             @Param("actualFirstUtcMillis") Long actualFirstUtcMillis,
                             @Param("actualLastUtcMillis") Long actualLastUtcMillis,
                             @Param("repairAttempts") Integer repairAttempts,
                             @Param("loadedStatus") String loadedStatus,
                             @Param("loadedHorizon") Long loadedHorizon,
                             @Param("modifiedAt") OffsetDateTime modifiedAt,
                             @Param("modifiedBy") String modifiedBy);

    /**
     * Углублённый горизонт и статус, выведенный из него, — точечной записью
     * под гардом застанного.
     *
     * <p><b>Счёт, границы и попытки докачки запрос не пишет вовсе</b>: их
     * писатель — шаг цикла загрузки, а не приём требования. <b>Гард —
     * статус и горизонт, застанные приёмом требования:</b> шаг, записавший
     * итог между чтением и записью требования, меняет статус, и горизонт
     * тогда не ложится — требование перечитывает группу и решает заново
     * (docs/lifecycles/CandleGroup.md §«Возврат к `BACKFILL` по углублённому
     * требованию»). Горизонт сравнивается через {@code is not distinct from}:
     * пустой горизонт — законное значение «вся история». Аудит проставляется
     * здесь же — запрос мимо сущности слушателей JPA не проходит.
     *
     * @return число записанных строк: {@code 0} — группу переписали между
     *         чтением и записью требования
     */
    @Modifying
    @Query("""
            update CandleGroupEntity g
               set g.plannedFirstUtcMillis = :horizon,
                   g.status = :status,
                   g.modifiedAt = :modifiedAt, g.modifiedBy = :modifiedBy
             where g.id = :id
               and g.status = :loadedStatus
               and g.plannedFirstUtcMillis is not distinct from :loadedHorizon""")
    Integer applyDeepenedHorizon(@Param("id") Long id,
                                 @Param("horizon") Long horizon,
                                 @Param("status") String status,
                                 @Param("loadedStatus") String loadedStatus,
                                 @Param("loadedHorizon") Long loadedHorizon,
                                 @Param("modifiedAt") OffsetDateTime modifiedAt,
                                 @Param("modifiedBy") String modifiedBy);
}
