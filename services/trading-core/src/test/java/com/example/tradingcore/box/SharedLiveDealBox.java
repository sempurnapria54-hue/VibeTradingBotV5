package com.example.tradingcore.box;

import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Штатное положение осей конфигурации у групп, чьи клетки стоят на ЖИВОЙ
 * сделке ({@link LiveDealBox}): наследники делят ОДИН контекст.
 *
 * <p><b>Контекст свой, а не общий с {@link SharedTradingCoreBox}, и
 * причина — наследование, а не оси.</b> Сборка живой сделки нужна и
 * классам со своим положением осей (строгий режим сверки, иные числа
 * допуска), поэтому она живёт ниже источника свойств; метод
 * {@code @DynamicPropertySource} у этой ветви свой, и ключ кэша у неё
 * другой.
 *
 * <p><b>Раз контекст свой — своя и группа потребителя, и тема владельца
 * определений</b> (довод — шапка {@link TradingCoreSubstrate}): два живых
 * контекста с одним именем группы делили бы партии темы, и клетка
 * соседнего класса упиралась бы в таймаут ожидания копии.
 */
abstract class SharedLiveDealBox extends LiveDealBox {

    /** Имя ветви: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-live-deal";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of());
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }
}
