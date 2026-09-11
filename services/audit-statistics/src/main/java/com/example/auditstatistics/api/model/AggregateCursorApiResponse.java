package com.example.auditstatistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * Позиция, с которой читается следующая страница агрегатов — ключ зерна
 * последней отданной строки
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Компоненты отдаются все, потому что все нужны для продолжения:</b>
 * ключ зерна уникален по построению, и отданный наполовину он позицию не
 * определяет. У зерна происшествий двух последних компонентов нет вовсе —
 * их нет в его ключе.
 */
@Getter
@Builder
public class AggregateCursorApiResponse {

    @Schema(description = "Сутки зерна последней отданной строки")
    private final LocalDate bucketDate;

    @Schema(description = "Биржевой счёт последней отданной строки")
    private final String exchangeAccountInternalId;

    @Schema(description = "Определение стратегии последней отданной строки — только у сделочного зерна; "
            + "пусто означает строку с пустым ключом, а не пропуск")
    private final String strategyInternalId;

    @Schema(description = "Расчётная валюта последней отданной строки — только у сделочного зерна; "
            + "пусто означает строку с пустым ключом, а не пропуск")
    private final String resultCurrency;
}
