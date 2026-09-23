package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.json.JsonMapper;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E1} тропы «активация определения → сделка»: создание
 * определения у владельца и его проверка у ядра
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E1 — Определение
 * заводится у владельца и проверяется у ядра»).
 *
 * <p><b>Предмет — стык, а не поведение стороны.</b> Коды отказов владельца
 * определений проверены его ящиком; здесь мерится, что за его ответом
 * стоит НАСТОЯЩИЙ вызов ядра — с токеном, выданным провайдером, по
 * проекциям, которые ядро сняло у своих соседей, — и что прочие стороны
 * следа не оставили.
 *
 * <p><b>Тропа одна на группу, а предусловия ставит каждый кейс сам.</b>
 * Синк проекций и простановка чисел необратимы, поэтому кейсу, которому
 * нужно их ОТСУТСТВИЕ, тропа поднимает ядро заново на пустой базе
 * ({@link Trail#renew}); порядок методов подобран так, чтобы таких
 * подъёмов было меньше, но от него ни один кейс не зависит.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E1 — Определение заводится у владельца и проверяется у ядра")
class DefinitionIntakePathTest {

    private static final String PAIR_CHECKS = Trail.CORE + "/pair-checks";

    private static final String RISK_APPETITE = Trail.CORE + "/risk-appetites/" + Trail.TENANT;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Trail trail;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("e1");
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E1.4 — Чисел риск-аппетита у ядра нет: создание отвергнуто")
    void e1_4_absentRiskAppetiteNumbersRejectTheCreation() {
        trail.withoutRiskAppetite();
        trail.projectionsSynced();
        trail.feeRatesSynced();
        trail.forgetTraces();

        Answer answer = create();

        assertThat(answer.status()).as("E1.4: создание отвергнуто — ответ " + answer.body()).isEqualTo(400);
        assertThat(errorCodeOf(answer)).isEqualTo("STRATEGY_REQUEST_REJECTED");
        assertThat(errorMessageOf(answer)).as("E1.4: сверять объявленное не с чем — числа не назначены")
                .contains("STRATEGY_RISK_APPETITE_NOT_CONFIGURED");
        assertThat(trail.database(Party.STRATEGIES).count("strategies")).as("E1.4: строки определения нет").isZero();
        List<Side.Access> core = trail.accesses(Party.TRADING_CORE);
        assertThat(core).as("E1.4: оба чтения пришли к ядру, пара — первой")
                .extracting(Side.Access::path, Side.Access::status)
                .containsExactly(tuple(PAIR_CHECKS, 200), tuple(RISK_APPETITE, 200));
        assertIssuedToken(core, "E1.4");
        assertNoTraceBeyondOwnerAndCore("E1.4");
        Map<String, Object> numbers = object(trail.call(Party.TRADING_CORE, "GET", RISK_APPETITE, null, null));
        assertThat(numbers.get("globalSimultaneousRiskPerDealPercent"))
                .as("E1.4: строка тенанта у ядра есть — её заводит синк проекций, — а чисел в ней нет").isNull();
        assertThat(numbers.get("globalCatastrophicRiskPerDealMultiplier")).isNull();

        trail.riskAppetiteSet();
        trail.forgetTraces();

        Answer again = create();

        assertThat(again.status()).as("E1.4: повтор после простановки чисел проходит").isEqualTo(201);
        assertThat(trail.accesses(Party.TRADING_CORE)).as("E1.4: проекции чисел у владельца нет — он снова идёт к ядру")
                .extracting(Side.Access::path, Side.Access::status)
                .contains(tuple(RISK_APPETITE, 200));
    }

    @Test
    @Order(2)
    @DisplayName("E1.1 — Создание определения спрашивает у ядра пару и числа риск-аппетита")
    void e1_1_creationAsksTheCoreForThePairAndTheRiskAppetite() {
        trail.commonPreconditions();
        Long outboxBefore = trail.database(Party.TRADING_CORE).count("outbox_events");
        trail.forgetTraces();

        Answer answer = create();

        assertThat(answer.status()).as("E1.1: создание проходит — ответ " + answer.body()).isEqualTo(201);
        Map<String, Object> created = object(answer);
        assertThat(created.get("status")).as("E1.1: статус черновика").isEqualTo("CREATED");
        String internalId = String.valueOf(created.get("internalId"));
        assertThat(internalId).as("E1.1: идентичность определения отдана").isNotBlank();
        Answer listed = trail.call(Party.STRATEGIES, "GET", Trail.STRATEGIES, Trail.TENANT, null);
        assertThat(listed.body()).as("E1.1: определение читается перечнем владельца").contains(internalId);

        List<Side.Access> core = trail.accesses(Party.TRADING_CORE);
        assertThat(core).as("E1.1: к ядру пришли ровно чтение пары и чтение чисел")
                .extracting(Side.Access::path, Side.Access::status)
                .containsExactly(tuple(PAIR_CHECKS, 200),
                        tuple(RISK_APPETITE, 200));
        assertIssuedToken(core, "E1.1");
        assertThat(core.get(0).uri()).as("E1.1: пара спрошена в контексте тенанта и по ссылкам определения")
                .contains("tenantInternalId=" + Trail.TENANT)
                .contains("exchangeAccountInternalId=" + Trail.ACCOUNT)
                .contains("instrumentInternalId=" + Trail.INSTRUMENT);
        Answer deals = trail.call(Party.TRADING_CORE, "GET",
                Trail.CORE + "/deals?exchangeAccountInternalId=" + Trail.ACCOUNT, null, null);
        assertThat(deals.status()).isEqualTo(200);
        assertThat(JSON.readTree(deals.body()).size()).as("E1.1: своей записи ядро не делает — сделок нет").isZero();
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events")).as("E1.1: строк outbox у ядра не прибавилось")
                .isEqualTo(outboxBefore);
        assertNoTraceBeyondOwnerAndCore("E1.1");
    }

    /**
     * Красна по построению: владелец определений отказ соседа журналом не
     * называет вовсе, а дом ставит ожидаемому отказу реакцию «лог»
     * (docs/rules/error-handling-policy.md §«Внутренняя градация: четыре
     * уровня»). Находка — .claude/work/backlog.md §«Отказ соседа на создании
     * определения не оставляет записи в журнале владельца».
     */
    @Test
    @Tag("debt")
    @Order(3)
    @DisplayName("E1.3 — Сторона-сосед недостижима: создание отвергается, а не проходит непроверенным")
    void e1_3_anUnreachableCoreRejectsTheCreation() {
        trail.commonPreconditions();
        Long definitionsBefore = trail.database(Party.STRATEGIES).count("strategies");
        trail.stop(Party.TRADING_CORE);
        trail.forgetTraces();
        Long logMark = trail.side(Party.STRATEGIES).logMark();

        Answer answer = create();

        try {
            assertThat(answer.status()).as("E1.3: создание отвергнуто — ответ " + answer.body()).isEqualTo(503);
            assertThat(errorCodeOf(answer)).as("E1.3: операнд не добыт — класс недоступности соседа")
                    .isEqualTo("PEER_UNAVAILABLE");
            assertThat(trail.database(Party.STRATEGIES).count("strategies"))
                    .as("E1.3: непроверенным вход не проходит — строки не прибавилось").isEqualTo(definitionsBefore);
            assertNoTraceBeyondOwnerAndCore("E1.3");
            assertThat(trail.side(Party.STRATEGIES).logSince(logMark))
                    .as("E1.3: в журнале владельца назван отказ чтения соседа")
                    .containsIgnoringCase("trading-core");
        } finally {
            trail.start(Party.TRADING_CORE);
        }
        trail.forgetTraces();

        Answer again = create();

        assertThat(again.status()).as("E1.3: отказ не кэширован — повтор после подъёма ядра проходит").isEqualTo(201);
        assertThat(trail.accesses(Party.TRADING_CORE)).extracting(Side.Access::path)
                .as("E1.3: повтор снова спросил ядро").containsExactly(PAIR_CHECKS, RISK_APPETITE);
    }

    @Test
    @Order(4)
    @DisplayName("E1.2 — Проекций у ядра нет: создание отвергнуто, а не пропущено непроверенным")
    void e1_2_withoutProjectionsTheCreationIsRejected() {
        trail.withoutProjections();
        trail.riskAppetiteSet();
        Long definitionsBefore = trail.database(Party.STRATEGIES).count("strategies");
        trail.forgetTraces();

        Answer answer = create();

        assertThat(answer.status()).as("E1.2: отказ создания — ответ " + answer.body()).isEqualTo(400);
        assertThat(errorCodeOf(answer)).isEqualTo("STRATEGY_REQUEST_REJECTED");
        assertThat(errorMessageOf(answer)).as("E1.2: обе ссылки не разрешены ядром")
                .contains("STRATEGY_ACCOUNT_NOT_FOUND")
                .contains("STRATEGY_INSTRUMENT_NOT_FOUND");
        assertThat(trail.database(Party.STRATEGIES).count("strategies")).as("E1.2: строки определения нет")
                .isEqualTo(definitionsBefore);
        List<Side.Access> core = trail.accesses(Party.TRADING_CORE);
        assertThat(core).as("E1.2: пришло чтение пары, а чтения чисел — ни одного: ссылки проверяются первыми")
                .extracting(Side.Access::path, Side.Access::status)
                .containsExactly(tuple(PAIR_CHECKS, 200));
        assertIssuedToken(core, "E1.2");
        assertNoTraceBeyondOwnerAndCore("E1.2");
    }

    private static Answer create() {
        return trail.call(Party.STRATEGIES, "POST", Trail.STRATEGIES, Trail.TENANT, Trail.referenceDefinition());
    }

    /** Вызовы владельца к ядру несут токен, выданный стабом провайдера на {@code client_credentials}. */
    private static void assertIssuedToken(List<Side.Access> core, String label) {
        assertThat(core).as(label + ": вызовы к ядру несут токен службы, выданный провайдером")
                .allSatisfy(access -> assertThat(access.bearer()).isEqualTo(trail.identity().issuedServiceToken()));
    }

    /**
     * Сторон, чей след на создании не предусмотрен, не коснулось ничто.
     *
     * <p>Коннектор и стаб площадки — ни одного обращения; журнал и
     * статистика — ни одной строки; темы — ни одной записи: у создания нет
     * класса события.
     */
    private static void assertNoTraceBeyondOwnerAndCore(String label) {
        assertThat(trail.accesses(Party.CONNECTOR)).as(label + ": к коннектору обращений нет").isEmpty();
        assertThat(trail.exchange().requests()).as(label + ": к стабу площадки обращений нет").isEmpty();
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as(label + ": строк журнала нет").isZero();
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as(label + ": фактов статистики нет")
                .isZero();
        assertThat(trail.records(Substrate.STRATEGY_TOPIC)).as(label + ": тема владельца определений пуста").isEmpty();
        assertThat(trail.records(Substrate.CORE_TOPIC)).as(label + ": тема ядра пуста").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Answer answer) {
        return JSON.readValue(answer.body(), Map.class);
    }

    private static String errorCodeOf(Answer answer) {
        return String.valueOf(object(answer).get("code"));
    }

    private static String errorMessageOf(Answer answer) {
        return String.valueOf(object(answer).get("message"));
    }
}
