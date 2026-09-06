package com.example.tradingcore.domain.safety;

import java.util.Objects;
import lombok.Value;

/**
 * Самодостаточный запрос «поднять ступень радиуса»: радиус, ступень и
 * машинный код причины.
 *
 * <p><b>Сигнал не закрывает сделку</b> — он адресует инструмент- либо
 * биржа-широкую реакцию, которую исполняет служба ступеней. Исполнитель
 * ступень ЗАТРЕБУЕТ, а поднимает её проход — так же, как переход сделки:
 * исход прохода есть намерение, а не право
 * (docs/processes/fsm-execution-layering.md).
 *
 * <p><b>Ступень — часть сигнала, а не свойство исполнителя.</b> Иначе
 * реактивный контур был бы по определению жёстким, и мягкая ступень,
 * объявленная своими триггерами, поднять реакцию не могла бы ничем.
 *
 * <p><b>Журнальные фабрики повторяют кортеж мягких, и имя у них другое
 * НАМЕРЕННО.</b> Журнальный сигнал уходит прямо в отчёт, минуя службу
 * ступеней, и запрета входов за собой не ведёт; имя — единственное, чем
 * два назначения одного кортежа различаются в коде
 * (docs/components/models/HoldSignal.md §Фабрики). Вызов не той фабрики
 * завёл бы запрет входов там, где реакции нет вовсе.
 */
@Value
public class HoldSignal {

    /** Радиус: пара «счёт, инструмент» либо весь биржевой счёт. */
    HoldScope scope;

    /** Ступень реакции — судьба принятого риска. */
    HoldRung rung;

    /** Машинно-читаемый код причины. */
    String code;

    /** Жёсткая ступень инструмента. */
    public static HoldSignal instrument(String code) {
        return new HoldSignal(HoldScope.INSTRUMENT, HoldRung.HARD, code);
    }

    /** Мягкая ступень инструмента: запрет новых входов без снятия принятого риска. */
    public static HoldSignal instrumentSoft(String code) {
        return new HoldSignal(HoldScope.INSTRUMENT, HoldRung.SOFT, code);
    }

    /** Жёсткая ступень биржевого счёта. */
    public static HoldSignal exchangeAccount(String code) {
        return new HoldSignal(HoldScope.EXCHANGE_ACCOUNT, HoldRung.HARD, code);
    }

    /**
     * Мягкая ступень биржевого счёта: счёт выпадает из выборки входа,
     * живые сделки ведутся полностью. Командного блок-сета у неё нет —
     * этим она отличается от мягкой ступени инструмента, и разводит
     * составы лестница, а не сигнал (docs/rules/exchange-hold.md).
     */
    public static HoldSignal exchangeAccountSoft(String code) {
        return new HoldSignal(HoldScope.EXCHANGE_ACCOUNT, HoldRung.SOFT, code);
    }

    /**
     * <b>Только отчёт</b> на счётном радиусе: службу ступеней сигнал
     * минует.
     */
    public static HoldSignal exchangeAccountJournal(String code) {
        return new HoldSignal(HoldScope.EXCHANGE_ACCOUNT, HoldRung.SOFT, code);
    }

    /**
     * <b>Только отчёт</b> на радиусе пары «счёт, инструмент»: службу
     * ступеней сигнал минует, запрета входов за собой не ведёт.
     *
     * <p>Вызывающие — терминальные звенья, отчитывающиеся о признаках
     * отбора и о неисчислимом числе
     * (docs/components/MarkDealEmergencyClosedExecutor.md,
     * docs/components/MarkDealClosedExecutor.md).
     */
    public static HoldSignal instrumentJournal(String code) {
        return new HoldSignal(HoldScope.INSTRUMENT, HoldRung.SOFT, code);
    }

    /** Реакция снимает принятый риск. */
    public Boolean tearsDownRisk() {
        return Objects.equals(HoldRung.HARD, rung);
    }
}
