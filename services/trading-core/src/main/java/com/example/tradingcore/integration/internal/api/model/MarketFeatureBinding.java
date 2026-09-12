package com.example.tradingcore.integration.internal.api.model;

import java.time.Duration;
import lombok.Builder;
import lombok.Getter;

/**
 * Привязка авторского имени операнда к идентичности вычисления и к сроку
 * свежести, под который значение годно этому читателю.
 *
 * <p>Обе половины принадлежат нам: имя — из объявления стратегии,
 * толерантность — её же настройка (docs/rules/market-data-freshness.md).
 * Владелец данных ни того, ни другого не выдумывает.
 */
@Getter
@Builder
public class MarketFeatureBinding {

    /** Авторское имя операнда, которым его называют условия. */
    private final String key;

    /** Идентичность вычисления, выданная владельцем на объявление потребности. */
    private final String configInternalId;

    /** Срок свежести: значение старше него в ответ не попадает. */
    private final Duration tolerance;
}
