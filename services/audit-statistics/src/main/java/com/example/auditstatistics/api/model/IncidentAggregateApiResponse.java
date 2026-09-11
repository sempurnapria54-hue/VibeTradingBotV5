package com.example.auditstatistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Строка зерна происшествий в форме ответа поверхности
 * (docs/rules/statistics-aggregates.md §«Счётчики происшествий — своё
 * зерно, а не строка сделочного агрегата»).
 *
 * <p><b>Расчётной валюты и определения стратегии в ключе зерна нет</b>, и
 * это не упущение формы: ни одно из четырёх её событий валюты результата не
 * несёт, а определение есть только у двух из четырёх — введённое в зерно,
 * оно дало бы у двух других пустой ключ со вторым смыслом.
 *
 * <p><b>Счётчика остановок среди величин нет</b>: событие остановки
 * публикуется на каждом ребре присвоения причины, и счётчик по классу
 * события дал бы одной сделке вес два.
 */
@Getter
@Builder
public class IncidentAggregateApiResponse {

    @Schema(description = "Биржевой счёт: компонент ключа зерна")
    private final String exchangeAccountInternalId;

    @Schema(description = "Сутки UTC: компонент ключа зерна, по нему идут окно и порядок")
    private final LocalDate bucketDate;

    @Schema(description = "Заведённых сделок за сутки")
    private final Integer openedDeals;

    @Schema(description = "Решений о создании обычной заявки; перестановка ноги даёт своё решение")
    private final Integer orderDecisions;

    @Schema(description = "Поднятых ступеней — обе тропы вместе")
    private final Integer raisedHolds;

    @Schema(description = "Из них жёстких: без этого разреза три мягких холда и одно "
            + "сворачивание счёта читались бы одинаково")
    private final Integer hardRaisedHolds;

    @Schema(description = "Из них поднятых ручной тропой, а не проходом; "
            + "тропу различает код операции, а не актор строки")
    private final Integer manuallyRaisedHolds;

    @Schema(description = "Отчётов о происшествиях — обе тропы вместе")
    private final Integer anomalyReports;

    @Schema(description = "Из них критичных: «kill-switch гонялся» и «не гонялся» в одном числе неразличимы")
    private final Integer criticalAnomalyReports;

    @Schema(description = "Из них заведённых ручной тропой, а не детекцией")
    private final Integer manualOperationReports;

    @Schema(description = "Момент сборки строки: им читатель видит лаг проекции")
    private final OffsetDateTime assembledAt;
}
