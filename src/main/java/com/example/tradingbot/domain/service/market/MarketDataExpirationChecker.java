package com.example.tradingbot.domain.service.market;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Runtime-проверка свежести рыночных данных. Состояние в БД не хранит и
 * Strategy.Status не меняет: свежесть вычисляется на чтение
 * {@code expiredAt = referencePoint + askingSetting.expirationDuration},
 * свежо ⟺ {@code now < expiredAt}. referencePoint — windowEndAt
 * (структура) / candleTimestamp (фаза, индикатор); confirmedAt — гейт без
 * look-ahead, не точка отсчёта. На общей строке шаримого результата
 * единого expiredAt нет — свежесть оценивается под каждую запрашивающую
 * настройку. Агрегирующие checkForEntry/checkForStep (по Strategy/
 * DealContext) приходят с кластером сделки. См.
 * docs/components/MarketDataExpirationChecker.md,
 * docs/rules/market-data-freshness.md.
 */
@Service
public class MarketDataExpirationChecker {

    /** Свежо ли значение с точкой отсчёта referencePoint под срок expirationDuration (на момент now UTC). */
    public Boolean isFresh(OffsetDateTime referencePoint, Duration expirationDuration) {
        if (isNull(referencePoint) || isNull(expirationDuration)) {
            return false;
        }
        OffsetDateTime expiredAt = referencePoint.plus(expirationDuration);
        return OffsetDateTime.now(ZoneOffset.UTC).isBefore(expiredAt);
    }

    /**
     * Свежи ли данные, нужные ИМЕННО ЭТОМУ шагу: каждая настройка, на которую
     * ссылается его условие, отдала значение в контекст оценки.
     *
     * <p><b>Отсутствие и устаревание здесь неразличимы — и различать их не
     * нужно:</b> обе пустоты означают «данным доверять нельзя» и ведут к одной
     * реакции (docs/spec/market-data-freshness.json, величина {@code reaction}).
     * Устаревшее значение до контекста не доходит — свежесть под срок
     * настройки-владельца гейтят раздатчики ({@code IndicatorService},
     * {@code MarketStructureService}), поэтому отсутствие ключа в контексте и
     * есть признак «не свежо».
     *
     * <p>Область — INDICATOR и MARKET_STRUCTURE: срок свежести объявляют
     * только их настройки (§«Источник сроков»). Цена и константы операндами
     * свежести не являются, предыдущее значение индикатора свежестью не
     * гейтится (направление, не точка решения).
     */
    public Boolean stepDataFresh(StrategyStep step, ConditionEvaluationContext context) {
        if (isNull(step) || isNull(context)) {
            return true;
        }
        return operandsOf(step.getCondition()).allMatch(operand -> operandResolved(operand, context));
    }

    private Stream<StrategyConditionOperand> operandsOf(StrategyCondition condition) {
        if (isNull(condition)) {
            return Stream.empty();
        }
        return emptyIfNull(condition.getRules()).stream()
                .flatMap(MarketDataExpirationChecker::sidesOf)
                .filter(operand -> nonNull(operand));
    }

    private static Stream<StrategyConditionOperand> sidesOf(StrategyConditionRule rule) {
        return Stream.of(rule.getLeftOperand(), rule.getRightOperand());
    }

    /** Операнд, ссылающийся на настройку со сроком свежести, отдал значение. */
    private Boolean operandResolved(StrategyConditionOperand operand, ConditionEvaluationContext context) {
        if (StrategyConditionSourceType.INDICATOR.equals(operand.getSourceType())) {
            return present(context.getLatestIndicators(), operand.getIndicatorKey());
        }
        if (StrategyConditionSourceType.MARKET_STRUCTURE.equals(operand.getSourceType())) {
            return present(context.getStructures(), operand.getStructureKey());
        }
        return true;
    }

    /** Ключ настройки резолвится в значение контекста; безымянный операнд свежести не гейтит. */
    private Boolean present(Map<String, ?> values, String key) {
        if (isNotBlank(key)) {
            return nonNull(values) && nonNull(values.get(key));
        }
        return true;
    }
}
