package com.example.auditstatistics.domain.service;

import com.example.auditstatistics.config.AggregatesPersistenceConfig;
import com.example.auditstatistics.persistence.repository.journalread.DealGrainRow;
import com.example.auditstatistics.persistence.repository.journalread.IncidentGrainRow;
import com.example.auditstatistics.persistence.service.DealAggregateDataService;
import com.example.auditstatistics.persistence.service.IncidentAggregateDataService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакционная граница ПОРЦИИ пересчёта — одни сутки, обе таблицы
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Граница — сутки, а не проход и не строка.</b> Одна транзакция на
 * всё окно держала бы блокировки на всех пересчитываемых строках, а обрыв
 * посреди неё откатывал бы уже собранное. Обрыв <b>между</b> порциями не
 * стои́т ничего: проекция от порядка не зависит и повтора не боится —
 * часть суток остаётся пересчитанной, часть с прежними числами, и каждая
 * половина верна на свой момент сборки.
 *
 * <p><b>Оба зерна пишутся одной транзакцией порции</b>, потому что оба
 * собраны из одного журнала одним проходом суток: половина записанной
 * порции показала бы человеку сутки, у которых сделочные числа новые, а
 * счётчики происшествий прежние.
 *
 * <p><b>Момент сборки едет той же транзакцией, что и числа</b>, и приходит
 * параметром: его ставит проход, а не запрос.
 *
 * <p><b>Менеджер транзакций назван, а не подразумевается:</b> подключений
 * у процесса три, и умолчания у выбора нет намеренно
 * ({@link AggregatesPersistenceConfig}).
 */
@Service
@RequiredArgsConstructor
public class AggregateWriteService {

    private final DealAggregateDataService dealAggregateDataService;
    private final IncidentAggregateDataService incidentAggregateDataService;

    /** Записать обе таблицы за одни сутки окна. */
    @Transactional(transactionManager = AggregatesPersistenceConfig.AGGREGATES_TRANSACTION_MANAGER)
    public void writeDay(LocalDate bucketDate,
                         List<DealGrainRow> dealRows,
                         List<IncidentGrainRow> incidentRows,
                         OffsetDateTime assembledAt) {
        dealRows.forEach(row -> dealAggregateDataService.upsert(row, bucketDate, assembledAt));
        incidentRows.forEach(row -> incidentAggregateDataService.upsert(row, bucketDate, assembledAt));
    }
}
