package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.dealStepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newAlgo;
import static com.example.strategies.unit.validation.ValidationFixture.newOrder;
import static com.example.strategies.unit.validation.ValidationFixture.newPositionAction;
import static com.example.strategies.unit.validation.ValidationFixture.newStep;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.stepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyActionApiModel;
import com.example.strategies.api.model.strategy.StrategyOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyPositionActionApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Пара «вид, тип» действия шага транша — группа {@code U34} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом перечня —
 * docs/models/domain/aggregate/Strategy.md §Действия, реджект —
 * docs/rules/strategy-validation.md).
 *
 * <p><b>Кейсы смотрят только код пары.</b> Действие, подложенное в дорожку
 * транша, задевает и соседние проверки (цель, покрытие, доли); их отказы
 * предметом группы не являются, и счёт ведётся по коду.
 */
class TrancheActionPairTest {

    private static final String PAIR = "STRATEGY_ACTION_KIND_TYPE_UNSUPPORTED";
    private static final String SWITCHED_PATH = "details[0].tranches[bull_main].stepsByStatus[PROTECTION_SWITCHED][0]";

    @Test
    @DisplayName("U34.1 — базовая сборка: все пары траншевых действий допустимы")
    void u34_1_theReferenceDeclaresOnlyAllowedPairs() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U34.2 — снятие обычной заявки: исполнителя у пары нет")
    void u34_2_aCancelOnAnOrderKindIsUnsupported() {
        CreateStrategyApiRequest request = reference();
        declareOnTranche(request, order("order_cancel", "CANCEL_ACTION"));

        assertThat(matching(violations(request), PAIR))
                .singleElement()
                .asString()
                .contains(SWITCHED_PATH + ".actions[0] " + PAIR)
                .endsWith("допустимы [CREATE_ACTION, REPLACE_ACTION], объявлено CANCEL_ACTION");
    }

    @Test
    @DisplayName("U34.3 — выход видом заявки на транше: выход объявляется только видом позиции")
    void u34_3_anExitOnAnOrderKindIsUnsupported() {
        CreateStrategyApiRequest request = reference();
        declareOnTranche(request, order("order_exit", "EXIT_ACTION"));

        assertThat(matching(violations(request), PAIR))
                .singleElement()
                .asString()
                .endsWith("объявлено EXIT_ACTION");
    }

    @Test
    @DisplayName("U34.4 — выход видом условной заявки: тот же отказ со своим перечнем")
    void u34_4_anExitOnAnAlgoKindIsUnsupported() {
        CreateStrategyApiRequest request = reference();
        declareOnTranche(request, newAlgo("algo_exit", "EXIT_ACTION", "STOP_LOSS"));

        assertThat(matching(violations(request), PAIR))
                .singleElement()
                .asString()
                .endsWith("допустимы [CREATE_ACTION, REPLACE_ACTION, CANCEL_ACTION], объявлено EXIT_ACTION");
    }

    @Test
    @DisplayName("U34.5 — действие вида позиции с типом создания на транше")
    void u34_5_aCreateOnAPositionKindIsUnsupported() {
        CreateStrategyApiRequest request = reference();
        StrategyPositionActionApiModel position = newPositionAction("position_create");
        position.setActionType("CREATE_ACTION");
        declareOnTranche(request, position);

        assertThat(matching(violations(request), PAIR))
                .singleElement()
                .asString()
                .endsWith("допустимы [EXIT_ACTION], объявлено CREATE_ACTION");
    }

    @Test
    @DisplayName("U34.6 — выход позиции на транше: пара допустима")
    void u34_6_anExitOnAPositionKindIsAllowedOnATranche() {
        CreateStrategyApiRequest request = reference();
        declareOnTranche(request, newPositionAction("tranche_exit"));

        assertThat(matching(violations(request), PAIR)).isEmpty();
    }

    @Test
    @DisplayName("U34.7 — замещение у заявки и условной заявки: оговорка перечня, пара допустима")
    void u34_7_aReplaceStaysAllowedOnBothOrderKinds() {
        CreateStrategyApiRequest request = reference();
        declareOnTranche(request, order("order_replace", "REPLACE_ACTION"),
                newAlgo("algo_replace", "REPLACE_ACTION", "STOP_LOSS"));

        assertThat(matching(violations(request), PAIR)).isEmpty();
    }

    @Test
    @DisplayName("U34.8 — тип вне перечня типов: отказ разбора, второго нарушения пара не даёт")
    void u34_8_anUnknownTypeIsRejectedOnlyByTheEnumCheck() {
        CreateStrategyApiRequest request = reference();
        declareOnTranche(request, order("order_amend", "AMEND_ACTION"));

        List<String> violations = violations(request);

        assertThat(matching(violations, SWITCHED_PATH + ".actions[0].actionType: unknown value AMEND_ACTION"))
                .hasSize(1);
        assertThat(matching(violations, PAIR)).isEmpty();
    }

    @Test
    @DisplayName("U34.9 — та же пара на уровне сделки: отказывает узость пакета сделки, а не проверка пары")
    void u34_9_theDealLevelIsCoveredByItsOwnNarrowing() {
        CreateStrategyApiRequest request = reference();
        StrategyPositionActionApiModel position = newPositionAction("deal_fail_safe");
        position.setActionType("CREATE_ACTION");
        dealStepsByStatus(bull(request)).put("EXIT_PENDING", List.of(newStep("FAIL_SAFE", position)));

        assertThat(matching(violations(request), PAIR)).isEmpty();
    }

    /** Шаг первичной защиты в дорожке бычьего транша, несущий только подложенные действия. */
    private void declareOnTranche(CreateStrategyApiRequest request, StrategyActionApiModel... actions) {
        stepsByStatus(tranche(bull(request))).put("PROTECTION_SWITCHED", List.of(newStep("MAIN_PROTECTION", actions)));
    }

    /** Действие-заявка с объявленным типом; прочие поля — как у входа эталона. */
    private StrategyOrderActionApiModel order(String key, String actionType) {
        StrategyOrderActionApiModel order = newOrder(key, "MARKET", "LONG", "100");
        order.setActionType(actionType);
        return order;
    }
}
