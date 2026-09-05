package com.example.marketdata.api.model;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Окно чтения истории свечей инструмента.
 *
 * <p><b>Объектом, а не тремя параметрами:</b> при более чем двух
 * параметрах запроса контракт объявляется {@code @ParameterObject}
 * (.claude/rules/codestyle.md §«Контроллеры / API»).
 *
 * <p><b>У окна есть ПОТОЛОК, и он не декоративный.</b> Предел страницы
 * приезжает от вызывающего и уходит прямо в {@code Pageable}: без
 * верхней границы один запрос с большим числом вытягивал бы весь ряд
 * группы в память — то самое безлимитное чтение истории, которое
 * запрещено (.claude/rules/codestyle.md §«Выборка данных»). Глубже
 * потолка история читается страницами, сдвигая {@code fromMillis}.
 */
@Getter
@Setter
public class CandleHistoryApiQuery {

    /** Потолок одной страницы истории: столько свечей помещается в один ответ и одну выборку. */
    private static final int MAX_LIMIT = 5000;

    @NotNull
    @Schema(description = "Таймфрейм серии, историю которой читаем")
    private TimeFrame timeframe;

    @NotNull
    @Schema(description = "Нижняя граница окна, миллисекунды эпохи: свечи берутся от неё по возрастанию")
    private Long fromMillis;

    @NotNull
    @Positive
    @Max(MAX_LIMIT)
    @Schema(description = "Предел числа свечей в ответе; безлимитного чтения истории нет")
    private Integer limit;
}
