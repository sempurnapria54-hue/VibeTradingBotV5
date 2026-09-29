package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Окно чтения действующего листинга каталога.
 *
 * <p><b>Окно ключевое, а не страничное:</b> курсор — {@code internalId}
 * последнего инструмента предыдущего окна, порядок — по нему же. Номер
 * страницы сдвигал бы окна, пока читатель их обходит: инструмент,
 * ушедший из действующих статусов, переносил бы соседа в уже
 * прочитанное окно, и тот пропускался бы молча. Числовой ключ базы
 * курсором не годится — наружу он не выходит
 * (.claude/rules/codestyle.md §«Идентичность наружу»).
 *
 * <p><b>У окна есть потолок</b>, как у истории свечей: без верхней
 * границы один запрос с большим пределом вернул бы листинг целиком — то
 * самое безлимитное чтение, которое запрещено
 * (.claude/rules/codestyle.md §«Выборка данных»). Окно короче предела —
 * последнее.
 */
@Getter
@Setter
public class InstrumentListingApiQuery {

    /** Окно, которое получает вызывающий, не назвавший предела. */
    private static final int DEFAULT_LIMIT = 500;

    /** Потолок одного окна: столько инструментов помещается в один ответ и одну выборку. */
    private static final int MAX_LIMIT = 1000;

    @Schema(description = "Курсор: internalId последнего инструмента предыдущего окна; пусто — окно от начала листинга")
    private String after;

    @NotNull
    @Positive
    @Max(MAX_LIMIT)
    @Schema(description = "Предел числа инструментов в окне; не предъявлен — окно умолчания поверхности")
    private Integer limit = DEFAULT_LIMIT;
}
