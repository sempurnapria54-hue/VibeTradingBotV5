package com.example.tradingbot.domain.util;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

/**
 * Закрытая форма убытка на уровне остановки убытка — дом в коде
 * (docs/spec/risk-at-stop.json).
 *
 * <p><b>Почему форма вынесена, а не написана у каждого потребителя.</b>
 * Её считают три разных места: сайзинг движка стратегий, четвёрка чисел
 * риска ядра и преконтроль риска. Спека объявляет форму «здесь и только
 * здесь» и называет, чем кончилось её размножение: та же формула жила в
 * четырёх спеках под четырьмя именами и разошлась по знаку, по клэмпу и
 * по якорю — три разных числа под одним смыслом.
 *
 * <p><b>Экспозицию и клэмп форма не знает.</b> Потребитель домножает
 * результат на свою экспозицию (контракт сайзинга, нетто-размер эпизода,
 * налив ноги) и при надобности клэмпует нулём: клэмп — свойство
 * слагаемого, а не формы.
 */
@UtilityClass
public class RiskMath {

    /**
     * Дистанция до уровня со знаком направления: положительна на
     * убыточной стороне, отрицательна за безубытком.
     *
     * <p>Знаковая форма, а не модуль: модуль выдаёт за риск то, что риском
     * не является, и делает перенос уровня за безубыток неотличимым от его
     * постановки под входом.
     */
    public static BigDecimal signedStopDistance(StrategyTradeDirection direction, BigDecimal entryAnchor,
                                                BigDecimal stopPrice) {
        return StrategyTradeDirection.LONG.equals(direction)
                ? entryAnchor.subtract(stopPrice)
                : stopPrice.subtract(entryAnchor);
    }

    /**
     * Round-trip комиссия в ценовых единицах: комиссия входа по якорю плюс
     * комиссия выхода по цене стопа. Численного буфера сверх комиссии
     * здесь нет — буфер есть величина риск-аппетита, и назначает её
     * держатель (docs/rules/risk-policy.md).
     */
    public static BigDecimal stopDistanceFloor(BigDecimal entryAnchor, BigDecimal stopPrice, BigDecimal feeRate) {
        return feeRate.multiply(entryAnchor.add(stopPrice));
    }

    /**
     * Убыток на стопе на единицу экспозиции: знаковая ценовая дистанция от
     * якоря плюс комиссии обеих ног. На точном безубытке равен нулю.
     */
    public static BigDecimal lossAtStopPerUnit(StrategyTradeDirection direction, BigDecimal entryAnchor,
                                               BigDecimal stopPrice, BigDecimal feeRate) {
        return signedStopDistance(direction, entryAnchor, stopPrice)
                .add(stopDistanceFloor(entryAnchor, stopPrice, feeRate));
    }
}
