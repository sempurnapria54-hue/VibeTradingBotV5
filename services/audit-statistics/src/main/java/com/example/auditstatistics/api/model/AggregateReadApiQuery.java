package com.example.auditstatistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Вопрос читателя агрегатов: зерно, окно по суткам и позиция страницы
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Объектом, а не семью параметрами:</b> при более чем двух параметрах
 * запроса контракт объявляется {@code @ParameterObject}
 * (.claude/rules/codestyle.md §«Контроллеры / API»).
 *
 * <p><b>Обязательность аннотациями НЕ выражена, и это то же решение, что у
 * журнальной выборки.</b> У зерна отвержений два (не названо и названо вне
 * перечня), у окна — три, и третье выразимо только кодом: предел есть
 * величина конфигурации сервиса ({@code AggregateReadProperties}). Часть
 * отвержений, положенная в аннотацию, развела бы один отказ по двум
 * носителям с разными кодами и разными текстами; поэтому все они живут
 * одним домом — доменной формой вопроса и её выборкой. Обязательность здесь
 * объявлена <b>описанием поверхности</b>, чтобы читатель контракта видел её
 * до первого отказа.
 *
 * <p><b>Зерно едет СТРОКОЙ, а не перечнем:</b> enum'ы объявляются только в
 * доменном слое (.claude/rules/codestyle.md §«Слои моделей и enum'ы»), и
 * разбор чужого значения принадлежит выборке, а не связыванию каркаса —
 * иначе тот же отказ отвечал бы другим кодом.
 *
 * <p><b>Тенанта форма не несёт:</b> он приезжает заголовком контекста,
 * который ставит периметр (docs/architecture/contracts.md §«Контекст
 * тенанта в вызове»), и операндом запроса не является.
 */
@Getter
@Setter
public class AggregateReadApiQuery {

    @Schema(description = "Зерно строки: DEAL — сделочное, INCIDENT — происшествий. Обязательно; "
            + "страница идёт внутри выбранного зерна",
            allowableValues = {"DEAL", "INCIDENT"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String grain;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Левая граница окна по суткам зерна (UTC), включающая; обязательна",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Правая граница окна по суткам зерна (UTC), включающая; обязательна. "
            + "Окно шире предела конфигурации отвергается, а не сужается молча",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate to;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Сутки зерна последней прочитанной строки — обязательный компонент позиции. "
            + "Оба обязательных компонента даются вместе либо позиция не даётся вовсе")
    private LocalDate cursorBucketDate;

    @Schema(description = "Биржевой счёт последней прочитанной строки — обязательный компонент позиции")
    private String cursorExchangeAccountInternalId;

    @Schema(description = "Определение стратегии позиции — компонент ключа СДЕЛОЧНОГО зерна. "
            + "Пусто означает строку «сделка стратегии не имеет», а не пропуск")
    private String cursorStrategyInternalId;

    @Schema(description = "Расчётная валюта позиции — компонент ключа СДЕЛОЧНОГО зерна. "
            + "Пусто означает строку «валюта результата не резолвилась», а не пропуск")
    private String cursorResultCurrency;
}
