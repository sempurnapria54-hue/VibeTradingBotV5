package com.example.auditstatistics.api.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Строка журнала событий в форме ответа поверхности
 * (docs/models/domain/other/AuditRecord.md).
 *
 * <p><b>Тенанта форма не несёт, и это решение.</b> Радиус приезжает
 * операндом вызова, и всякая строка ответа несёт его по построению отбора:
 * поле повторяло бы вопрос в каждом элементе ответа
 * (.claude/rules/design-simplicity.md). Условие пересмотра — выборка
 * поперёк тенантов, которой сегодня нет ни одной строкой вызова
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 *
 * <p><b>Ключа базы форма не несёт тем более:</b> наружу идёт идентичность
 * события, она же ключ дедупа доставки
 * (.claude/rules/codestyle.md §«Идентичность наружу»).
 */
@Getter
@Builder
public class AuditRecordApiResponse {

    @Schema(description = "Идентичность события; она же ключ дедупа доставки и внешняя идентичность строки")
    private final String eventId;

    @Schema(description = "Класс события — дискриминатор содержимого, имя из перечня производителя")
    private final String eventType;

    @Schema(description = "Момент происшествия из конверта, от производителя; по нему идут окно и порядок")
    private final OffsetDateTime occurredAt;

    @Schema(description = "Момент приёма события журналом; по нему считается нижняя граница полноты")
    private final OffsetDateTime recordedAt;

    @Schema(description = "Версия формы содержимого из конверта")
    private final Integer version;

    @Schema(description = "Контекст трассировки; пусто законно и означает «трассировки не было»")
    private final String traceContext;

    @Schema(description = "Радиус: биржевой счёт; пусто — содержимое идентичности не несёт")
    private final String exchangeAccountInternalId;

    @Schema(description = "Радиус: инструмент; пусто — содержимое идентичности не несёт")
    private final String instrumentInternalId;

    @Schema(description = "Радиус: сделка; пусто — содержимое идентичности не несёт")
    private final String dealInternalId;

    @Schema(description = "Радиус: определение стратегии; пусто — содержимое идентичности не несёт")
    private final String strategyInternalId;

    /**
     * Содержимое события целиком, как доставлено.
     *
     * <p><b>Едет ОБЪЕКТОМ, а не строкой с экранированием.</b> Журнал
     * содержимое не разбирает — форму знает только производитель класса, —
     * но и переупаковывать его в строку не должен: читатель получил бы
     * JSON внутри JSON и разбирал бы дважды.
     *
     * <p><b>Проверено пробой, а не прочитано в документации
     * библиотеки.</b> Аннотация лежит в {@code jackson-annotations} —
     * пакете, который Jackson 3 сохранил, — но соседняя по виду аннотация
     * из {@code databind} была бы <b>молча проигнорирована</b> на этом
     * проводе (.claude/rules/tech-radar.md, записи Jackson;
     * .claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
     * сериализацию»). Поэтому исход закреплён тестом поверхности, а не
     * доверием к пакету: он же уронит сборку, если миграция на Jackson 3
     * это поведение сдвинет.
     */
    @JsonRawValue
    @Schema(description = "Содержимое события целиком, как доставлено; журнал его не разбирает",
            type = "object")
    private final String content;
}
