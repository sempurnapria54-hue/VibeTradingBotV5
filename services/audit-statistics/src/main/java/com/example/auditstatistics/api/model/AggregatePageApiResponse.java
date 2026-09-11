package com.example.auditstatistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Страница агрегатной выборки: строки выбранного зерна, позиция
 * продолжения и объявленная полнота журнала
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Перечня два, а заполнен ровно один — по названному зерну.</b>
 * Оболочка выбрана владельцем предмета; состав входа и выхода выбором не
 * является — он назван домом
 * (.claude/work/roadmap/phase-2.md §«Read-only API» шага 10 — это контур,
 * ВЫБОРКА на предмет и ограничения отбора; отложен состав ЭКРАННЫХ
 * операций). Общая форма строки на два зерна склеила бы непересекающиеся
 * наборы величин и заставила бы читателя различать зёрна по пустотам.
 *
 * <p><b>ПУСТОЙ перечень и ОТСУТСТВУЮЩИЙ различаются:</b> пустой означает
 * «зерно выбрано, строк в окне нет», отсутствующий — «спрошено не это
 * зерно» (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Полнота лежит В ТОМ ЖЕ ответе, а не в соседней точке чтения:</b>
 * обязательство объявлено владельцем журнала и распространено на всякую
 * выдачу чисел этого сервиса, включая строки агрегатов
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 */
@Getter
@Builder
public class AggregatePageApiResponse {

    @Schema(description = "Зерно, о котором спрошено: им же выбран заполненный перечень строк")
    private final String grain;

    @Schema(description = "Строки сделочного зерна — от новых суток к старым; "
            + "отсутствует, когда спрошено зерно происшествий")
    private final List<DealAggregateApiResponse> dealRows;

    @Schema(description = "Строки зерна происшествий — от новых суток к старым; "
            + "отсутствует, когда спрошено сделочное зерно")
    private final List<IncidentAggregateApiResponse> incidentRows;

    @Schema(description = "Позиция для следующей страницы; пусто — окно дочитано до конца")
    private final AggregateCursorApiResponse nextCursor;

    @Schema(description = "Нижняя граница полноты журнала и предикат непрерывности — "
            + "достоверность отданных чисел")
    private final JournalCompletenessApiResponse completeness;
}
