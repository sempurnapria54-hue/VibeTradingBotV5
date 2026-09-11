package com.example.tradingcore.domain.safety;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Радиус реактивной ступени: на каком уровне ставится ограничение
 * торговли. Уровни внутренней градации
 * (docs/rules/error-handling-policy.md): пара «счёт, инструмент» —
 * уровень 3 (docs/rules/instrument-hold.md), биржевой счёт — уровень 4
 * (docs/rules/exchange-hold.md).
 *
 * <p><b>Радиусы читаются ОТ СЧЁТА, а не от площадки.</b> Монолит имел
 * ровно одну строку биржи, и она молча служила площадкой, счётом и
 * тенантом; в целевой конструкции корневая торговая строка называет
 * биржевой счёт, и ступень, поднятая на счёте одного тенанта, других не
 * трогает (docs/architecture/tenant-and-exchange.md §«Торговая строка
 * называет счёт, и радиусы читаются от него»).
 *
 * <p><b>Причина остановки — атрибут РАДИУСА, и дом у неё здесь.</b>
 * Читатель у соответствия ОДИН — {@link HardRungShutdownReasonResolver};
 * затребователей ребра энфорсмента по-прежнему два (первый ход,
 * docs/components/SafetyHoldCoordinator.md, и шаг прохода,
 * docs/components/DealOrchestratorJob.md), и оба берут причину у него.
 * Второй читатель разошёлся бы с первым — и расходился
 * (.claude/rules/policy-home.md). Старшинство радиусов домом не является:
 * порядок чтения при обоих стоящих ступенях держит читатель, а его
 * исполнимая форма — docs/spec/hard-rung-shutdown-reason.json, величина
 * {@code hardRungShutdownReason}
 * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
 */
@Getter
@RequiredArgsConstructor
public enum HoldScope {

    /**
     * Ступень пары «счёт, инструмент»: локализованная риск-ошибка. Счёт
     * приходит из сделки, по которой сигнал поднят, — инструмент
     * принадлежит площадке, и отказы одного счёта не описывают другой.
     */
    INSTRUMENT(Deal.ShutdownReason.RISK_POLICY),

    /** Ступень всего биржевого счёта: каскад на все его инструменты. */
    EXCHANGE_ACCOUNT(Deal.ShutdownReason.EXCHANGE_HOLD);

    /**
     * Причина выхода из штатного ведения, которую жёсткая ступень ЭТОГО
     * радиуса присваивает уводимой сделке
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
     */
    private final Deal.ShutdownReason shutdownReason;
}
