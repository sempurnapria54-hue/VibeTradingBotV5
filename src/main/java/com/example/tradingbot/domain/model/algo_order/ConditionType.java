package com.example.tradingbot.domain.model.algo_order;

public enum ConditionType {

    /**
     * STOP_LOSS — одиночный условный Stop-Loss (SL).
     * <p>
     * Доменный смысл:
     * - Закрыть позицию (или её часть) при достижении цены SL.
     * - Используется как базовая защита позиции.
     * <p>
     * Внутренние поля:
     * - Trigger.stopLoss (type/value)
     * - closeFraction (обычно 1)
     * <p>
     * Биржевое отображение (OKX):
     * - Обычно ordType=conditional
     * - slTriggerPx = Trigger.stopLoss.value
     * - slTriggerPxType = Trigger.stopLoss.type (MARK/LAST/INDEX)
     * - slOrdPx = -1 (market) — если стратегия не требует limit исполнения
     * <p>
     * Типовые кейсы:
     * - Первичная защита сразу при открытии позиции
     * - Перевод SL в безубыток (через amend/cancel+create)
     * - Трейлинг “вручную” (пересоздавая SL на новых уровнях)
     */
    STOP_LOSS,

    /**
     * TAKE_PROFIT — одиночный условный Take-Profit (TP).
     * <p>
     * Доменный смысл:
     * - Закрыть позицию (или её часть) при достижении цены TP.
     * <p>
     * Внутренние поля:
     * - Trigger.takeProfit (type/value)
     * - closeFraction (обычно 1 или частичное значение)
     * <p>
     * Биржевое отображение (OKX):
     * - Обычно ordType=conditional
     * - tpTriggerPx = Trigger.takeProfit.value
     * - tpTriggerPxType = Trigger.takeProfit.type
     * - tpOrdPx = -1 (market)
     * <p>
     * Типовые кейсы:
     * - Фиксация прибыли на заранее определённом уровне
     * - Один из уровней частичного выхода (см. PARTIAL_TAKE_PROFIT)
     */
    TAKE_PROFIT,

    /**
     * OCO_FULL — OCO связка (TP + SL), “один сработал — второй отменился”.
     * <p>
     * Доменный смысл:
     * - Два условия (TP и SL) связаны в единый сценарий выхода.
     * - Если сработал TP — SL должен быть автоматически снят (и наоборот).
     * <p>
     * Внутренние поля:
     * - Trigger.stopLoss и Trigger.takeProfit (оба обязательны)
     * - closeFraction (обычно 1)
     * <p>
     * Биржевое отображение (OKX):
     * - ordType=oco
     * - TP/SL триггеры и их типы передаются в одном algo запросе
     * <p>
     * Типовые кейсы:
     * - Классическая “фиксированная цель + фиксированный риск” без трейлинга
     */
    OCO_FULL,

    /**
     * TRAILING_PERCENTS — trailing stop по проценту отката (callbackRatio).
     * <p>
     * Доменный смысл:
     * - После активации (или сразу) отслеживать экстремум.
     * - Закрыть позицию при откате на N% от экстремума.
     * <p>
     * Внутренние поля:
     * - Trailing.trailingPercents (обязателен)
     * - Trailing.activationPrice (опционален)
     * - closeFraction (обычно 1)
     * <p>
     * Биржевое отображение (OKX):
     * - ordType=move_order_stop
     * - callbackRatio = trailingPercents
     * - activePx = activationPrice (если задан)
     * <p>
     * Типовые кейсы:
     * - Трендовая торговля “дать прибыли течь”, выйти при откате
     */
    TRAILING_PERCENTS,

    /**
     * TRAILING_VALUE — trailing stop по абсолютному шагу отката (callbackSpread).
     * <p>
     * Доменный смысл:
     * - После активации (или сразу) отслеживать экстремум.
     * - Закрыть позицию при откате на фиксированное значение от экстремума.
     * <p>
     * Внутренние поля:
     * - Trailing.trailingStepValue (обязателен)
     * - Trailing.activationPrice (опционален)
     * - closeFraction (обычно 1)
     * <p>
     * Биржевое отображение (OKX):
     * - ordType=move_order_stop
     * - callbackSpread = trailingStepValue
     * <p>
     * Типовые кейсы:
     * - Трейлинг с шагом, удобный в “ценовых единицах” инструмента
     */
    TRAILING_VALUE,

    /**
     * PARTIAL_TAKE_PROFIT — частичный take-profit (один уровень лесенки TP).
     * <p>
     * Доменный смысл:
     * - Это один из уровней лестницы фиксации прибыли.
     * - Отличается от TAKE_PROFIT только семантикой: “я часть набора”.
     * - Сортировка/группировка уровней выполняется на уровне сделки (Deal).
     * <p>
     * Внутренние поля:
     * - Trigger.takeProfit (обязателен)
     * - closeFraction (обычно < 1)
     * <p>
     * Биржевое отображение (OKX):
     * - чаще всего ordType=conditional (market TP)
     * <p>
     * Типовые кейсы:
     * - TP1/TP2/TP3 с разными долями закрытия
     */
    PARTIAL_TAKE_PROFIT,

    /**
     * PARTIAL_STOP_LOSS — частичный stop-loss (один уровень лесенки SL).
     * <p>
     * Доменный смысл:
     * - Это один из уровней лестницы защитных стопов.
     * - Отличается от STOP_LOSS только семантикой: “я часть набора”.
     * - Сортировка/группировка уровней выполняется на уровне сделки (Deal).
     * <p>
     * Внутренние поля:
     * - Trigger.stopLoss (обязателен)
     * - closeFraction (обычно < 1)
     * <p>
     * Биржевое отображение (OKX):
     * - чаще всего ordType=conditional (market SL)
     * <p>
     * Типовые кейсы:
     * - Многоступенчатое сокращение позиции при движении против нас
     */
    PARTIAL_STOP_LOSS
}
