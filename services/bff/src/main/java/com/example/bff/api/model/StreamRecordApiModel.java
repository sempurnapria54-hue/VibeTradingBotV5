package com.example.bff.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Запись потока живых данных — единственная форма, которую периметр
 * кладёт в SSE.
 *
 * <p><b>Она порождается периметром, поэтому и форма его</b>
 * (docs/architecture/contracts.md §«Форму задаёт происхождение:
 * порождённое — своё, пересланное — чужое»). Доменный класс наружу не
 * выходит ни в одном поле: {@code content} — всегда api-модель периметра
 * из {@code api.model.stream}.
 *
 * @param id         идентичность записи; у фактов равна идентичности
 *                   события, у порождённых периметром — пуста
 * @param type       класс записи: класс события либо запись периметра
 *                   ({@code PERIMETER_PULSE}, {@code PERIMETER_GAP})
 * @param occurredAt момент происшествия для фактов, момент отправки для
 *                   записей периметра; время UTC
 * @param content    содержимое записи в форме периметра; у пульса и
 *                   разрыва пусто — они не несут ничего сверх факта
 *                   своего появления
 */
public record StreamRecordApiModel(
        @Schema(description = "Идентичность записи; у фактов равна идентичности события") String id,
        @Schema(description = "Класс записи: класс события либо запись периметра") String type,
        @Schema(description = "Момент происшествия либо отправки, UTC") OffsetDateTime occurredAt,
        @Schema(description = "Содержимое записи в форме периметра") Object content) {
}
