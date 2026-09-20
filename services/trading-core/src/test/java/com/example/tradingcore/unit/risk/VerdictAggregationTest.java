package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.CONTRACT_VALUE;
import static com.example.tradingcore.unit.risk.RiskFixture.FEE;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.pairState;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static com.example.tradingcore.unit.risk.RiskFixture.workingRules;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Свёртка перечня в решение вердикта — группа {@code U20} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/models/RiskValidationResult.md; поведение перечня —
 * docs/components/RiskValidator.md §Проверки).
 *
 * <p><b>Свёртка живёт ПРИВАТНЫМ методом валидатора, и публичной тропы к
 * ней с прямо построенным перечнем не существует</b> — три её точки
 * входа принимают состояние, а не список проверок. Поэтому клетки
 * группы, чьи члены валидатор производит сам, прогоняются через
 * преконтроль, а клетки U20.3-U20.5 остаются описанными и
 * непрогоняемыми: их вход требует члена со статусом, которого не ставит
 * ни одна фабрика результата (находка R-2 и её сосед по статусу
 * «пройдено»), — тот же класс, что у объявленного документом U20.6.
 */
class VerdictAggregationTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U20.1 — перечень пуст: решение разрешающее")
    void u20_1_anEmptyCheckListYieldsAnAllowingDecision() {
        RiskValidationResult result = harness.validate(entryAction(), workingContext());

        assertThat(result.getChecks()).isEmpty();
        assertThat(result.getDecision()).isEqualTo(RiskDecision.ALLOWED);
    }

    @Test
    @DisplayName("U20.2 — один блокирующий член: решение блокирующее")
    void u20_2_aSingleBlockingMemberYieldsABlockingDecision() {
        InstrumentExternalRules suspended = workingRules();
        suspended.setStatus(InstrumentExternalRules.Status.SUSPEND);
        harness.givenRules(suspended);

        RiskValidationResult result = harness.validate(entryAction(), workingContext());

        assertThat(codes(result)).containsExactly(RiskCheckCode.INSTRUMENT_NOT_LIVE);
        assertThat(result.getDecision()).isEqualTo(RiskDecision.BLOCKED);
    }

    @Test
    @DisplayName("U20.7 — три блокирующих члена: порядок членов сохранён")
    void u20_7_theAccumulationOrderIsPreserved() {
        InstrumentExternalRules suspendedWithHighMinimum = rules(CONTRACT_VALUE, "1", "20", FEE);
        suspendedWithHighMinimum.setStatus(InstrumentExternalRules.Status.SUSPEND);
        harness.givenRules(suspendedWithHighMinimum);
        AccountInstrumentState cross = pairState(Instrument.SafetyRung.ACTIVE);
        cross.setMarginMode(Instrument.MarginMode.CROSS);
        harness.givenPairState(cross);

        RiskValidationResult result = harness.validate(entryAction(), workingContext());

        assertThat(codes(result))
                .as("первым стои́т тот, чья проверка идёт раньше")
                .containsExactly(RiskCheckCode.INSTRUMENT_NOT_LIVE,
                        RiskCheckCode.MARGIN_MODE_NOT_ISOLATED,
                        RiskCheckCode.SIZE_BELOW_MIN);
        assertThat(result.getDecision()).isEqualTo(RiskDecision.BLOCKED);
    }
}
