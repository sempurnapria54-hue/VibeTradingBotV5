package com.example.tradingbot.domain.safety;

import java.util.Objects;
import lombok.Value;

/**
 * Сигнал «поднять safety-ступень scope»: самодостаточный запрос —
 * радиус, ступень и машинный код причины. Его несёт
 * {@link com.example.tradingbot.domain.deal.DealTransition} рядом с уводом
 * своей сделки в ERROR либо собирает сам детектор, обнаруживший основание.
 * Сигнал не закрывает сделку — он адресует инструмент-/биржа-широкую
 * реакцию, которую исполняет {@link HoldService}. RVO.
 *
 * <p><b>Ступень — часть сигнала, а не свойство исполнителя.</b> Прежде
 * severity не носилась вовсе, и реактивный контур был по определению
 * CRITICAL: мягкая ступень выразима не была, а объявленные ею триггеры
 * (docs/rules/instrument-hold.md) поднять реакцию не могли ничем.
 */
@Value
public class HoldSignal {

    /** Уровень холда: инструмент (L3) или биржа (L4). */
    HoldScope scope;

    /** Ступень реакции — судьба принятого риска. */
    HoldRung rung;

    /** Машинно-читаемый код причины (попадает в AnomalyReport.code). */
    String code;

    /** Жёсткий холд инструмента (L3) с кодом причины. */
    public static HoldSignal instrument(String code) {
        return new HoldSignal(HoldScope.INSTRUMENT, HoldRung.HARD, code);
    }

    /** Мягкая ступень инструмента (L3): запрет новых входов без kill-switch. */
    public static HoldSignal instrumentSoft(String code) {
        return new HoldSignal(HoldScope.INSTRUMENT, HoldRung.SOFT, code);
    }

    /** Жёсткий холд биржи (L4) с кодом причины. */
    public static HoldSignal exchange(String code) {
        return new HoldSignal(HoldScope.EXCHANGE, HoldRung.HARD, code);
    }

    /**
     * Мягкая ступень биржи (L4, ступень 1): биржа выпадает из выборки
     * входа, живые сделки ведутся полностью. Командного блок-сета у неё
     * нет — этим она отличается от мягкой ступени инструмента, и разводит
     * составы лестница, а не сигнал (docs/rules/exchange-hold.md).
     */
    public static HoldSignal exchangeSoft(String code) {
        return new HoldSignal(HoldScope.EXCHANGE, HoldRung.SOFT, code);
    }

    /**
     * Журнальный сигнал биржевого радиуса (NON_CRITICAL): блокировки в
     * составе реакции нет — сигнал описывает scope/severity/code
     * журнального отчёта и через исполнителя блокировки не идёт.
     *
     * <p>Кортеж тот же, что у {@link #exchangeSoft}, а имя другое
     * НАМЕРЕННО — ровно по той же причине, по какой разведены
     * {@link #instrumentSoft} и {@link #instrumentJournal}: с тех пор как
     * мягкая биржевая ступень исполняется, вызов не той фабрики заводит
     * запрет входов по всей бирже там, где реакции нет вовсе. Прежде обе
     * ветви были неотличимы по последствиям, потому что не исполнялась ни
     * одна.
     */
    public static HoldSignal exchangeJournal(String code) {
        return new HoldSignal(HoldScope.EXCHANGE, HoldRung.SOFT, code);
    }

    /**
     * Журнальный сигнал радиуса инструмента (NON_CRITICAL). Кортеж тот же,
     * что у {@link #instrumentSoft}, а имя другое НАМЕРЕННО: журнальный
     * сигнал идёт прямо в отчёт, минуя исполнителя блокировки, и вызов
     * фабрики «мягкой ступени» на такой тропе приглашал бы завести
     * блокировку входов там, где реакции нет вовсе.
     */
    public static HoldSignal instrumentJournal(String code) {
        return new HoldSignal(HoldScope.INSTRUMENT, HoldRung.SOFT, code);
    }

    /** Реакция снимает принятый риск: kill-switch в её составе есть. */
    public Boolean tearsDownRisk() {
        return Objects.equals(HoldRung.HARD, rung);
    }
}
