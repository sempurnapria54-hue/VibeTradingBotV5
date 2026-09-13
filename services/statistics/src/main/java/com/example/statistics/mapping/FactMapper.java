package com.example.statistics.mapping;

import com.example.statistics.domain.model.DealFact;
import com.example.statistics.domain.model.IncidentFact;
import com.example.statistics.integration.internal.event.model.StatisticsEventMessage;
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Перевод принятого сообщения в факт своего зерна
 * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»).
 *
 * <p><b>Цель называется в имени метода</b>, потому что форм у одного
 * направления две: перегрузка по типу возврата невозможна, а
 * {@code messageToDomain} во множественном числе перестаёт адресовать.
 *
 * <p><b>У обоих методов политика ужесточена до {@code ERROR}.</b> Общий
 * дефолт мапперов заведён под слои, где часть полей цели законно пуста; у
 * факта пустое поле есть <b>потерянный операнд свёртки</b>, и замечает его
 * не сборка, а читатель агрегата — числом, которого не хватило.
 */
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface FactMapper {

    /**
     * Сделочный факт.
     *
     * <p><b>Ось времени — момент терминала сделки</b>, и приезжает он
     * моментом происшествия конверта: событие закрытия произошло тогда,
     * когда сделка закрылась (docs/models/domain/other/StatisticsFact.md
     * §«Сделочный факт»).
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    @Mapping(target = "closedAt", source = "occurredAt")
    DealFact messageToDealFact(StatisticsEventMessage message);

    /** Факт происшествия: ось времени — момент происшествия из конверта. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    IncidentFact messageToIncidentFact(StatisticsEventMessage message);
}
