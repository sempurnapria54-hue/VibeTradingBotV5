package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B1} документа кейсов: создание определения
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Выходов у создания ТРИ, и клетка утверждает обо всех:</b> ответ
 * поверхности, строка определения в своей базе и ОТСУТСТВИЕ строки
 * outbox — у статуса {@code CREATED} писателя события нет
 * (docs/architecture/contracts.md §«У каждого класса события назван
 * писатель, и он же писатель решения»). Четвёртый выход — исходящие
 * вызовы к соседу: операнды обеих проверок живут у ядра, и «ушло ровно
 * два» наблюдается только записями стаба.
 *
 * <p><b>Ожидание берёт ПАРУ «класс отказа плюс реджект-код текста», а не
 * HTTP-число.</b> Класс у всех бросков поверхности один —
 * {@code STRATEGY_REQUEST_REJECTED}, — а что именно отвергнуто, несёт
 * реджект-код; число же выбирается на месте броска и ходом выравнивания
 * кодов по платформе меняется (.claude/tests/cases/strategies.md §«Число
 * ответа и класс отказа — разные ожидания»). Исключение названо там же:
 * {@code 400} на создании фиксирует дом валидации заголовком, {@code 201}
 * — контракт успеха самой точки.
 */
class DefinitionIntakeBoxTest extends SharedStrategiesBox {

    /** Класс отказа, которым отвечают все броски поверхности владельца. */
    private static final String REJECTED = "STRATEGY_REQUEST_REJECTED";

    @Test
    @DisplayName("B1.1 — Штатное создание заводит черновик и ничего не публикует")
    void b1_1_theRegularCreationDraftsAndPublishesNothing() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.status()).as("B1.1: контракт успеха точки создания").isEqualTo(201);
        assertThat(String.valueOf(answer.asObject().get("internalId"))).isNotBlank();
        assertThat(answer.asObject()).containsEntry("status", "CREATED");
        Map<String, Object> row = rows.row(STRATEGIES_TABLE, "internal_id",
                answer.asObject().get("internalId"));
        assertThat(row).containsEntry("tenant_internal_id", TENANT);
        assertThat(rows.count("strategy_details")).as("дерево легло вместе с корнем").isPositive();
        assertThat(rows.count("strategy_tranches")).isPositive();
        assertThat(rows.count("strategy_steps")).isPositive();
        assertThat(rows.count("strategy_actions")).isPositive();
        assertThat(rows.count(OUTBOX_TABLE))
                .as("у черновика писателя события нет: строки outbox не заводится")
                .isZero();
        assertThat(peer.paths())
                .as("к ядру ушли ровно два чтения — разрешимость ссылок и числа тенанта")
                .containsExactly(PEER_PAIR_CHECKS, PEER_RISK_APPETITES + "/" + TENANT);
    }

    @Test
    @DisplayName("B1.2 — Тенант берётся из заголовка, а не из тела")
    void b1_2_theTenantComesFromTheHeaderNotTheBody() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, TENANT, Bodies.referenceWith("tenantId", SECOND_TENANT));

        assertThat(answer.status()).isEqualTo(201);
        Map<String, Object> row = rows.row(STRATEGIES_TABLE, "internal_id",
                answer.asObject().get("internalId"));
        assertThat(row).containsEntry("tenant_internal_id", TENANT);
        // Искомое — ЗНАЧЕНИЕ поля, поэтому сравнение идёт со строкой JSON в
        // кавычках: голая подстрока «T2» встречается в сроках годности
        // («PT2H») и краснела бы на исправной системе.
        assertThat(answer.body())
                .as("значение из тела не попадает в ответ ни одним полем")
                .doesNotContain("\"" + SECOND_TENANT + "\"");
        assertThat(row.values())
                .as("и в строку определения оно не попадает ни одной колонкой")
                .doesNotContain(SECOND_TENANT);
        assertThat(peer.single(PEER_PAIR_CHECKS).getUrl())
                .as("радиус проверки ссылок — тенант заголовка")
                .contains("tenantInternalId=" + TENANT);
    }

    @Test
    @DisplayName("B1.3 — Идентичность присваивает сервис, присланная не принимается")
    void b1_3_theServiceAssignsTheIdentityAndRejectsTheSentOne() {
        peerResolvesEverything();
        String sent = "st-chosen-by-caller";

        Answer first = post(STRATEGIES, TENANT, Bodies.referenceWith("internalId", sent));
        Answer second = post(STRATEGIES, TENANT, Bodies.referenceWith("internalId", sent));

        assertThat(first.status()).isEqualTo(201);
        String assigned = String.valueOf(first.asObject().get("internalId"));
        assertThat(assigned).isNotEqualTo(sent);
        assertThat(rows.countWhere(STRATEGIES_TABLE, "internal_id", assigned)).isEqualTo(1L);
        assertThat(rows.countWhere(STRATEGIES_TABLE, "internal_id", sent))
                .as("присланная идентичность не ложится в базу")
                .isZero();
        assertThat(String.valueOf(second.asObject().get("internalId")))
                .as("идемпотентности у создания нет и не объявлено")
                .isNotEqualTo(assigned);
    }

    @Test
    @DisplayName("B1.4 — Несуществующий счёт отвергает создание своим кодом")
    void b1_4_anAbsentAccountRejectsTheCreationWithItsOwnCode() {
        peerResolvesEverything();
        peer.answers(PEER_PAIR_CHECKS, Feed.pairCheck(Boolean.FALSE, Boolean.FALSE, Boolean.TRUE));

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.status()).as("число отказа создания фиксирует дом валидации").isEqualTo(400);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(REJECTED);
        assertThat(answer.errorMessage())
                .contains("STRATEGY_ACCOUNT_NOT_FOUND")
                .as("отказ адресует поле, а не «не годится»")
                .contains("exchangeAccountInternalId");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
        assertThat(rows.count(OUTBOX_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.5 — Счёт чужого тенанта отвергается тем же кодом, но другой причиной")
    void b1_5_anAccountOfAnotherTenantIsRejectedByTheSameCodeForAnotherReason() {
        peerResolvesEverything();
        peer.answers(PEER_PAIR_CHECKS, Feed.pairCheck(Boolean.TRUE, Boolean.FALSE, Boolean.TRUE));

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorCode()).isEqualTo(REJECTED);
        assertThat(answer.errorMessage())
                .as("код тот же, а текст причины иной — счёт принадлежит другому тенанту")
                .contains("STRATEGY_ACCOUNT_NOT_FOUND")
                .contains("другому тенанту");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.6 — Несуществующий инструмент отвергается своим кодом")
    void b1_6_anAbsentInstrumentIsRejectedByItsOwnCode() {
        peerResolvesEverything();
        peer.answers(PEER_PAIR_CHECKS, Feed.pairCheck(Boolean.TRUE, Boolean.TRUE, Boolean.FALSE));

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage())
                .contains("STRATEGY_INSTRUMENT_NOT_FOUND")
                .contains("instrumentInternalId");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.7 — Обе ссылки негодны: отказ называет обе, а не первую")
    void b1_7_bothBrokenReferencesAreNamedNotJustTheFirst() {
        peerResolvesEverything();
        peer.answers(PEER_PAIR_CHECKS, Feed.pairCheck(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE));

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage())
                .as("отказ адресует каждый ложный конъюнкт (docs/concept.md П3)")
                .contains("STRATEGY_ACCOUNT_NOT_FOUND")
                .contains("STRATEGY_INSTRUMENT_NOT_FOUND");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.8 — Неназначенные числа риск-аппетита отвергают создание")
    void b1_8_unassignedRiskAppetiteNumbersRejectTheCreation() {
        peerResolvesEverything();
        peer.answers(PEER_RISK_APPETITES + "/" + TENANT,
                Feed.riskAppetite(TENANT, null, GLOBAL_CATASTROPHIC_MULTIPLIER));

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage()).contains("STRATEGY_RISK_APPETITE_NOT_CONFIGURED");
        assertThat(answer.errorMessage())
                .as("без глобальных чисел неравенства не считаются вовсе")
                .doesNotContain("_ABOVE_GLOBAL");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.9 — Пустая строка чисел у ядра от пустого числа не отличается")
    void b1_9_anAbsentRowOfNumbersIsIndistinguishableFromAnEmptyNumber() {
        peerResolvesEverything();
        peer.answers(PEER_RISK_APPETITES + "/" + TENANT, "");

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage())
                .as("исход у обеих причин один: сверять объявленное не с чем")
                .contains("STRATEGY_RISK_APPETITE_NOT_CONFIGURED");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.10 — Ядро не ответило: создание отвергается, а не проходит непроверенным")
    void b1_10_anUnavailablePeerRejectsTheCreation() {
        peerResolvesEverything();
        peer.answers(PEER_PAIR_CHECKS, 503, "{}");

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode())
                .as("операнд не добыт — класс отказа о недоступности соседа")
                .isEqualTo("PEER_UNAVAILABLE");
        assertThat(rows.count(STRATEGIES_TABLE)).as("непроверенным вход не проходит").isZero();
        assertThat(rows.count(OUTBOX_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.11 — Ядро отвергло наш запрос: класс отказа другой")
    void b1_11_aRefusingPeerGivesAnotherFailureClass() {
        peerResolvesEverything();
        peer.answers(PEER_PAIR_CHECKS, 400, "{}");

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode())
                .as("осознанный отказ соседа — наш дефект, повтором не лечится")
                .isEqualTo("PEER_REFUSED");
        assertThat(answer.errorCode()).isNotEqualTo("PEER_UNAVAILABLE");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.12 — Структурный дефект дерева отвергается на создании")
    void b1_12_aStructuralDefectOfTheTreeIsRejectedOnCreation() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, TENANT, Bodies.withEntryAllocation(BigDecimal.ZERO));

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage())
                .contains("STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE")
                .as("отказ адресует путь до детали")
                .contains("details[");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
        assertThat(rows.count(OUTBOX_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.13 — Отсутствие обязательного объявления отличается от негодного значения")
    void b1_13_anAbsentDeclarationDiffersFromAnInvalidValue() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, TENANT, Bodies.withoutEntryAllocation());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage())
                .as("пустое и нулевое разведены: обязательность и диапазон — разные проверки")
                .contains("STRATEGY_ACTION_ALLOCATION_NOT_DECLARED")
                .doesNotContain("STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.14 — Неравенство создания отвергает определение, которое не сработает никогда")
    void b1_14_theCreationInequalityRejectsADefinitionThatWouldNeverFire() {
        peerResolvesEverything();
        String body = Bodies.withDetailNumber("strategySimultaneousRiskPerDealPercent",
                new BigDecimal("2.0"));

        Answer answer = post(STRATEGIES, TENANT, body);

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage()).contains("STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.15 — Неудовлетворимый веер траншей отвергается своим кодом, а не кодом потолка")
    void b1_15_anUnsatisfiableTrancheFanIsRejectedByItsOwnCode() {
        peerResolvesEverything();
        String body = Bodies.withDetailNumber("riskPerActionPercent", new BigDecimal("2.0"));

        Answer answer = post(STRATEGIES, TENANT, body);

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage())
                .as("величины разведены по реджект-кодам: слияние потеряло бы адресность")
                .contains("STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE")
                .doesNotContain("STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.16 — Нулевой запас нотинала отвергается созданием")
    void b1_16_zeroNotionalHeadroomIsRejectedOnCreation() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, TENANT, Bodies.withEntryAllocation(new BigDecimal("100")));

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage()).contains("STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.17 — Доля запаса приходит константой правила, а не числом тенанта")
    void b1_17_theHeadroomShareIsARuleConstantNotATenantNumber() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, TENANT, Bodies.withEntryAllocation(new BigDecimal("99.5")));

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.errorMessage())
                .as("запас считается и БЕЗ третьего числа в ответе соседа")
                .contains("STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT")
                .doesNotContain("STRATEGY_RISK_APPETITE_NOT_CONFIGURED");
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
    }

    @Test
    @DisplayName("B1.18 — Непредъявленный заголовок контекста отвергается единым error-DTO")
    void b1_18_anAbsentContextHeaderIsRejectedByTheSingleErrorDto() {
        peerResolvesEverything();

        Answer answer = postWithoutTenant(STRATEGIES, Bodies.reference());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.carriesErrorDto())
                .as("отказ контейнера отвечает тем же телом, что и всякая ошибка поверхности")
                .isTrue();
        assertThat(answer.errorMessage()).isNotBlank();
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
        assertThat(peer.count()).as("вход отсечён до домена: к ядру не ушло ни одного запроса").isZero();
    }

    @Test
    @DisplayName("B1.19 — Пустой заголовок контекста от отсутствующего не отличается")
    void b1_19_anEmptyContextHeaderIsIndistinguishableFromAnAbsentOne() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, "", Bodies.reference());

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count(STRATEGIES_TABLE)).isZero();
        assertThat(peer.count()).as("охрана непустоты отсекает вход до домена").isZero();
    }

    @Test
    @DisplayName("B1.20 — Колонки аудита заполняются по правилу состава")
    void b1_20_theAuditColumnsAreFilledByTheCompositionRule() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, TENANT, Bodies.reference());

        Map<String, Object> row = rows.row(STRATEGIES_TABLE, "internal_id",
                answer.asObject().get("internalId"));
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row)
                .as("актор равен имени предъявленного принципала, а не классу контура")
                .containsEntry("created_by", IdentityStub.PRINCIPAL);
        assertThat(row.get("external_created_at"))
                .as("биржевые поля пишет тот, кто производит данные площадки")
                .isNull();
        assertThat(row.get("external_modified_at")).isNull();
    }

    /**
     * Пустая база защиты уходит в строку определения марк-ценой, а не
     * пустотой: иначе копия ядра довезла бы до площадки молчание, которое
     * та читает последней ценой.
     */
    @Test
    @DisplayName("B1.21 — Пустая база срабатывания защиты записывается марк-ценой")
    void b1_21_anEmptyProtectiveTriggerIsStoredAsMark() {
        peerResolvesEverything();

        Answer answer = post(STRATEGIES, TENANT,
                Bodies.withoutActionField("bull_protection_oco", "triggerPriceType"));

        assertThat(answer.status()).isEqualTo(201);
        Object actionId = rows.row("strategy_actions", "key", "bull_protection_oco").get("id");
        assertThat(rows.row("strategy_algo_order_actions", "id", actionId))
                .containsEntry("trigger_price_type", "MARK");
    }
}
