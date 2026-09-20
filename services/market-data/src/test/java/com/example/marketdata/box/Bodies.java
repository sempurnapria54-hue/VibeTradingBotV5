package com.example.marketdata.box;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Тела запросов к поверхности ящика.
 *
 * <p><b>Собираются строкой, а не api-моделью сервиса.</b> Взяв его модель,
 * кейс проверял бы сериализацию против неё же и не смог бы подать то,
 * чего модель не выражает: чужой порядок ключей ({@code B1.9}), поля
 * чужого типа ({@code B1.10}), неразбираемое тело ({@code B8.6}).
 */
final class Bodies {

    private Bodies() {
    }

    /** Требование свечей с названной глубиной истории. */
    static String candleRequirement(String instrumentInternalId, String timeframe, Long depthBars) {
        return """
                {
                  "instrumentInternalId": "%s",
                  "timeframe": "%s",
                  "depthBars": %d
                }
                """.formatted(instrumentInternalId, timeframe, depthBars);
    }

    /** Требование свечей без глубины: вся доступная история площадки. */
    static String candleRequirement(String instrumentInternalId, String timeframe) {
        return """
                {
                  "instrumentInternalId": "%s",
                  "timeframe": "%s"
                }
                """.formatted(instrumentInternalId, timeframe);
    }

    /** Требование индикатора с телом параметров как есть. */
    static String indicatorRequirement(String indicatorType, String timeframe, String params) {
        return """
                {
                  "indicatorType": "%s",
                  "timeframe": "%s",
                  "params": %s
                }
                """.formatted(indicatorType, timeframe, params);
    }

    /** Требование структуры рынка с названными идентичностями входов. */
    static String structureRequirement(String timeframe, String params,
                                       String efficiencyRatioConfigInternalId, String atrConfigInternalId) {
        return """
                {
                  "timeframe": "%s",
                  "params": %s,
                  "efficiencyRatioConfigInternalId": %s,
                  "atrConfigInternalId": %s
                }
                """.formatted(timeframe, params,
                quotedOrNull(efficiencyRatioConfigInternalId), quotedOrNull(atrConfigInternalId));
    }

    /** Параметры структуры: окно расчёта и пороги уровней. */
    static String structureParams(Integer lookbackBars) {
        return """
                {
                  "lookbackBars": %d,
                  "minTouches": 2,
                  "minRangeWidthPercents": "0.1",
                  "maxRangeWidthPercents": "50",
                  "breakoutBufferPercents": "5",
                  "breakoutConfirmationBars": 1,
                  "swingLookbackBars": 3,
                  "trendEfficiencyThreshold": "0.5",
                  "levelToleranceAtrMultiplier": "0.5"
                }
                """.formatted(lookbackBars);
    }

    /** Параметры структуры БЕЗ окна расчёта: вход клетки {@code B4.9}. */
    static String structureParamsWithoutWindow() {
        return """
                {
                  "minTouches": 2,
                  "swingLookbackBars": 3
                }
                """;
    }

    /** Привязка операнда к идентичности вычисления со сроком читателя. */
    static String binding(String key, String configInternalId, String tolerance) {
        return """
                {"key": "%s", "configInternalId": "%s", "tolerance": "%s"}
                """.formatted(key, configInternalId, tolerance);
    }

    /** Чтение фич названными привязками. */
    static String featureRead(String indicatorBindings, String structureBindings, Boolean priceRequired) {
        return """
                {
                  "indicatorBindings": %s,
                  "structureBindings": %s,
                  "priceRequired": %b
                }
                """.formatted(indicatorBindings, structureBindings, priceRequired);
    }

    /** Чтение фич с клаузами классификации фазы. */
    static String featureReadWithPhase(String indicatorBindings, String phaseRules) {
        return """
                {
                  "indicatorBindings": %s,
                  "phaseRules": %s
                }
                """.formatted(indicatorBindings, phaseRules);
    }

    /**
     * Клауза фазы: тип фазы и условие, сравнивающее операнд с литералом.
     *
     * <p>Операнд назван АВТОРСКИМ именем привязки, а не идентичностью
     * вычисления: клаузы и привязки приносит потребитель, и связывает их
     * он же (docs/components/MarketPhaseService.md).
     */
    static String phaseRule(String phaseType, String indicatorKey, String operator, String value) {
        return """
                {
                  "type": "%s",
                  "condition": {
                    "rules": [
                      {
                        "level": 1,
                        "ruleType": "INDICATOR_COMPARE",
                        "operator": "%s",
                        "leftOperand": {"sourceType": "INDICATOR", "indicatorKey": "%s"},
                        "rightOperand": {"sourceType": "CONSTANT", "valueType": "NUMBER", "value": "%s"}
                      }
                    ]
                  }
                }
                """.formatted(phaseType, operator, indicatorKey, value);
    }

    /** Клауза фазы, читающая ЦЕНУ: ею наблюдается чтение цены по клаузе. */
    static String pricePhaseRule(String phaseType, String operator, String value) {
        return """
                {
                  "type": "%s",
                  "condition": {
                    "rules": [
                      {
                        "level": 1,
                        "ruleType": "PRICE_COMPARE",
                        "operator": "%s",
                        "leftOperand": {"sourceType": "PRICE", "priceSource": "LAST_PRICE"},
                        "rightOperand": {"sourceType": "CONSTANT", "valueType": "NUMBER", "value": "%s"}
                      }
                    ]
                  }
                }
                """.formatted(phaseType, operator, value);
    }

    /** Перечень тел одним телом. */
    static String array(String... items) {
        return Arrays.stream(items).collect(Collectors.joining(",", "[", "]"));
    }

    private static String quotedOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
