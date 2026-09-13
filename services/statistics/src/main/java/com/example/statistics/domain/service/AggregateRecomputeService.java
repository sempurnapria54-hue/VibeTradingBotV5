package com.example.statistics.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.statistics.persistence.repository.DealGrainRow;
import com.example.statistics.persistence.repository.IncidentGrainRow;
import com.example.statistics.persistence.service.AggregateSourceDataService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Порция пересчёта: собрать зёрна одних суток из СВОИХ фактов и отдать их
 * писателю (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Сутки берутся по оси времени СВОЕГО зерна</b> — моменту терминала у
 * сделочного и моменту происшествия у зерна происшествий — и меряются
 * полуинтервалом {@code [начало суток; начало следующих)}: включающая
 * правая граница задваивала бы полночь в двух соседних сутках.
 *
 * <p><b>Охрана отбора — своя у каждого зерна</b>
 * (docs/spec/statistics-aggregates.json, {@code dayRecomputable}): сутки,
 * начавшиеся раньше начала ряда фактов зерна, покрыты им частично, и
 * собранная по ним строка заменила бы верные числа частичными — молча, при
 * свежем моменте сборки. Ряды двух зёрен наполняются независимо, поэтому и
 * охрана у них раздельная: общая запретила бы пересчёт суток, покрытых
 * одним зерном и не покрытых другим.
 */
@Service
@RequiredArgsConstructor
public class AggregateRecomputeService {

    private final AggregateSourceDataService aggregateSourceDataService;
    private final AggregateWriteService aggregateWriteService;

    /**
     * Пересчитать одни сутки окна: по запросу группировки на каждое
     * <b>покрытое</b> зерно и запись порции.
     *
     * <p><b>Оба зерна пишутся одной транзакцией порции</b>, даже когда
     * покрыто одно: половина записанной порции показала бы человеку сутки,
     * у которых сделочные числа новые, а счётчики происшествий прежние.
     * Непокрытое зерно отдаёт пустой перечень — строк не появляется ни
     * одной, и прежние числа остаются на своём моменте сборки.
     */
    public void recomputeDay(LocalDate bucketDate, OffsetDateTime assembledAt) {
        OffsetDateTime dayStart = bucketDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        List<DealGrainRow> dealRows = dayRecomputable(dayStart, aggregateSourceDataService.earliestDealFactMoment())
                ? aggregateSourceDataService.collectDealGrain(dayStart, dayEnd)
                : List.of();
        List<IncidentGrainRow> incidentRows =
                dayRecomputable(dayStart, aggregateSourceDataService.earliestIncidentFactMoment())
                        ? aggregateSourceDataService.collectIncidentGrain(dayStart, dayEnd)
                        : List.of();
        if (isFalse(dealRows.isEmpty()) || isFalse(incidentRows.isEmpty())) {
            aggregateWriteService.writeDay(bucketDate, dealRows, incidentRows, assembledAt);
        }
    }

    /**
     * Проход пишет эти сутки у этого зерна: ряд фактов зерна непуст И сутки
     * начались не раньше его начала
     * (docs/spec/statistics-aggregates.json, {@code dayRecomputable}).
     *
     * <p><b>Ложь на пустом ряде — не отказ, а тот же исход, что дала бы
     * группировка:</b> строк не появляется ни одной. <b>Граница
     * включающая:</b> сутки, начавшиеся ровно в момент первого факта, ряд
     * покрывает целиком.
     */
    private Boolean dayRecomputable(OffsetDateTime dayStart, OffsetDateTime grainMinFactMoment) {
        if (isNull(grainMinFactMoment)) {
            return Boolean.FALSE;
        }
        return isFalse(dayStart.isBefore(grainMinFactMoment));
    }
}
