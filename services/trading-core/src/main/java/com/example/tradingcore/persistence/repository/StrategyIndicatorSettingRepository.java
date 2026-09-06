package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.StrategyIndicatorSettingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Запросы по объявлению индикатора в копии определения.
 *
 * <p>Строка принадлежит копии, но одна её колонка — идентичность
 * вычисления — принадлежит потребителю, и пишется она <b>точечно</b>:
 * сохранение строки целиком затирало бы копию значениями, устаревшими на
 * возраст прохода.
 */
public interface StrategyIndicatorSettingRepository extends JpaRepository<StrategyIndicatorSettingEntity, Long> {

    /**
     * Стратегии, у которых есть объявление без идентичности вычисления, —
     * вход тика объявления потребности.
     *
     * <p>Проекция идентификаторов, а не строк: тику нужен перечень
     * стратегий, а дерево он грузит по одной
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    @Query("""
            select distinct s.strategy.id from StrategyIndicatorSettingEntity s
            where s.computationConfigInternalId is null""")
    List<Long> findStrategyIdsWithUnboundComputation();

    /**
     * Write-once привязки: уже привязанное объявление повторной записью не
     * перетирается. Охрана стои́т в самом запросе — копия неизменяема, и
     * вторая идентичность у одного объявления означала бы, что значения
     * читаются по двум разным вычислениям.
     */
    @Modifying
    @Query("""
            update StrategyIndicatorSettingEntity s set s.computationConfigInternalId = :configInternalId
            where s.id = :id and s.computationConfigInternalId is null""")
    int bindComputation(@Param("id") Long id, @Param("configInternalId") String configInternalId);
}
