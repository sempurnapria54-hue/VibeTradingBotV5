package com.example.auditstatistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Позиция, с которой читается следующая страница журнала.
 *
 * <p><b>Обе половины пары отдаются наружу, потому что обе нужны для
 * продолжения:</b> момент происшествия задаёт порядок, идентичность
 * события разводит строки одного момента. Отдав одну, поверхность
 * заставила бы читателя догадываться о второй.
 */
@Getter
@Builder
public class JournalCursorApiResponse {

    @Schema(description = "Момент происшествия последней отданной строки")
    private final OffsetDateTime occurredAt;

    @Schema(description = "Идентичность последней отданной строки")
    private final String eventId;
}
