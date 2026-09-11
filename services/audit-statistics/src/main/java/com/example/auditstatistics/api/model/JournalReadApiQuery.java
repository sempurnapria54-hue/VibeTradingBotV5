package com.example.auditstatistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Вопрос читателя журнала: окно, позиция страницы и необязательные отборы
 * по радиусам
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 *
 * <p><b>Объектом, а не восемью параметрами:</b> при более чем двух
 * параметрах запроса контракт объявляется {@code @ParameterObject}
 * (.claude/rules/codestyle.md §«Контроллеры / API»).
 *
 * <p><b>Обязательность окна аннотацией НЕ выражена, и это решение.</b>
 * Отвержений у окна три — его нет, оно перевёрнуто, оно шире предела, — и
 * третье выразимо только кодом: предел есть величина конфигурации сервиса
 * ({@code JournalReadProperties}), а не константа. Часть отвержений,
 * положенная в аннотацию, развела бы один отказ по двум носителям с
 * разными кодами и разными текстами; поэтому все три живут одним домом —
 * доменной формой вопроса и её выборкой. Обязательность здесь объявлена
 * <b>описанием поверхности</b>, чтобы читатель контракта видел её до
 * первого отказа.
 *
 * <p><b>Тенанта форма не несёт:</b> он приезжает заголовком контекста,
 * который ставит периметр (docs/architecture/contracts.md §«Контекст
 * тенанта в вызове»), и операндом запроса не является.
 */
@Getter
@Setter
public class JournalReadApiQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Левая граница окна по моменту происшествия, включающая; обязательна",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private OffsetDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Правая граница окна по моменту происшествия, включающая; обязательна. "
            + "Окно шире предела конфигурации отвергается, а не сужается молча",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private OffsetDateTime to;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Момент происшествия последней прочитанной строки — первая половина курсора. "
            + "Обе половины даются вместе либо не даются вовсе")
    private OffsetDateTime cursorOccurredAt;

    @Schema(description = "Идентичность последней прочитанной строки — вторая половина курсора")
    private String cursorEventId;

    @Schema(description = "Отбор по биржевому счёту; необязателен, конъюнктивен с прочими")
    private String exchangeAccountInternalId;

    @Schema(description = "Отбор по инструменту; необязателен, конъюнктивен с прочими")
    private String instrumentInternalId;

    @Schema(description = "Отбор по сделке; необязателен, конъюнктивен с прочими")
    private String dealInternalId;

    @Schema(description = "Отбор по определению стратегии; необязателен, конъюнктивен с прочими")
    private String strategyInternalId;
}
