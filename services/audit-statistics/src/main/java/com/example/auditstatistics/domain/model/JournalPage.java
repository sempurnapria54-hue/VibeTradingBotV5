package com.example.auditstatistics.domain.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Страница журнальной выборки: строки, позиция продолжения и то, что
 * журнал объявляет о своей полноте
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 *
 * <p><b>Полнота едет ВМЕСТЕ со строками, а не отдельным запросом.</b>
 * Число, показанное без своей достоверности, читается как «всё в порядке»
 * (docs/concept.md, П1); отдельная точка чтения полноты позволяла бы
 * показать строки, её не спросив.
 */
@Value
@Builder
public class JournalPage {

    /** Строки окна — от новых к старым. */
    List<AuditRecord> records;

    /**
     * Позиция, с которой читается следующая страница; пусто — окно
     * дочитано до конца.
     */
    JournalCursor nextCursor;

    /** Нижняя граница полноты и предикат непрерывности. */
    JournalCompleteness completeness;
}
