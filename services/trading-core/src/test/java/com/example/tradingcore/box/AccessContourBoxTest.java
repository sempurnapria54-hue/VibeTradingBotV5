package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B12} — контур доступа и отказы поверхности.
 *
 * <p><b>Ожидание проверяет КЛАСС отказа, а не HTTP-число, всюду, где число
 * пишет наш собственный обработчик</b>
 * (.claude/tests/cases/trading-core.md §«Число ответа и класс отказа —
 * разные ожидания»). Числами пиньнуты ровно те исходы, чьё число
 * фиксирует дом: {@code 401} точки входа контура, {@code 405} и
 * {@code 400} контейнера, {@code 200} и {@code 204} контракта успеха.
 *
 * <p><b>Клетки с меткой {@code debt} красны ПО ПОСТРОЕНИЮ</b>: их
 * ожидание взято из дома, который дерево кода ещё не несёт, и ослаблять
 * его под текущий факт значило бы закрепить дефект
 * (.claude/work/backlog.md §«Единый error-DTO у поверхностей соседних
 * сервисов»).
 */
class AccessContourBoxTest extends SharedTradingCoreBox {

    /** Основа идентичности определения, которым заводится сделка радиуса. */
    private static final String DEFINITION = "S-ACCESS";

    /** Биржевой момент, который отдаёт коннектор. */
    private static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /** Последняя цена момента. */
    private static final String LAST_PRICE = "100";

    @Test
    @Tag("debt")
    @DisplayName("B12.1 — вызов без предъявленного принципала")
    void aCallWithoutAPresentedPrincipalIsRefusedWithTheSharedErrorDto() {
        Answer answer = getAnonymously(DEALS + "?exchangeAccountInternalId=" + ACCOUNT);

        assertThat(answer.status()).isEqualTo(401);
        // Долг: единого error-DTO у отказа доступа сегодня нет —
        // точки входа отказа ядро не берёт вовсе.
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(auth.count()).isZero();
        assertThat(marketData.count()).isZero();
        assertThat(connector.count()).isZero();
    }

    @Test
    @Tag("debt")
    @DisplayName("B12.2 — токен чужого ключа, просроченный и чужого издателя")
    void threeBrokenTokensAreRefusedAndTheSignatureIsCheckedLocally() {
        assertThat(getWith(DEALS, identity.foreignKeyToken()).status()).isEqualTo(401);
        assertThat(getWith(DEALS, identity.expiredToken()).status()).isEqualTo(401);
        assertThat(getWith(DEALS, identity.foreignIssuerToken()).status()).isEqualTo(401);

        // Локальность проверки мерится ростом числа обращений к провайдеру
        // при повторной проверке ГОДНОГО токена, а не их нулём: неизвестный
        // идентификатор ключа заставляет декодер перечитать НАБОР ключей —
        // это добыча ключей, а не подтверждение токена.
        get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT);
        Integer before = identity.count();
        get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT);
        get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT);

        assertThat(identity.count()).isEqualTo(before);
        assertThat(getWith(DEALS, identity.foreignKeyToken()).carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B12.3 — пер-операционных проверок права нет намеренно")
    void allFourOperationsArePassedUnderTheSingleServicePrincipal() {
        provisionAccounts(ACCOUNT);

        Answer deals = get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT);
        Answer halt = post(HALTS, Bodies.halt("FREEZE", ACCOUNT));
        Answer appetite = put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("1.5", "2", "3"));
        Answer job = post(JOBS + "/entry-scanner", "");

        // Приняты — то есть не отвергнуты контуром доступа: различать при
        // одном субъекте некого.
        assertThat(deals.status()).isEqualTo(200);
        assertThat(halt.status()).isNotIn(401, 403);
        assertThat(appetite.status()).isNotIn(401, 403);
        assertThat(job.status()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("B12.4 — проба живости открыта, остальное закрыто")
    void theLivenessProbeIsOpenAndTheRestIsClosed() {
        Answer health = getAnonymously(HEALTH);
        Answer metrics = getAnonymously("/actuator/metrics");

        assertThat(health.status()).isEqualTo(200);
        assertThat(metrics.status()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("B12.5 — неизвестный путь не предъявившему себя")
    void anUnknownPathIsRefusedRatherThanReportedMissing() {
        Answer answer = getAnonymously(ROOT + "/whatever");

        // 401, а не 404: не предъявивший себя не узнаёт, существует ли путь.
        assertThat(answer.status()).isEqualTo(401);
    }

    @Test
    @Tag("debt")
    @DisplayName("B12.6 — неподдержанный метод и неразбираемое тело")
    void containerProducedRefusalsCarryTheSameErrorDto() {
        provisionAccounts(ACCOUNT);

        Answer wrongMethod = get(HALTS);
        Answer unparseable = post(HALTS, Bodies.unparseable());

        assertThat(wrongMethod.status()).isEqualTo(405);
        assertThat(unparseable.status()).isEqualTo(400);
        // Долг: отказ, произведённый контейнером, отдаёт своё тело, а не
        // единый error-DTO поверхности.
        assertThat(wrongMethod.carriesErrorDto()).isTrue();
        assertThat(unparseable.carriesErrorDto()).isTrue();
        assertThat(rows.count("account_instrument_states")).isZero();
        assertThat(rows.row("exchange_accounts", "internal_id", ACCOUNT).get("safety_rung"))
                .isEqualTo("NONE");
    }

    @Test
    @DisplayName("B12.7 — отказ соседа наружу — отказ зависимости, а не наша ошибка")
    void aPeerFailureIsReportedAsADependencyFailureAndNotAsOurs() {
        standHaltedAccountWithDeal();
        Object dealTouchedAt = dealRow().get("modified_at");
        // Сосед ОТВЕЧАЕТ отказом своей стороны: таймаут, обрыв и `5xx` —
        // одна и та же недоступность (`PeerCall`), и `5xx` ставится без
        // платы временем.
        marketData.refusesAnything(503, "{}");
        connector.forgetRequests();

        Answer answer = post(HALT_CLEARANCES, Bodies.halt("FULL", ACCOUNT));

        // Класс отказа — недоступность соседа, а не «всё непредусмотренное»:
        // наша сторона исправна, и повтор имеет смысл позже.
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(PEER_SERVICE_UNAVAILABLE);
        assertThat(answer.status()).isEqualTo(503);
        // Ступень стои́т: снятие не применилось ни на байт.
        assertThat(rungOf(ACCOUNT)).isEqualTo("TRADE_BLOCKED");
        // Снятия риска не гонялось и сделка не тронута: отказ соседа по
        // ярусу сделку в ошибку не уводит (docs/rules/runtime-error-classification.md
        // §«Отказ соседа по ярусу — свой класс, и сделку в ошибку он не
        // уводит»). В ошибочном состоянии она уже стои́т — её увёл каскад
        // жёсткой ступени предусловия, — поэтому наблюдается НЕТРОНУТОСТЬ
        // строки, а не её статус.
        assertThat(connector.requests()).isEmpty();
        assertThat(dealRow().get("modified_at")).isEqualTo(dealTouchedAt);
    }

    @Test
    @DisplayName("B12.8 — исходящий токен наружу не выходит")
    void theOutgoingServiceTokenNeverLeavesTheProcess() {
        Integer mark = AppLog.mark();
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        // Тропы к соседям гоняются ПОСЛЕ предусловия: оно свои записи у
        // стабов забывает, и отрицание о путях иначе мерило бы пустоту.
        tick(Tick.REGISTRY_PROJECTIONS);
        tick(Tick.STRATEGY_DEMAND);
        tick(Tick.TRADE_FEE_RATES);
        Answer deals = get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT);
        Answer state = get(SAFETY_STATES + "/" + ACCOUNT);
        Answer missing = get(DEALS + "?exchangeAccountInternalId=NOPE");

        assertThat(AppLog.since(mark)).doesNotContain(IdentityStub.SERVICE_ACCESS_TOKEN);
        assertThat(deals.body()).doesNotContain(IdentityStub.SERVICE_ACCESS_TOKEN);
        assertThat(state.body()).doesNotContain(IdentityStub.SERVICE_ACCESS_TOKEN);
        assertThat(missing.body()).doesNotContain(IdentityStub.SERVICE_ACCESS_TOKEN);
        // Ключей биржевого счёта ядро не запрашивает ни у кого: хранилища
        // секретов в субстрате нет вовсе, и весь набор троп прошёл.
        assertThat(auth.paths()).containsOnly(PEER_ACCOUNTS);
    }

    @Test
    @Tag("debt")
    @DisplayName("B12.10 — след отказа доступа")
    void theAccessDenialLeavesATrace() {
        Integer mark = AppLog.mark();

        getAnonymously(DEALS + "?exchangeAccountInternalId=" + ACCOUNT);

        // Часть о СТРОКЕ не прогоняется: таблицы отказов доступа в схеме
        // ядра нет (.claude/work/backlog.md §«Таблица отказов доступа у
        // сервисов со своей базой»), и её появление — исход находки, а не
        // ожидание этой клетки.
        assertThat(rows.tableNames()).doesNotContain("access_denials");
        // Долг, добытый прогоном: следа у отказа доступа НЕТ ВОВСЕ — ни
        // строки, ни записи журнала. Точек входа отказа ядро не берёт из
        // общего артефакта периметра, а контур отвечает молча. Ожидание
        // взято из дома и под текущий факт не ослаблено.
        assertThat(AppLog.since(mark)).contains(ACCOUNT);
    }

    // ------------------------------------------------------------------
    // Предусловия группы
    // ------------------------------------------------------------------

    /**
     * Счёт под сворачиванием, на радиусе которого есть сделка недавнего
     * окна: предусловие снятия обязано собрать её контекст, а сборка
     * контекста ходит к владельцу рыночных данных.
     *
     * <p><b>Сделка ставится тиком отбора входа, ступень — ручной
     * поверхностью:</b> обе тропы свои, прямой записи в предусловии нет.
     * Каскад жёсткой ступени уводит сделку в ошибочное состояние тем же
     * ходом — это не помеха клетке: кандидатом живого риска она остаётся,
     * потому что ошибочное состояние не терминально.
     */
    private void standHaltedAccountWithDeal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithPrice(MarketPhase.Type.BULL_TREND.name(), LAST_PRICE));
        connector.answers(balancePath(ACCOUNT), balanceBody());
        connector.answers(positionPath(ACCOUNT), Feed.absent());
        connector.answers(closedPositionsPath(ACCOUNT), Feed.emptyArray());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        activate(Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND));
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(1L);
        fullHalt(ACCOUNT);
        assertThat(rungOf(ACCOUNT)).isEqualTo("TRADE_BLOCKED");
    }

    /** Единственная сделка. */
    private Map<String, Object> dealRow() {
        return rows.all("deals").getFirst();
    }

    /** Ступень счёта, как её видит база. */
    private String rungOf(String accountInternalId) {
        return String.valueOf(rows.row("exchange_accounts", "internal_id", accountInternalId)
                .get("safety_rung"));
    }

    /** Путь чтения связки фич момента у владельца рыночных данных. */
    private String featuresPath(String instrumentInternalId) {
        return PEER_INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }

    /** Корень путей счёта у коннектора. */
    private String accountPath(String accountInternalId) {
        return "/api/v1/accounts/" + accountInternalId;
    }

    /** Путь чтения снимка средств у коннектора. */
    private String balancePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/balance";
    }

    /** Живой эпизод позиции по инструменту: след хода снятия риска. */
    private String positionPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/instrument";
    }

    /** История закрытых эпизодов: нога 2 добычи позиции. */
    private String closedPositionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/closed";
    }

    /** Снимок средств моментом прогона: возраст ставится В ДАННЫХ. */
    private String balanceBody() {
        String moment = OffsetDateTime.now(ZoneOffset.UTC).toString();
        return """
                {
                  "externalUpdatedAt": "%s",
                  "externalTotalEquity": "100000",
                  "externalAdjustedEquity": "100000",
                  "externalAvailableEquity": "100000",
                  "balances": [
                    {
                      "externalCurrency": "USDT",
                      "externalUpdatedAt": "%s",
                      "externalEquity": "100000",
                      "externalCashBalance": "100000",
                      "externalAvailableBalance": "100000",
                      "externalFrozenBalance": "0"
                    }
                  ]
                }
                """.formatted(moment, moment);
    }
}
