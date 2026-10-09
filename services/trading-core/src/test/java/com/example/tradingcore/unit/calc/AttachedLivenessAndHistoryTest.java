package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.AttachedFacts.facts;
import static com.example.tradingcore.unit.calc.AttachedFacts.observed;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import com.example.tradingcore.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingcore.domain.command.resolve.AttachedProtectionFacts;
import com.example.tradingcore.domain.command.resolve.AttachedProtectionResolution;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Встроенная защита: живость, вторая ступень и разбор истории — группа
 * {@code U11} документа `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/spec/order-lifecycle.json §{@code attachedBecomesActive},
 * §{@code searchExhaustedOutcome}, §{@code attachedHistoryStatus},
 * §{@code attachedHistoryCloseReason}; docs/lifecycles/Order.md §«Исход
 * ненайденности — вторая ступень», §«Пустой разбор истории»; звенья
 * Z26-Z28).
 *
 * <p><b>Живость — по факту МАТЕРИАЛИЗАЦИИ:</b> доказательство одно —
 * предъявленная самостоятельная запись. Налив родителя его не заменяет:
 * площадка ставит встроенную защиту только на терминале родителя с наливом
 * (.claude/decisions/attached-protection-deferred-coverage.md). Присутствие
 * элемента в теле родителя живости не доказывает — он стои́т там и у
 * живого без налива, и у отменённого.
 *
 * <p><b>Вторая ступень собирается на терминале, наблюдённом раньше</b>, —
 * кроме клеток первого наблюдения: там пустой разбор даёт ожидание
 * (.claude/decisions/protection-lost-needs-prior-terminal.md). Клетки
 * первого наблюдения меток не несут — их назначает документ кейсов.
 *
 * <p><b>Базовая сборка:</b> та же, что у {@code U10}; кода отказа
 * постановки нет ни в одном кейсе группы.
 */
class AttachedLivenessAndHistoryTest {

    private final AttachedAlgoOrderStateResolver resolver = new AttachedAlgoOrderStateResolver();

    private static BigDecimal fill(String value) {
        return CalcFixture.decimal(value);
    }

    /** Терминальный родитель с наливом, терминал наблюдён раньше — сборка второй ступени. */
    private static AttachedProtectionFacts.AttachedProtectionFactsBuilder afterSearchCycle() {
        return facts()
                .observed(observed())
                .parentStatus(Order.Status.COMPLETED)
                .parentAccumulatedFillSize(fill("1"))
                .parentTerminalObservedBefore(true);
    }

    /** Та же сборка на добыче, впервые показавшей терминал родителя. */
    private static AttachedProtectionFacts.AttachedProtectionFactsBuilder firstTerminalObservation() {
        return afterSearchCycle()
                .parentTerminalObservedBefore(false);
    }

    @Test
    @DisplayName("U11.1 — родитель жив, защита не предъявлена: принята биржей, но не материализована")
    void u11_1_anUnobservedProtectionUnderALiveParentIsPending() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .parentStatus(Order.Status.ACTIVE)
                .parentAccumulatedFillSize(fill("1"))
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.PENDING);
        assertThat(resolution.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U11.2 — родитель жив с наливом, защита предъявлена: записи нет — защита в постановке")
    void u11_2_aFilledLiveParentKeepsTheProtectionPending() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ACTIVE)
                .parentAccumulatedFillSize(fill("1"))
                .build());

        assertThat(resolution.getStatus())
                .as("налив живого родителя активации не доказывает: площадка ставит защиту на его терминале")
                .isEqualTo(AttachedAlgoOrder.Status.PENDING);
    }

    @Test
    @DisplayName("U11.3 — пустой налив при найденной самостоятельной записи: запись — своё доказательство")
    void u11_3_aStandaloneRecordProvesMaterialisationOnItsOwn() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ACTIVE)
                .parentAccumulatedFillSize(fill(""))
                .standaloneRecordFound(true)
                .build());

        assertThat(resolution.getStatus())
                .as("недобытость налива самостоятельную запись не гасит")
                .isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
    }

    @Test
    @DisplayName("U11.4 — нулевой налив без самостоятельной записи: присутствие в теле родителя не довод")
    void u11_4_presenceInTheParentBodyDoesNotProveLiveness() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ACTIVE)
                .parentAccumulatedFillSize(fill("0"))
                .build());

        assertThat(resolution.getStatus())
                .as("живость — дизъюнкция двух троп предъявления (Z26)")
                .isEqualTo(AttachedAlgoOrder.Status.PENDING);
    }

    @Test
    @DisplayName("U11.5 — родитель не подтверждён с наливом: до терминала налив исхода не меняет")
    void u11_5_anUnconfirmedParentWithAFillKeepsTheProtectionPending() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.CREATED)
                .parentAccumulatedFillSize(fill("1"))
                .build());

        assertThat(resolution.getStatus())
                .as("живость даёт тот же предикат (Z26): записи нет — постановка")
                .isEqualTo(AttachedAlgoOrder.Status.PENDING);
    }

    @Test
    @DisplayName("U11.6 — нога разбора истории сработавшая: исполнена, причина — сработала")
    void u11_6_anEffectiveHistoryLegGivesTriggered() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .historyLegFound(ProtectionHistoryLeg.EFFECTIVE)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.COMPLETED);
        assertThat(resolution.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.TRIGGERED);
    }

    @Test
    @DisplayName("U11.7 — снятая нога при стоящем намерении снятия: переключена стратегией")
    void u11_7_aCancelledLegWithAStandingIntentIsSwitchedByStrategy() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .historyLegFound(ProtectionHistoryLeg.CANCELED)
                .cancelIntentStanding(true)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(resolution.getCloseReason())
                .isEqualTo(AttachedAlgoOrder.CloseReason.SWITCHED_BY_STRATEGY);
    }

    @Test
    @DisplayName("U11.8 — снятая нога без намерения: причина неизвестна, отмена тупиком не становится")
    void u11_8_aCancelledLegWithoutAnIntentGivesAnUnknownReason() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .historyLegFound(ProtectionHistoryLeg.CANCELED)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(resolution.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.UNKNOWN);
    }

    @Test
    @DisplayName("U11.9 — нога отказа заявки: ошибка, причина — отказ срабатывания защиты")
    void u11_9_anOrderFailedLegGivesATriggerFailure() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .historyLegFound(ProtectionHistoryLeg.ORDER_FAILED)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ERROR);
        assertThat(resolution.getCloseReason())
                .isEqualTo(AttachedAlgoOrder.CloseReason.PROTECTION_TRIGGER_FAILED);
    }

    @Test
    @DisplayName("U11.10 — терминал с недобытым наливом: вторая ступень определена у обоих классов")
    void u11_10_aTerminalParentWithAnUnknownFillAlsoReachesTheSecondStage() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.COMPLETED)
                .parentAccumulatedFillSize(fill(""))
                .historyLegFound(ProtectionHistoryLeg.EFFECTIVE)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.COMPLETED);
        assertThat(resolution.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.TRIGGERED);
    }

    @Test
    @DisplayName("U11.11 — ноги истории нет, самостоятельная запись найдена: живая без причины")
    void u11_11_aStandaloneRecordWithoutAHistoryLegIsActive() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .standaloneRecordFound(true)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(resolution.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U11.12 — живой риск транша без покрытия, разбор истории пуст: потерянное покрытие")
    void u11_12_liveTrancheRiskWithoutCoverageIsLostProtection() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(false)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ERROR);
        assertThat(resolution.getCloseReason())
                .as("ни живой записи, ни записи в истории: защита пропала")
                .isEqualTo(AttachedAlgoOrder.CloseReason.PROTECTION_LOST);
    }

    @Test
    @DisplayName("U11.22 — живой риск транша без покрытия, разбор нашёл срабатывание: стоп сработал, а не пропал")
    void u11_22_aTriggeredLegOutranksTheLostCoverageBranch() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(false)
                .historyLegFound(ProtectionHistoryLeg.EFFECTIVE)
                .build());

        assertThat(resolution.getStatus())
                .as("экспозиция транша стоит налитой до наблюдения срабатывания — сработавшая защита её и закрыла")
                .isEqualTo(AttachedAlgoOrder.Status.COMPLETED);
        assertThat(resolution.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.TRIGGERED);
    }

    @Test
    @DisplayName("U11.19 — живой риск транша без покрытия, но наше намерение снятия стои́т: не потерянное покрытие")
    void u11_19_aStandingCancelIntentClosesTheLostCoverageBranch() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(false)
                .cancelIntentStanding(true)
                .build());

        assertThat(resolution.getOutcomeUndetermined())
                .as("защиту, которую сняли мы, пропавшей не читают: судьбу даёт разбор истории")
                .isTrue();
        assertThat(resolution.getStatus()).isNull();
    }

    @Test
    @DisplayName("U11.20 — та же сборка, разбор нашёл ногу снятой: терминал по ноге, а не потерянное покрытие")
    void u11_20_aStandingCancelIntentTakesTheTerminalFromTheHistoryLeg() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(false)
                .cancelIntentStanding(true)
                .historyLegFound(ProtectionHistoryLeg.CANCELED)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
    }

    @Test
    @DisplayName("U11.21 — пустой разбор при живом риске транша: ветвь потерянного покрытия читает намерение")
    void u11_21_theLostCoverageBranchReadsTheStandingIntent() {
        assertThat(emptyAnalysis(fill("1"), false, false).getCloseReason())
                .isEqualTo(AttachedAlgoOrder.CloseReason.PROTECTION_LOST);
        assertThat(emptyAnalysis(fill("1"), false, null).getCloseReason())
                .as("пустой признак намерения — отсутствие намерения")
                .isEqualTo(AttachedAlgoOrder.CloseReason.PROTECTION_LOST);
        assertThat(emptyAnalysis(fill("1"), false, true).getOutcomeUndetermined()).isTrue();
        assertThat(emptyAnalysis(fill("1"), true, false).getOutcomeUndetermined()).isTrue();
        assertThat(emptyAnalysis(fill("0"), false, false).getOutcomeUndetermined()).isTrue();
        assertThat(emptyAnalysis(null, false, false).getOutcomeUndetermined()).isTrue();
    }

    private AttachedProtectionResolution emptyAnalysis(BigDecimal trancheExposure, Boolean standaloneExists,
                                                       Boolean cancelIntentStanding) {
        return resolver.resolve(afterSearchCycle()
                .trancheExposure(trancheExposure)
                .standaloneProtectionExists(standaloneExists)
                .cancelIntentStanding(cancelIntentStanding)
                .build());
    }

    @Test
    @DisplayName("U11.13 — отдельная защита транша есть: исход не определён, терминал не ставится")
    void u11_13_anExistingStandaloneProtectionLeavesTheOutcomeUndetermined() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(true)
                .build());

        assertThat(resolution.getOutcomeUndetermined()).isTrue();
        assertThat(resolution.getStatus()).isNull();
    }

    @Test
    @DisplayName("U11.14 — пустая экспозиция транша в потерянное покрытие не уводит")
    void u11_14_anEmptyTrancheExposureDoesNotGiveLostProtection() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .trancheExposure(fill(""))
                .standaloneProtectionExists(false)
                .build());

        assertThat(resolution.getOutcomeUndetermined())
                .as("предикат стои́т на непустоте и строгой положительности (Z28)")
                .isTrue();
    }

    @Test
    @DisplayName("U11.15 — нулевая экспозиция транша: исход не определён")
    void u11_15_aZeroTrancheExposureDoesNotGiveLostProtection() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .trancheExposure(fill("0"))
                .standaloneProtectionExists(false)
                .build());

        assertThat(resolution.getOutcomeUndetermined()).isTrue();
    }

    @Test
    @DisplayName("U11.16 — неопределённый исход: статус и причина пусты, флаг явный, применять нечего")
    void u11_16_theUndeterminedResolutionCarriesAnExplicitFlag() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(true)
                .build());

        assertThat(resolution.getStatus()).isNull();
        assertThat(resolution.getCloseReason()).isNull();
        assertThat(resolution.getOutcomeUndetermined())
                .as("флаг явный, а не выводимый из пустого статуса")
                .isTrue();
        assertThat(resolution.hasStatus()).isFalse();
    }

    @Test
    @DisplayName("U11.17 — нога истории и самостоятельная запись вместе: берётся терминал по ноге")
    void u11_17_theHistoryLegIsReadBeforeTheStandaloneRecord() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .historyLegFound(ProtectionHistoryLeg.CANCELED)
                .standaloneRecordFound(true)
                .build());

        assertThat(resolution.getStatus())
                .as("разбор истории читается раньше признака живой записи (Z27)")
                .isEqualTo(AttachedAlgoOrder.Status.CANCELED);
    }

    @Test
    @DisplayName("U11.18 — родитель отменён с нулевым наливом: берётся отмена родителем, а не нога")
    void u11_18_theCancelledEmptyParentOutranksTheHistoryLeg() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.CANCELED)
                .parentAccumulatedFillSize(fill("0"))
                .historyLegFound(ProtectionHistoryLeg.EFFECTIVE)
                .build());

        assertThat(resolution.getStatus())
                .as("класс «терминален с пустым наливом» отвечает раньше гейта цикла (Z23)")
                .isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(resolution.getCloseReason())
                .isEqualTo(AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED);
    }

    // --- первое наблюдение терминала родителя ------------------------------

    @Test
    @DisplayName("Терминал впервые увиден, живой записи и разбора нет, риск транша не покрыт: ожидание, а не потеря")
    void aFirstTerminalObservationWithAnEmptyAnalysisWaits() {
        AttachedProtectionResolution resolution = resolver.resolve(firstTerminalObservation()
                .parentStatus(Order.Status.CANCELED)
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(false)
                .cancelIntentStanding(false)
                .build());

        assertThat(resolution.getStatus())
                .as("пустота на первом наблюдении мерит задержку постановки, а не судьбу защиты")
                .isNull();
        assertThat(resolution.getCloseReason()).isNull();
        assertThat(resolution.getOutcomeUndetermined())
                .as("ожидание сигнала не требует — вывод делает следующая добыча")
                .isFalse();
    }

    @Test
    @DisplayName("Терминал впервые увиден, экспозиции у транша нет, разбор пуст: ожидание, а не неопределённый исход")
    void aFirstTerminalObservationWithoutExposureWaitsInsteadOfSignalling() {
        AttachedProtectionResolution resolution = resolver.resolve(firstTerminalObservation()
                .parentStatus(Order.Status.CANCELED)
                .parentAccumulatedFillSize(fill(""))
                .trancheExposure(fill("0"))
                .standaloneProtectionExists(false)
                .build());

        assertThat(resolution.hasStatus()).isFalse();
        assertThat(resolution.getOutcomeUndetermined()).isFalse();
    }

    @Test
    @DisplayName("Терминал впервые увиден, разбор нашёл срабатывание: факт применяется сразу")
    void aFirstTerminalObservationAppliesAFoundHistoryLeg() {
        AttachedProtectionResolution resolution = resolver.resolve(firstTerminalObservation()
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(false)
                .historyLegFound(ProtectionHistoryLeg.EFFECTIVE)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.COMPLETED);
        assertThat(resolution.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.TRIGGERED);
    }

    @Test
    @DisplayName("Терминал впервые увиден, живая запись найдена: защита активна сразу")
    void aFirstTerminalObservationActivatesOnAFoundRecord() {
        AttachedProtectionResolution resolution = resolver.resolve(firstTerminalObservation()
                .standaloneRecordFound(true)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
    }

    @Test
    @DisplayName("Признак «терминал наблюдён раньше» пуст: ожидания нет — пустота тревогу не снимает")
    void anEmptyPriorTerminalOperandDoesNotOpenTheWaitBranch() {
        AttachedProtectionResolution resolution = resolver.resolve(afterSearchCycle()
                .parentTerminalObservedBefore(null)
                .trancheExposure(fill("1"))
                .standaloneProtectionExists(false)
                .build());

        assertThat(resolution.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.PROTECTION_LOST);
    }
}
