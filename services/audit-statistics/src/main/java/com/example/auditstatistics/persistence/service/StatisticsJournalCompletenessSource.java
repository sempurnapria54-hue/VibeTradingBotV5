package com.example.auditstatistics.persistence.service;

import com.example.auditstatistics.domain.service.JournalCompletenessSource;
import com.example.auditstatistics.persistence.repository.journalread.ReceptionStateSourceRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Операнды полноты, читаемые под ролью АГРЕГАТОВ — кросс-подключением к
 * базе журнала (docs/architecture/data-ownership.md §Раскладка:
 * «Читателей у третьего подключения два»).
 *
 * <p><b>Этим источником полноту берёт агрегатная выборка, и другого ей не
 * дозволено.</b> Журнал для модуля статистики — чужая база; взятая
 * подключением владельца журнала, та же величина читалась бы под ролью, у
 * которой на журнале есть и запись, — и грант перестал бы держать что-либо,
 * оставшись выданным (docs/architecture/data-ownership.md §«Чего грант НЕ
 * охраняет»).
 *
 * <p><b>Границей трёх операндов состояния приёма служит этот класс, а
 * четвёртого — уже существующая</b> ({@link AggregateSourceDataService}
 * читает самый ранний момент приёма тем же подключением для джобы
 * пересчёта). Асимметрия названа, а не случайна: у трёх запросов состояния
 * на кросс-подключении иного потребителя нет, и отдельная граница была бы
 * носителем без предмета; у четвёртого граница есть, и второй ей не
 * заводится (.claude/rules/design-simplicity.md).
 *
 * <p><b>Собственной транзакции методы не открывают.</b> Читающий запрос
 * идёт транзакцией своего менеджера, назначенного объявлением
 * репозиториев; общей транзакции с чтением строк агрегатов у него не
 * бывает по построению — базы разные, и кросс-базового запроса не
 * существует.
 */
@Service
@RequiredArgsConstructor
public class StatisticsJournalCompletenessSource implements JournalCompletenessSource {

    private final AggregateSourceDataService aggregateSourceDataService;
    private final ReceptionStateSourceRepository repository;

    @Override
    public OffsetDateTime earliestRecordedAt() {
        return aggregateSourceDataService.earliestRecordedAt();
    }

    @Override
    public OffsetDateTime latestObservedSince(String consumerGroup) {
        return repository.latestObservedSince(consumerGroup);
    }

    @Override
    public Long countSubscribedPairs(String consumerGroup) {
        return repository.countSubscribedPairs(consumerGroup);
    }

    @Override
    public Long countSubscribedPairsWithBreak(String consumerGroup, OffsetDateTime staleBefore) {
        return repository.countSubscribedPairsWithBreak(consumerGroup, staleBefore);
    }
}
