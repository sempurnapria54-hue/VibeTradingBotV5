package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Контур доступа и отказы поверхности — группа {@code B8} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Вся поверхность закрыта, открытое перечислено поимённо</b>
 * (docs/rules/api-access-policy.md), и пер-операционных проверок здесь
 * нет намеренно: вызывающие — соседи по ярусу, а не человек, и различать
 * на этой границе некого.
 *
 * <p><b>Ожидание проверяет КЛАСС отказа, а не число</b>, всюду, где число
 * пишет наш собственный обработчик: набора HTTP-кодов не фиксирует ни
 * один дом (docs/rules/error-handling-policy.md §«Внешняя поверхность»),
 * и пиньнутое число сделало бы клетку красной на исправной системе ровно
 * тем ходом, которым коды выравниваются по платформе. Числа контейнера и
 * точки входа контура — контракт, и они остаются числами.
 */
class AccessContourBoxTest extends SharedMarketDataBox {

    /**
     * Ожидание формы тела взято из дома: отказ доступа есть тот же
     * контракт, что и прочие ошибки поверхности
     * (docs/rules/error-handling-policy.md). Сегодня точка входа контура
     * отдаёт пустое тело; долг — `.claude/work/backlog.md` §«Единый
     * error-DTO у поверхностей соседних сервисов». Сам отказ при этом
     * ЗЕЛЁН: закрытая точка не предъявившему себя не отвечает данными.
     */
    @Test
    @Tag("debt")
    @DisplayName("B8.1 — вызов без предъявленного принципала")
    void b8_1_aCallWithoutAPresentedPrincipal() {
        provisionInstruments(INSTRUMENT);

        Answer answer = getAnonymously(INSTRUMENTS);

        assertThat(answer.status()).isEqualTo(401);
        assertThat(connector.count()).isEqualTo(0);
        assertThat(answer.carriesErrorDto()).isTrue();
    }

    /**
     * <b>«Локально» мерится ростом числа обращений к провайдеру, а не их
     * нулём, и это уточнение поймано прогоном.</b> Неизвестный
     * идентификатор ключа заставляет декодер перечитать НАБОР ключей —
     * это добыча ключей, а не подтверждение токена; подтверждения же не
     * происходит никогда, и потому проверка годного токена не стои́т
     * провайдеру ни одного обращения, сколько бы раз её ни повторили.
     */
    @Test
    @DisplayName("B8.2 — токен, подписанный чужим ключом, просроченный и чужого издателя")
    void b8_2_aTokenSignedByAForeignKeyExpiredAndOfAForeignIssuer() {
        Answer foreignKey = getWith(INSTRUMENTS, identity.foreignKeyToken());
        Answer expired = getWith(INSTRUMENTS, identity.expiredToken());
        Answer foreignIssuer = getWith(INSTRUMENTS, identity.foreignIssuerToken());
        Integer providerCallsBefore = identity.count();

        get(INSTRUMENTS);
        get(INSTRUMENTS);
        get(INSTRUMENTS);

        assertThat(foreignKey.status()).isEqualTo(401);
        assertThat(expired.status()).isEqualTo(401);
        assertThat(foreignIssuer.status()).isEqualTo(401);
        assertThat(identity.count()).isEqualTo(providerCallsBefore);
    }

    @Test
    @DisplayName("B8.3 — служебная идентичность соседа поверхность принимает")
    void b8_3_theSurfaceAcceptsANeighbourServiceIdentity() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();

        Answer listing = get(INSTRUMENTS);
        Answer features = post(INSTRUMENTS + "/" + instrument + "/features", "{}");

        assertThat(listing.status()).isEqualTo(200);
        assertThat(features.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("B8.4 — проба живости открыта, остальное закрыто")
    void b8_4_theLivenessProbeIsOpenAndTheRestIsClosed() {
        Answer health = getAnonymously("/actuator/health");
        Answer metrics = getAnonymously("/actuator/metrics");

        assertThat(health.status()).isEqualTo(200);
        assertThat(health.asObject().get("status")).isEqualTo("UP");
        assertThat(metrics.status()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("B8.5 — неизвестный путь не предъявившему себя")
    void b8_5_anUnknownPathToTheOneWhoDidNotPresentThemselves() {
        Answer answer = getAnonymously("/api/v1/market-data/whatever");

        assertThat(answer.status()).isEqualTo(401);
    }

    /**
     * Ожидание формы тела взято из дома: отказ, произведённый
     * контейнером, есть тот же контракт, что и отказ приложения
     * (docs/rules/error-handling-policy.md §«Отказ, произведённый
     * контейнером, — тот же контракт»); долг — `.claude/work/backlog.md`
     * §«Единый error-DTO у поверхностей соседних сервисов». Сами числа
     * при этом ЗЕЛЕНЫ.
     */
    @Test
    @Tag("debt")
    @DisplayName("B8.6 — неподдержанный метод и неразбираемое тело")
    void b8_6_anUnsupportedMethodAndAnUnparseableBody() {
        provisionInstruments(INSTRUMENT);

        Answer method = get(REQUIREMENTS + "/candles");
        Answer body = post(REQUIREMENTS + "/candles", "{");

        assertThat(method.status()).isEqualTo(405);
        assertThat(body.status()).isEqualTo(400);
        assertThat(rows.count("candle_groups")).isEqualTo(0L);
        assertThat(method.carriesErrorDto()).isTrue();
        assertThat(body.carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B8.7 — отказ доступа площадки наружу — отказ зависимости")
    void b8_7_anExchangeAccessRefusalTravelsOutAsADependencyFailure() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), 429, Feed.refusal("RATE_LIMITED"));

        Answer answer = get(INSTRUMENTS + "/" + instrument + "/prices");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_ACCESS_REFUSED");
    }

    @Test
    @DisplayName("B8.8 — рядовой отказ чтения площадки наружу")
    void b8_8_anOrdinaryExchangeReadFailureTravelsOut() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), 500,
                Feed.refusal("EXCHANGE_READ_FAILED"));

        Answer answer = get(INSTRUMENTS + "/" + instrument + "/prices");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("EXCHANGE_READ_FAILED");
    }

    /**
     * Ожидание взято из дома: точка чтения цен объявляет {@code 204} на
     * «тикера на площадке нет» — контракт УСПЕХА, а не реакция на отказ
     * ({@code @ApiResponses} точки цен;
     * docs/components/MarketPriceDataService.md §Поведение).
     *
     * <p>Сегодня та же ветвь отвечает {@code 404}: пустое значение
     * уходит в {@code ResponseEntity.ofNullable}, а он на пустоте даёт
     * «не найдено», а не «нет содержимого». Читатель, ветвящийся по
     * объявленному контракту, отсутствия тикера не распознаёт. Находка
     * {@code F-11}; долг — `.claude/work/backlog.md` §«Отсутствие тикера
     * у чтения цен `market-data` отвечает не объявленным числом».
     */
    @Test
    @Tag("debt")
    @DisplayName("B8.9 — тикера на площадке нет — это пустота, а не отказ")
    void b8_9_noTickerOnTheExchangeIsEmptinessNotAFailure() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), 200, "");

        Answer answer = get(INSTRUMENTS + "/" + instrument + "/prices");

        assertThat(answer.status()).isEqualTo(204);
        assertThat(answer.body()).isEmpty();
    }

    @Test
    @DisplayName("B8.10 — тенанта поверхность не принимает вовсе")
    void b8_10_theSurfaceDoesNotAcceptATenantAtAll() {
        String instrument = provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT).getFirst();

        Answer without = get(INSTRUMENTS);
        Answer with = getWithHeader(INSTRUMENTS, "X-Tenant-Id", "TENANT-1");
        Answer groupsWithout = get(INSTRUMENTS + "/" + instrument + "/candle-groups");
        Answer groupsWith = getWithHeader(INSTRUMENTS + "/" + instrument + "/candle-groups",
                "X-Tenant-Id", "TENANT-1");

        assertThat(with.status()).isEqualTo(without.status());
        assertThat(with.body()).isEqualTo(without.body());
        assertThat(groupsWith.status()).isEqualTo(groupsWithout.status());
        assertThat(groupsWith.body()).isEqualTo(groupsWithout.body());
    }

    /**
     * Ожидание взято из дома: след отказа пишет тот, у кого есть своя
     * база, и пишет он ЕГО ДО ОТВЕТА
     * (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого
     * есть база»; docs/models/domain/other/AccessDenial.md).
     *
     * <p>Сегодня у сервиса нет ни таблицы, ни точки входа отказа: отказ
     * уходит наружу, не оставляя следа НИ ОДНОГО — ни строки, ни записи
     * журнала. Прогон это и показал: ожидание «хотя бы журнальный след»
     * не сошлось, и потому клетка предъявляет весь долг целиком, а не его
     * половину. Долг — `.claude/work/backlog.md` §«Таблица отказов
     * доступа у сервисов со своей базой».
     */
    @Test
    @Tag("debt")
    @DisplayName("B8.11 — след отказа доступа")
    void b8_11_theTraceOfAnAccessRefusal() {
        Integer mark = AppLog.mark();

        Answer answer = getAnonymously(INSTRUMENTS);

        assertThat(answer.status()).isEqualTo(401);
        assertThat(rows.tableNames()).contains("access_denials");
        assertThat(AppLog.since(mark)).isNotEmpty();
    }

    @Test
    @DisplayName("B8.12 — исходящий токен наружу не выходит")
    void b8_12_theOutgoingTokenDoesNotTravelOut() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        connector.answers(ConnectorStub.TICKERS, Feed.emptyMap());
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(1L, 3));
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), Feed.prices(INSTRUMENT, "50500"));
        tick(Tick.SNAPSHOTS);

        List<Answer> answers = List.of(
                get(INSTRUMENTS + "/" + instrument + "/prices"),
                post(INSTRUMENTS + "/" + instrument + "/features",
                        Bodies.featureRead(Bodies.array(), Bodies.array(), Boolean.TRUE)),
                get(INSTRUMENTS));
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), 500,
                Feed.refusal("EXCHANGE_READ_FAILED"));
        Answer failure = get(INSTRUMENTS + "/" + instrument + "/prices");

        assertThat(answers).allSatisfy(answer ->
                assertThat(answer.body()).doesNotContain(IdentityStub.SERVICE_ACCESS_TOKEN));
        assertThat(failure.body()).doesNotContain(IdentityStub.SERVICE_ACCESS_TOKEN);
        assertThat(AppLog.text()).doesNotContain(IdentityStub.SERVICE_ACCESS_TOKEN);
    }
}
