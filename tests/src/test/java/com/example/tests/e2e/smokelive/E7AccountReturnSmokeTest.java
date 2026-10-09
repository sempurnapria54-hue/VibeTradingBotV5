package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.smokelive.Perimeter.Reply;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.smokelive.SmokeRun.require;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа-конец {@code E7} дыма: счёт вернулся в то состояние, в котором дым
 * его застал (.claude/tests/cases/smoke-live.md §«E7 — Счёт вернулся в то
 * состояние, в котором дым его застал»).
 *
 * <p><b>Авторитет — ответ площадки ключом прогона, зеркало ядра — сверяемая
 * сторона</b> (.claude/decisions/smoke-account-return-authority.md).
 * Расхождение двух ответов и невозврат после принудительной зачистки — отказ
 * прогона ({@link IllegalStateException}), а не красный кейс: следующий прогон
 * по грязному счёту проверял бы остаток предыдущего.
 *
 * <p><b>Группа идёт и после отказавших групп</b>: класс бежит последним в
 * порядке полного имени, и его предусловие — не исход прежних кейсов, а
 * предъявленное {@code E4.3} тождество счетов.
 *
 * <p><b>Определения дыма деактивируются первым ходом группы</b> — до чтения
 * счёта: активное определение при снятой ступени открыло бы новую сделку
 * посреди проверки возврата.
 */
@Tag("smoke")
@DisplayName("E7 — Счёт вернулся в то состояние, в котором дым его застал")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E7AccountReturnSmokeTest {

    private static SmokeRun run;

    @BeforeAll
    static void retireSmokeDefinitions() {
        run = SmokeRun.get();
        for (String definition : smokeDefinitions()) {
            String status = run.perimeter().get("/api/v1/strategies/" + definition).json().path("status").asString("");
            if ("ACTIVE".equals(status)) {
                Reply retired = run.deactivateDefinition(definition);
                require(retired.status() == 200, "определение дыма " + definition + " не деактивировано: " + retired);
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("E7.1 — Открытых заявок и позиций по инструменту дыма не осталось")
    void e7_1_noOpenOrdersOrPositionsRemainOnTheSmokeInstrument() {
        require(isTrue(run.accountIdentityProven()), "тождество счёта ключа прогона и счёта системы не предъявлено"
                + " E4.3 — исхода «вернулся» кейс не даёт");

        if (isTrue(dirty())) {
            run.fullHalt();
            try {
                run.driveUntil("принудительная зачистка пары дыма полной ступенью", run.exchangeTimeout(),
                        () -> isTrue(clean()));
            } catch (ConditionTimeoutException timeout) {
                throw new IllegalStateException("ОТКАЗ ПРОГОНА: счёт не вернулся и после полной ступени — счёт"
                        + " возвращает держатель: " + describe(), timeout);
            }
        }

        require(isTrue(clean()), "ответ площадки и зеркало ядра расходятся — " + describe());
        assertThat(run.okx().pendingOrders(run.externalInstrument())).as("E7.1: открытые заявки остались")
                .isEmpty();
        assertThat(run.okx().pendingAlgoOrders(run.externalInstrument())).as("E7.1: защитные заявки остались")
                .isEmpty();
        assertThat(run.okx().openPositions(run.externalInstrument())).as("E7.1: позиция осталась").isEmpty();
        assertThat(run.liveSmokeDeal()).as("E7.1: у ядра осталась живая сделка по паре дыма").isNull();
    }

    @Test
    @Order(2)
    @DisplayName("E7.2 — Настройки счёта те же, что были до прогона")
    void e7_2_theAccountSettingsAreTheSameAsBeforeTheRun() {
        require(isTrue(run.accountIdentityProven()) && nonNull(run.configAtStart()),
                "снимок настроек до первого хода либо тождество счёта не предъявлены");
        if (run.safetyState().path("standingInstrumentRungs").has(run.instrument())) {
            Reply cleared = run.pairHaltClearance();
            require(cleared.status() == 204, "ступень пары дыма не снята обратным ходом: " + cleared);
        }

        JsonNode config = run.okx().accountConfig();
        JsonNode leverage = run.okx().leverage(run.externalInstrument(), run.marginMode());
        JsonNode safety = run.safetyState();

        assertThat(config.path("acctLv").asString("")).as("E7.2: уровень счёта изменён прогоном")
                .isEqualTo(run.configAtStart().path("acctLv").asString(""));
        assertThat(config.path("posMode").asString("")).as("E7.2: режим позиций изменён прогоном")
                .isEqualTo(run.configAtStart().path("posMode").asString(""));
        assertThat(lever(leverage)).as("E7.2: плечо по инструменту дыма изменено прогоном")
                .isEqualTo(lever(run.leverageAtStart()));
        assertThat(safety.path("accountSafetyRung").asString("")).as("E7.2: на счёте стои́т ступень защиты")
                .isEqualTo("ACTIVE");
        assertThat(safety.path("standingInstrumentRungs").has(run.instrument()))
                .as("E7.2: на паре дыма стои́т ступень защиты: %s", safety).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("E7.3 — Что дым оставляет навсегда — названо, а не скрыто")
    void e7_3_whatTheSmokeLeavesForeverIsNamed() {
        for (String definition : smokeDefinitions()) {
            assertThat(run.perimeter().get("/api/v1/strategies/" + definition).json().path("status").asString(""))
                    .as("E7.3: определение дыма %s осталось активным", definition).isNotEqualTo("ACTIVE");
        }
        Reply accounts = run.perimeter().get(run.accountsPath());
        assertThat(accounts.json().size()).as("E7.3: прогон завёл либо удалил счёт").isEqualTo(run.accountCountAtStart());
    }

    private static List<String> smokeDefinitions() {
        List<String> definitions = new ArrayList<>();
        for (String definition : new String[] {run.restingDefinition(), run.marketDefinition()}) {
            if (nonNull(definition)) {
                definitions.add(definition);
            }
        }
        return definitions;
    }

    private static Boolean clean() {
        return run.okx().pendingOrders(run.externalInstrument()).isEmpty()
                && run.okx().pendingAlgoOrders(run.externalInstrument()).isEmpty()
                && run.okx().openPositions(run.externalInstrument()).isEmpty()
                && isNull(run.liveSmokeDeal());
    }

    private static Boolean dirty() {
        return isTrue(clean()) ? Boolean.FALSE : Boolean.TRUE;
    }

    private static String describe() {
        return "площадка: заявок " + run.okx().pendingOrders(run.externalInstrument()).size()
                + ", условных " + run.okx().pendingAlgoOrders(run.externalInstrument()).size()
                + ", позиций " + run.okx().openPositions(run.externalInstrument()).size()
                + "; ядро: живая сделка " + run.liveSmokeDeal();
    }

    private static String lever(JsonNode leverageInfo) {
        return leverageInfo.isArray() && leverageInfo.size() > 0 ? leverageInfo.get(0).path("lever").asString("") : "";
    }
}
