package com.example.tradingbot.domain.model.aggregate.strategy;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Политика шага на случай устаревания нужных именно этому шагу данных.
 * Обязательна для каждого шага (default на уровне detail не
 * используется); не определяет, когда данные устарели — это
 * expirationDuration настроек + MarketDataExpirationChecker. Хранится
 * JSONB-полем на строке strategy_step. См.
 * docs/models/domain/aggregate/Strategy.md
 * (§StrategyMarketDataExpiredSetting).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyMarketDataExpiredSetting {

    /** Реакция при защищённой позиции. */
    private MarketDataExpiredAction protectedPositionAction;

    /** Реакция при незащищённой позиции. */
    private MarketDataExpiredAction unprotectedPositionAction;

    /**
     * Реакция, выбранная ветвью покрытия (docs/spec/market-data-freshness.json,
     * величина {@code expiredSettingResolved}).
     *
     * <p><b>Операнды читаются по уровню объекта шага</b>: у потраншевого шага
     * объект — его транш, у шага агрегатной поверхности (EXIT / FAIL_SAFE
     * уровня сделки) транша нет ни одного, и объектом становится сама сделка.
     * Уровень выбирает вызывающая сторона, подставляя соответствующую пару
     * предикатов; сама настройка об уровне не знает — иначе разведение
     * пришлось бы повторять в каждом потребителе.
     */
    public MarketDataExpiredAction resolve(Boolean branchRiskBearing, Boolean branchCovered,
                                           Boolean dealStopUnresolved) {
        return isTrue(isUnprotected(branchRiskBearing, branchCovered, dealStopUnresolved))
                ? unprotectedPositionAction
                : protectedPositionAction;
    }

    /**
     * Ветвь незащищённой позиции (величина {@code protectionBranch}).
     *
     * <p>Незащищённой считается ТОЛЬКО позиция, у которой живой риск есть И
     * он не покрыт: «защищать нечего» и «покрыто» — разные факты, и оба ведут
     * в ветвь защищённой, потому что аварийная реакция на них закрывала бы
     * либо то, чего нет, либо то, что под контролем. Умолчания здесь нет ни в
     * одну сторону — обе пустоты названы.
     *
     * <p>Неразрешимый уровень защиты читается как НЕПОКРЫТЫЙ: риск есть, а
     * чем он ограничен — неизвестно, и благоприятного прочтения у этой пустоты
     * быть не может.
     */
    public Boolean isUnprotected(Boolean branchRiskBearing, Boolean branchCovered,
                                 Boolean dealStopUnresolved) {
        return isTrue(branchRiskBearing)
                && (isFalse(branchCovered) || isTrue(dealStopUnresolved));
    }
}
