package com.example.auditstatistics.domain.service;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.auditstatistics.config.AggregateReadProperties;
import com.example.auditstatistics.config.AggregatesPersistenceConfig;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.AggregateCursor;
import com.example.auditstatistics.domain.model.AggregateGrain;
import com.example.auditstatistics.domain.model.AggregatePage;
import com.example.auditstatistics.domain.model.AggregateQuery;
import com.example.auditstatistics.domain.model.DealAggregate;
import com.example.auditstatistics.domain.model.IncidentAggregate;
import com.example.auditstatistics.domain.model.JournalCompleteness;
import com.example.auditstatistics.persistence.service.DealAggregateDataService;
import com.example.auditstatistics.persistence.service.IncidentAggregateDataService;
import com.example.auditstatistics.persistence.service.StatisticsJournalCompletenessSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Агрегатная выборка чтения: строки выбранного зерна и объявленная полнота
 * журнала рядом с ними
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Ограничения отбора живут у владельца агрегатов, а не здесь.</b>
 * Этот класс их <b>исполняет</b>: зерно обязательно и с закрытым перечнем,
 * окно по суткам зерна обязательно и ограничено сверху, страница берётся
 * курсором по ключу выбранного зерна. Состав ограничений не
 * пересказывается (.claude/rules/policy-home.md).
 *
 * <p><b>Отвергается, а не сужается.</b> Запрос без окна и запрос с окном
 * шире предела получают отказ: молчаливое сужение отдало бы неполный ряд
 * под видом запрошенного (docs/concept.md, П1).
 *
 * <p><b>Полнота берётся источником АГРЕГАТНОГО подключения</b>
 * ({@link StatisticsJournalCompletenessSource}): журнал для модуля
 * статистики — чужая база, и читается он третьим подключением под ролью
 * агрегатов (docs/architecture/data-ownership.md §Раскладка). Тип
 * объявленного источника и есть охрана: подключение владельца журнала
 * сюда не подставляется.
 *
 * <p><b>Менеджер транзакций назван, а не подразумевается:</b> подключений у
 * процесса три, и умолчания у выбора нет намеренно
 * ({@link AggregatesPersistenceConfig}). Транзакция накрывает <b>строки</b>;
 * полнота лежит в другой базе, и общей транзакции с ними у неё не бывает по
 * построению. Обещать согласованность и не нужно — обе величины полноты
 * монотонны, и прочитанные позже строк они обещают <b>не больше</b>, чем
 * строки несут.
 */
@Service
@RequiredArgsConstructor
public class AggregateReadService {

    private final AggregateReadProperties aggregateReadProperties;
    private final ReceptionProperties receptionProperties;
    private final DealAggregateDataService dealAggregateDataService;
    private final IncidentAggregateDataService incidentAggregateDataService;
    private final JournalCompletenessService journalCompletenessService;
    private final StatisticsJournalCompletenessSource journalCompletenessSource;

    /**
     * Прочитать страницу окна вместе с тем, что журнал объявляет о своей
     * полноте.
     *
     * <p><b>Зерно выбирает и запрос, и форму строки:</b> заполняется ровно
     * один перечень страницы, второй остаётся пустым — «спрошено не это
     * зерно». Пустой перечень означает другое: «зерно выбрано, строк в окне
     * нет» (docs/rules/absent-value-semantics.md).
     *
     * <p><b>Полнота едет одной выдачей со строками</b>, потому что читатель
     * суточной строки без предиката не отличает «за сутки было три сделки»
     * от «за сутки принято три события, а приём стои́т с 10:00»
     * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
     */
    @Transactional(readOnly = true,
            transactionManager = AggregatesPersistenceConfig.AGGREGATES_TRANSACTION_MANAGER)
    public AggregatePage read(AggregateQuery query) {
        rejectUnlessAcceptable(query);
        Integer pageSize = aggregateReadProperties.getPageSize();
        if (AggregateGrain.DEAL.equals(query.getGrain())) {
            return dealPage(dealAggregateDataService.findPage(query, pageSize + 1), pageSize, completeness());
        }
        return incidentPage(incidentAggregateDataService.findPage(query, pageSize + 1), pageSize, completeness());
    }

    /**
     * Шесть отвержений вопроса, и все шесть — здесь
     * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
     * читает»).
     *
     * <p>Guard-clause вместо вложенных условий: тело метода остаётся
     * неглубоким (.claude/rules/codestyle.md §«Вложенность и
     * rich-модели»), а предикаты живут на самой доменной форме вопроса.
     *
     * <p><b>Зерно проверяется ПЕРВЫМ, и порядок несущий:</b> предикат чужих
     * компонентов позиции спрашивает о выбранном зерне, и на неназванном
     * ответа у него нет.
     */
    private void rejectUnlessAcceptable(AggregateQuery query) {
        if (isFalse(query.hasGrain())) {
            throw new ReadQueryRejectedException(
                    "Зерно обязательно и принимает одно из двух значений: DEAL либо INCIDENT");
        }
        if (isFalse(query.hasWindow())) {
            throw new ReadQueryRejectedException(
                    "Окно по суткам зерна обязательно: названы обе границы либо запрос не принимается");
        }
        if (isFalse(query.isWindowOrdered())) {
            throw new ReadQueryRejectedException(
                    "Правая граница окна не может быть раньше левой");
        }
        if (isTrue(query.isWindowWiderThan(aggregateReadProperties.getMaxWindowDays()))) {
            throw new ReadQueryRejectedException(
                    "Окно шире допустимого: " + aggregateReadProperties.getMaxWindowDays()
                            + " суток. Оно не сужается молча — читаются страницы более узких окон");
        }
        if (isTrue(query.hasPartialCursor())) {
            throw new ReadQueryRejectedException(
                    "Позиция задаётся сутками зерна и биржевым счётом вместе: "
                            + "один её компонент позицию не определяет");
        }
        if (isTrue(query.hasForeignGrainCursor())) {
            throw new ReadQueryRejectedException(
                    "Позиция несёт компоненты не выбранного зерна: определения стратегии и расчётной валюты "
                            + "в ключе зерна происшествий нет");
        }
    }

    /** Полнота журнала — обеими величинами, тем же ответом, что и строки. */
    private JournalCompleteness completeness() {
        OffsetDateTime moment = OffsetDateTime.now(ZoneOffset.UTC);
        return journalCompletenessService.completeness(journalCompletenessSource,
                receptionProperties.getGroupId(),
                moment.minus(receptionProperties.getStateMaxAge()));
    }

    /** Страница сделочного зерна: перечень происшествий остаётся пустым. */
    private AggregatePage dealPage(List<DealAggregate> found, Integer pageSize, JournalCompleteness completeness) {
        List<DealAggregate> handed = page(found, pageSize);
        return AggregatePage.builder()
                .grain(AggregateGrain.DEAL)
                .dealRows(handed)
                .nextCursor(dealCursor(found, pageSize))
                .completeness(completeness)
                .build();
    }

    /** Страница зерна происшествий: сделочный перечень остаётся пустым. */
    private AggregatePage incidentPage(List<IncidentAggregate> found, Integer pageSize,
                                       JournalCompleteness completeness) {
        List<IncidentAggregate> handed = page(found, pageSize);
        return AggregatePage.builder()
                .grain(AggregateGrain.INCIDENT)
                .incidentRows(handed)
                .nextCursor(incidentCursor(found, pageSize))
                .completeness(completeness)
                .build();
    }

    /**
     * Страница — прочитанное без лишней строки, которой узнавалось, есть ли
     * продолжение.
     *
     * <p>Метод общий у обоих зёрен: правило «лишняя прочитанная строка» одно,
     * и вторая его редакция расходилась бы с первой молча.
     */
    private <T> List<T> page(List<T> found, Integer pageSize) {
        if (found.size() > pageSize) {
            return found.subList(0, pageSize);
        }
        return found;
    }

    /**
     * Продолжение узнаётся ЛИШНЕЙ прочитанной строкой, а не равенством
     * размера странице.
     *
     * <p>Полная страница сама по себе не означает, что за ней что-то есть:
     * окно, чей остаток равен размеру страницы ровно, обещало бы читателю
     * ещё один — пустой — запрос.
     */
    private <T> Boolean hasMore(List<T> found, Integer pageSize) {
        return isNotEmpty(found) && found.size() > pageSize;
    }

    /** Позиция продолжения сделочного зерна — ключ последней ОТДАВАЕМОЙ строки. */
    private AggregateCursor dealCursor(List<DealAggregate> found, Integer pageSize) {
        if (isFalse(hasMore(found, pageSize))) {
            return null;
        }
        DealAggregate last = found.get(pageSize - 1);
        return new AggregateCursor(last.getBucketDate(),
                last.getExchangeAccountInternalId(),
                last.getStrategyInternalId(),
                last.getResultCurrency());
    }

    /**
     * Позиция продолжения зерна происшествий — ключ последней ОТДАВАЕМОЙ
     * строки; двух компонентов сделочного ключа у неё нет.
     */
    private AggregateCursor incidentCursor(List<IncidentAggregate> found, Integer pageSize) {
        if (isFalse(hasMore(found, pageSize))) {
            return null;
        }
        IncidentAggregate last = found.get(pageSize - 1);
        return new AggregateCursor(last.getBucketDate(), last.getExchangeAccountInternalId(), null, null);
    }
}
