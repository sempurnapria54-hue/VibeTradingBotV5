package com.example.tradingbot.domain.util;

import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

/**
 * Дом доменных констант общего артефакта: один класс, темы —
 * вложенными классами (.claude/rules/codestyle.md §Константы).
 *
 * <p>Сюда попадает только то, что читают <b>несколько</b> сервисов и что
 * не является ни числом риск-аппетита (те живут на строке тенанта —
 * docs/rules/risk-policy.md), ни специфи́кой площадки (её общий артефакт
 * не несёт по построению — docs/architecture/services.md).
 */
@UtilityClass
public class Constants {

    /** Константы риск-правил, общие для владельца определений и ядра. */
    @UtilityClass
    public class Risk {

        /**
         * Доля катастрофического потолка, которая обязана остаться
         * свободной после объявленного нотинала. КОНСТАНТА ПРАВИЛА, а не
         * число риск-аппетита: выведена из наблюдаемой величины проскока,
         * поля конфигурации не имеет и отказа при незаданности не даёт
         * (docs/rules/risk-policy.md §«Нотинал укладывается в потолок с
         * запасом, а не в границу», docs/spec/strategy-reference.json,
         * операнд {@code notionalHeadroomShare}).
         */
        public static final BigDecimal NOTIONAL_HEADROOM_SHARE = new BigDecimal("0.01");

        /** Полное покрытие защитой в процентах: сумма долей защитного набора шага. */
        public static final BigDecimal FULL_COVERAGE_PERCENTS = new BigDecimal("100");
    }
}
