package com.example.auditstatistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Страница журнальной выборки: строки, позиция продолжения и объявленная
 * полнота (docs/models/domain/other/AuditRecord.md §«Как журнал
 * читается»).
 *
 * <p><b>Полнота лежит В ТОМ ЖЕ ответе, а не в соседней точке чтения.</b>
 * Отдельная точка позволяла бы показать строки, её не спросив, — то есть
 * ровно то состояние, против которого обязательство и заведено.
 */
@Getter
@Builder
public class JournalPageApiResponse {

    @Schema(description = "Строки окна — от новых к старым")
    private final List<AuditRecordApiResponse> records;

    @Schema(description = "Позиция для следующей страницы; пусто — окно дочитано до конца")
    private final JournalCursorApiResponse nextCursor;

    @Schema(description = "Нижняя граница полноты и предикат непрерывности — достоверность отданных строк")
    private final JournalCompletenessApiResponse completeness;
}
