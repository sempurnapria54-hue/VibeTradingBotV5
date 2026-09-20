package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Объявление индикатора в стратегии: { key, type, params }. Собственная
 * реляционная строка strategy-scope (таблица strategy_indicator_settings,
 * UNIQUE(strategy_id, key)); адресуется по {@code key} (операнд условия
 * {@code indicatorKey}, «мягкие» ссылки JSON-листьев, ER/ATR-входы
 * структуры). Результат расчёта {@code IndicatorValue} на неё НЕ
 * ссылается: он ключуется идентичностью вычисления
 * (docs/models/domain/other/IndicatorValue.md). Дискриминатор строки —
 * {@code indicatorType} (колонка-владелец строки); сам params — JSONB на
 * строке без дублирования тега. Доменный таймфрейм и warmup живут внутри
 * params. См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyIndicatorSetting), docs/rules/persistence-representation.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyIndicatorSetting {

    /** Технический ID настройки (strategy-scope-строка; цель FK результата расчёта). */
    private Long id;

    /** Стабильный ключ настройки — по нему ссылается индикаторный операнд условия. */
    private String key;

    /** Тип индикатора; дискриминатор подтипа params (колонка-владелец строки). */
    private IndicatorValue.Type indicatorType;

    /**
     * Параметры расчёта (таймфрейм, warmup-override, математические
     * параметры по типу).
     *
     * <p><b>Подтип восстанавливается по {@code indicatorType} ЭТОЙ
     * строки, и на проводе — тоже.</b> В персистентности дискриминатор
     * живёт колонкой-владельцем, и тег внутри JSONB не дублируется
     * (docs/rules/persistence-representation.md §«Полиморфный JSONB»); на
     * проводе колонки нет, а одноимённое поле есть — и внешний тег
     * читает его, а не заводит второй источник
     * (.claude/rules/carrier-levels.md).
     *
     * <p><b>Без этого снимок определения на проводе не разбирается
     * вовсе:</b> {@code IndicatorParams} абстрактен, и читатель
     * ({@code trading-core}, {@code bff}) падает на первом же объявлении
     * индикатора — то есть на всяком настоящем определении
     * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
     * сериализацию»).
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "indicatorType", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AtrParams.class, name = "ATR"),
            @JsonSubTypes.Type(value = EmaParams.class, name = "EMA"),
            @JsonSubTypes.Type(value = RsiParams.class, name = "RSI"),
            @JsonSubTypes.Type(value = MacdParams.class, name = "MACD"),
            @JsonSubTypes.Type(value = StochasticParams.class, name = "STOCHASTIC"),
            @JsonSubTypes.Type(value = BollingerBandsParams.class, name = "BOLLINGER_BANDS"),
            @JsonSubTypes.Type(value = ObvParams.class, name = "OBV"),
            @JsonSubTypes.Type(value = EfficiencyRatioParams.class, name = "EFFICIENCY_RATIO")})
    private IndicatorParams params;

    /** Назначение результата расчёта внутри стратегии. */
    private Destiny destiny;


    /**
     * Идентичность вычисления у {@code market-data}, которой это
     * объявление адресуется в чтениях значений.
     *
     * <p><b>Пишет её ПОТРЕБИТЕЛЬ, а не автор стратегии.</b> Идентичность
     * есть функция содержания объявления (тип, таймфрейм, канонические
     * параметры), и владелец данных выдаёт её на идемпотентное требование
     * (docs/architecture/market-data-collection.md §«Как потребность
     * доходит до сбора»). Автор её не назначает и назначить не может:
     * реестр идентичностей живёт у владельца
     * (docs/models/domain/other/IndicatorValue.md §«Ключевание —
     * идентичностью вычисления»).
     *
     * <p>Пусто — потребность ещё не объявлена: значения по этому
     * объявлению не читаются, а не читаются «как попало». Форма
     * двухписательной модели здесь та же, что у биржевого счёта:
     * реестровую часть пишет один сервис, свою — другой.
     */
    private String computationConfigInternalId;

    /** Срок свежести рассчитанного значения (MarketDataExpirationChecker). */
    private Duration expirationDuration;
}
