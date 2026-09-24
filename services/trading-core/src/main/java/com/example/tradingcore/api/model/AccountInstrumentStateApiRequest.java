package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Назначение торговых настроек счёта на инструменте
 * (docs/rules/trading-constraints.md: плечо — ручная статичная настройка
 * счёта на инструменте).
 *
 * <p><b>Держатель назначает только плечо.</b> Режим маржи — ограничение
 * контура с единственным допустимым значением, а ступень пары двигают служба
 * холдов и ручная поверхность остановки; тропы записи к ним у этой точки нет.
 *
 * <p><b>Пустое плечо — «не назначено», а не ноль и не умолчание:</b>
 * risk-creating действие по паре тогда отвергается
 * ({@code LEVERAGE_NOT_CONFIGURED}). Назначение — снимок целиком, как у чисел
 * риск-аппетита: непереданное поле стирает прежнее значение.
 *
 * <p><b>Область определения проверяется, величина — нет.</b> Верхний предел
 * плеча — биржевой максимум инструмента, и сверяет его преконтроль на каждом
 * акте ({@code EXCHANGE_MAX_LEVERAGE_EXCEEDED}): максимум меняет синк правил, и
 * проверка здесь устарела бы с первым же его тиком.
 */
@Getter
@Setter
public class AccountInstrumentStateApiRequest {

    @Positive(message = "Плечо положительно: непозитивное плечо не читается ничем")
    @Schema(description = "Рабочее плечо счёта на инструменте. Пусто — плечо не назначено,"
            + " и risk-creating действие по паре отвергается")
    private Integer leverage;
}
