package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B11} — поверхность чтения и числа риск-аппетита.
 *
 * <p><b>Предмет группы — ВЫЗОВ, и выход его есть ТЕЛО ответа.</b> Поэтому
 * клетки читают {@link Answer#body()} дословно, а не только разобранный
 * объект: половина ожиданий группы утверждает о том, чего в теле НЕТ, —
 * числового ключа базы у сделки, транша и счёта
 * (.claude/rules/codestyle.md §«Идентичность наружу — `internalId`, не id
 * из БД»), — и разбор в типизованную форму такое ожидание выразить не
 * даёт.
 *
 * <p><b>Предусловие сделки ставится ПРЯМОЙ записью, и это названная
 * цена.</b> Своей тропы к сделке у ящика ещё нет — её строит тик сканера
 * входа (группа {@code B1}); до неё писателя сделки ящик не имеет вовсе,
 * и область такой записи объявлена у {@link Rows#put}. Все прочие
 * предусловия группы ставятся тропой ящика: проекции — тиком синка,
 * ступени — ручной поверхностью остановки, числа — назначением.
 *
 * <p><b>Окно чтения пинится ЧИСЛОМ, потому что кейс о нём и есть.</b>
 * Дом величины — {@code TradingSurfaceService.DEAL_WINDOW}; снаружи она
 * наблюдаема ровно тем, что выборка на ней обрывается, и вход клетки
 * обязан быть больше неё.
 */
class SurfaceReadBoxTest extends SharedTradingCoreBox {

    /**
     * Окно чтения сделок счёта: дом — {@code TradingSurfaceService}.
     * Вход клетки {@code B11.1} обязан быть больше него, иначе обрыв
     * выборки не наблюдается ничем.
     */
    private static final Integer DEAL_WINDOW = 100;

    /** Идентичность, которой в реестрах нет ни у одной сущности. */
    private static final String ABSENT = "NOPE";

    /** Тенант, которому счёт `A1` не принадлежит. */
    private static final String SECOND_TENANT = "T2";

    /** Заголовок контекста тенанта — тот же, которым его подаёт периметр. */
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Test
    @DisplayName("B11.1 — сделки счёта отдаются окном, от новых")
    void dealsOfAnAccountComeBackAsAWindowStartingFromTheNewest() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT,
                SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT));
        insertClosedDeals(DEAL_WINDOW + 1);

        Answer answer = get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT);

        assertThat(answer.status()).isEqualTo(200);
        List<Map<String, Object>> deals = answer.asList();
        // Выборка оборвана окном, а не размером истории счёта: читатель
        // приходит за текущим состоянием торговой строки, и история растёт
        // без предела (.claude/rules/codestyle.md §«Выборка данных»).
        assertThat(deals).hasSize(DEAL_WINDOW);
        // Порядок от новых: обрезана хвостовая, самая старая сделка.
        assertThat(deals.getFirst().get("internalId")).isEqualTo("D" + (DEAL_WINDOW + 1));
        assertThat(deals.getLast().get("internalId")).isEqualTo("D2");
        // Идентичности инструментов резолвены у ВСЕГО окна, и обе — то
        // есть раскладка накрыла оба ключа, а не первый попавшийся.
        assertThat(deals).allMatch(deal -> Objects.nonNull(deal.get("instrumentInternalId")));
        assertThat(deals.stream().map(deal -> deal.get("instrumentInternalId")).distinct().toList())
                .containsExactlyInAnyOrder(INSTRUMENT, SECOND_INSTRUMENT);
        // Наружу уходят идентичности: числового ключа базы нет ни у одной
        // строки ответа.
        assertThat(answer.body()).doesNotContain("\"id\":");
        // ПОЛОВИНА «ОДНОЙ раскладкой на всю выборку» снаружи ящика не
        // наблюдаема, и довод механический: число запросов к базе
        // наблюдалось бы либо счётчиками её статистики — а те выгружаются
        // лениво, и «одна» от «ста одной» отличалось бы гонкой, — либо
        // журналом запросов, а он ось СУБСТРАТА, общая всем классам
        // одного контекста. Носитель у половины при этом ЕСТЬ, и он
        // назван: {@code TradingSurfaceReadTest} того же дерева считает
        // обращения к резолву прямо, потому и не снимается ревизией
        // набора. Наблюдаемая ящиком половина — что резолв накрыл всё
        // окно, и она выше.
    }

    @Test
    @DisplayName("B11.2 — сделка отдаётся со своими траншами")
    void aDealComesBackWithItsOwnTranches() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Long deal = insertActiveDeal("D1");
        insertTranche("TR1", deal, 1);
        insertTranche("TR2", deal, 2);

        Answer answer = get(DEALS + "/D1");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("internalId")).isEqualTo("D1");
        // Идентичности связанных сущностей резолвены: вызывающий получает
        // то, чем он их и адресует.
        assertThat(answer.asObject().get("exchangeAccountInternalId")).isEqualTo(ACCOUNT);
        assertThat(answer.asObject().get("instrumentInternalId")).isEqualTo(INSTRUMENT);
        assertThat(answer.nestedList("tranches"))
                .extracting(tranche -> tranche.get("internalId"))
                .containsExactly("TR1", "TR2");
        // Числового ключа базы нет ни у сделки, ни у транша: граница
        // сервиса его не пересекает.
        assertThat(answer.body()).doesNotContain("\"id\":");
    }

    @Test
    @DisplayName("B11.3 — несуществующая идентичность — негодный вход вызова")
    void anUnknownIdentityIsABadRequestAndNotAServerFailure() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));

        List<Answer> answers = List.of(
                get(DEALS + "?exchangeAccountInternalId=" + ABSENT),
                get(DEALS + "/" + ABSENT),
                get(SAFETY_STATES + "/" + ABSENT));

        // Класс отказа у всех трёх один и тот же, и он о ЗАПРОСЕ: пятисотый
        // ответ сказал бы «чини сервер» тому, кто чинить обязан запрос
        // (docs/rules/error-handling-policy.md §«Внешняя поверхность»).
        assertThat(answers).allMatch(answer -> Boolean.TRUE.equals(answer.carriesErrorDto()));
        assertThat(answers).extracting(Answer::errorCode)
                .containsExactly(INVALID_REQUEST, INVALID_REQUEST, INVALID_REQUEST);
        // Число ответа пишет прогон; `500` остаётся пиньнутым отрицанием
        // (.claude/tests/cases/trading-core.md §«Число ответа и класс
        // отказа — разные ожидания»).
        assertThat(answers).extracting(Answer::status).containsExactly(400, 400, 400);
    }

    @Test
    @DisplayName("B11.4 — торговое состояние счёта показывает ступень и инструменты со стоящей ступенью")
    void theSafetyStateOfAnAccountNamesItsRungAndOnlyThePairsThatStand() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT,
                SECOND_INSTRUMENT, SECOND_EXTERNAL_INSTRUMENT));
        assertThat(freeze(ACCOUNT).status()).isEqualTo(204);
        assertThat(post(HALTS, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT)).status()).isEqualTo(204);

        Answer answer = get(SAFETY_STATES + "/" + ACCOUNT);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("exchangeAccountInternalId")).isEqualTo(ACCOUNT);
        assertThat(answer.asObject().get("accountSafetyRung")).isEqualTo("HOLD");
        // Назван ровно тот инструмент, у которого ступень СТОИ́Т: второй в
        // ответе не появляется — строки состояния у него нет вовсе
        // (docs/rules/manual-halt.md §«Наблюдаемость: ручное отличимо и от
        // автоматики, и друг от друга»).
        assertThat(answer.asObject().get("instrumentInternalIdsWithStandingRung"))
                .isEqualTo(List.of(INSTRUMENT));
        assertThat(answer.body()).doesNotContain(SECOND_INSTRUMENT);
        assertThat(answer.body()).doesNotContain("\"id\":");
    }

    @Test
    @DisplayName("B11.5 — числа риск-аппетита назначаются снимком намерения целиком")
    void assigningTheRiskAppetiteErasesEveryFieldTheBodyDidNotCarry() {
        provisionAccounts(ACCOUNT);
        assertThat(put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("1.5", "3", "4")).status())
                .isEqualTo(200);

        Answer answer = put(RISK_APPETITES + "/" + TENANT, """
                {"globalSimultaneousRiskPerDealPercent": 2, "globalConsecutiveLossLimit": 5}
                """);

        assertThat(answer.status()).isEqualTo(200);
        // Непереданное поле СТЁРТО: тело есть снимок намерения держателя
        // целиком, и «не прислал» не может значить «оставь как было» —
        // иначе оно стало бы неотличимо от «снял»
        // (docs/rules/risk-policy.md §«Числа назначает держатель; пустое
        // место — отказ»).
        assertThat(answer.asObject().get("globalCatastrophicRiskPerDealMultiplier")).isNull();
        Answer read = get(RISK_APPETITES + "/" + TENANT);
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.asObject().get("globalCatastrophicRiskPerDealMultiplier")).isNull();
        assertThat(read.asObject().get("globalConsecutiveLossLimit")).isEqualTo(5);
        assertThat(read.asObject().get("tenantInternalId")).isEqualTo(TENANT);
        assertThat(rows.row("tenant_risk_appetites", "tenant_internal_id", TENANT)
                .get("global_catastrophic_risk_per_deal_multiplier")).isNull();
    }

    @Test
    @DisplayName("B11.6 — отсутствие строки чисел отличается от пустых чисел")
    void anAbsentRowOfNumbersIsNotTheSameAsARowOfEmptyNumbers() {
        provisionAccounts(ACCOUNT);

        Answer unknown = get(RISK_APPETITES + "/" + SECOND_TENANT);
        Answer known = get(RISK_APPETITES + "/" + TENANT);

        // Строки нет — ядро о тенанте не знает вовсе, и это негодный вход
        // вызова, а не пустой ответ (docs/rules/absent-value-semantics.md).
        assertThat(unknown.carriesErrorDto()).isTrue();
        assertThat(unknown.errorCode()).isEqualTo(INVALID_REQUEST);
        assertThat(unknown.status()).isEqualTo(400);
        // Строка есть, числа пусты — назначения не было, и ответ об этом
        // говорит: пустота есть отказ risk-creating действия, а не ноль.
        assertThat(known.status()).isEqualTo(200);
        assertThat(known.asObject().get("globalSimultaneousRiskPerDealPercent")).isNull();
        assertThat(known.asObject().get("globalCatastrophicRiskPerDealMultiplier")).isNull();
        assertThat(known.asObject().get("globalConsecutiveLossLimit")).isNull();
    }

    @Test
    @DisplayName("B11.7 — негодное тело назначения чисел отвергается валидацией")
    void aBodyOutsideTheDeclaredBoundsIsRefusedAndChangesNothing() {
        provisionAccounts(ACCOUNT);
        put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("1.5", "3", "4"));

        // Ноль области определения процента не принадлежит: граница
        // объявлена самим полем (@DecimalMin, inclusive = false).
        Answer answer = put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("0", "3", "4"));

        // Число пишет КОНТЕЙНЕР — нарушено объявленное ограничение поля, и
        // дом называет его тем же контрактом.
        assertThat(answer.status()).isEqualTo(400);
        // По дому тело — единый error-DTO; сегодня отказ контейнера его не
        // несёт, и это НАЗВАННЫЙ долг, а не находка клетки
        // (.claude/work/backlog.md §«Единый error-DTO у поверхностей
        // соседних сервисов»).
        assertThat(answer.carriesErrorDto()).isFalse();
        // Числа в базе не изменились: отказ дошёл до записи.
        Map<String, Object> row = rows.row("tenant_risk_appetites", "tenant_internal_id", TENANT);
        assertThat(String.valueOf(row.get("global_simultaneous_risk_per_deal_percent")))
                .startsWith("1.5");
        assertThat(row.get("global_consecutive_loss_limit")).isEqualTo(4);
    }

    @Test
    @DisplayName("B11.8 — проверка пары отвечает признаками, а не отказом")
    void thePairCheckAnswersWithFlagsAndNeverWithARefusal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));

        Answer sound = pairCheck(TENANT, ACCOUNT, INSTRUMENT);
        Answer foreignTenant = pairCheck(SECOND_TENANT, ACCOUNT, INSTRUMENT);
        Answer absentAccount = pairCheck(TENANT, ABSENT, INSTRUMENT);
        Answer absentInstrument = pairCheck(TENANT, ACCOUNT, ABSENT);

        // Ненайденность здесь — ОТВЕТ, а не исключение: вопрос вызывающего
        // и есть «существует ли», и бросок заставил бы его разбирать статус
        // вместо тела (docs/rules/strategy-validation.md).
        assertThat(List.of(sound, foreignTenant, absentAccount, absentInstrument))
                .extracting(Answer::status).containsExactly(200, 200, 200, 200);
        assertThat(flags(sound)).containsExactly(true, true, true);
        assertThat(flags(foreignTenant)).containsExactly(true, false, true);
        // Счёта нет — принадлежность ему тенанта ложна тем же ответом:
        // неизвестный счёт не «принадлежит» никому.
        assertThat(flags(absentAccount)).containsExactly(false, false, true);
        assertThat(flags(absentInstrument)).containsExactly(true, true, false);
    }

    @Test
    @DisplayName("B11.9 — проверка пары отвечает проекциями со своим моментом снимка")
    void thePairCheckAnswersFromProjectionsAndErrsTowardsRefusal() {
        // У владельца реестра счёт уже есть, а синк проекций ещё не
        // проходил: строк у ядра нет.
        auth.answers(PEER_ACCOUNTS, Feed.array(Feed.account(ACCOUNT, TENANT, "DEMO", "ACTIVE")));
        marketData.answers(PEER_INSTRUMENTS, Feed.emptyArray());

        Answer answer = pairCheck(TENANT, ACCOUNT, INSTRUMENT);

        assertThat(answer.status()).isEqualTo(200);
        // Ошибка направлена в ЗАПРЕЩАЮЩУЮ сторону: членство в реестре
        // монотонно, и устаревшая проекция может лишь не знать о новом —
        // но не признать несуществующее.
        assertThat(flags(answer)).containsExactly(false, false, false);
        assertThat(rows.count("exchange_accounts")).isZero();
        // Отвечали проекции, а не владелец: к нему вызова не было ни
        // одного.
        assertThat(auth.count()).isZero();
    }

    @Test
    @DisplayName("B11.10 — поверхность чтения состояния не двигает")
    void readingTheSurfaceTwiceMovesNothingAtAll() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Long deal = insertActiveDeal("D1");
        insertTranche("TR1", deal, 1);
        post(HALTS, Bodies.halt("SOFT", ACCOUNT, INSTRUMENT));
        put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("1.5", "3", "4"));
        connector.forgetRequests();
        Map<String, Long> before = rows.countsByTable();

        List<Answer> first = readEveryPoint();
        List<Answer> second = readEveryPoint();

        assertThat(first).extracting(Answer::status).allMatch(status -> Objects.equals(200, status));
        assertThat(second).extracting(Answer::body)
                .containsExactlyElementsOf(first.stream().map(Answer::body).toList());
        // Ни одной строки не прибавилось и не убыло: чтение не заводит ни
        // строк исполнения, ни отчётов, ни outbox.
        assertThat(rows.countsByTable()).isEqualTo(before);
        assertThat(rows.row("deals", "internal_id", "D1").get("status")).isEqualTo("ACTIVE");
        assertThat(rows.row("exchange_accounts", "internal_id", ACCOUNT).get("safety_rung"))
                .isEqualTo("ACTIVE");
        // Мягкая ступень ПАРЫ называется своей лестницей — `ENTRY_BLOCKED`:
        // счётная лестница и инструментная различаются именами ступеней
        // (docs/rules/instrument-hold.md).
        assertThat(rows.row("account_instrument_states", "instrument_id", instrumentId(INSTRUMENT))
                .get("safety_rung")).isEqualTo("ENTRY_BLOCKED");
        // К площадке чтение не ходит вовсе: торговых решений поверхность не
        // принимает (docs/architecture/services/trading-core.md §«Чего не
        // делает намеренно»).
        assertThat(connector.count()).isZero();
    }

    @Test
    @DisplayName("B11.11 — тенант операндом на читающих тропах не принимается")
    void theTenantHeaderChangesNothingOnTheReadingPaths() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        insertActiveDeal("D1");
        String path = DEALS + "?exchangeAccountInternalId=" + ACCOUNT;

        Answer withForeignTenant = getWithHeader(path, TENANT_HEADER, SECOND_TENANT);
        Answer withoutHeader = get(path);

        // СЕГОДНЯ: оба ответа одинаковы — тенант в отборе не участвует
        // вовсе, отбор идёт идентичностью счёта.
        assertThat(withForeignTenant.status()).isEqualTo(200);
        assertThat(withoutHeader.status()).isEqualTo(200);
        assertThat(withForeignTenant.body()).isEqualTo(withoutHeader.body());
        assertThat(withForeignTenant.single().get("internalId")).isEqualTo("D1");
        // ПОЛОВИНА «по дому» не прогоняется: контракт называет третьим
        // звеном радиуса «домен применяет полученного тенанта к своим
        // строкам», а у ядра этого звена нет совсем — то есть ожидание
        // предъявляет находку F-1, а не дефект клетки
        // (.claude/work/backlog.md §«Тенант на читающих тропах ядра
        // операндом не принимается»). Прогоняемой она станет исходом
        // находки.
    }

    // ------------------------------------------------------------------
    // Предусловия и наблюдатели группы
    // ------------------------------------------------------------------

    /**
     * Заводит названное число ТЕРМИНАЛЬНЫХ сделок счёта одной вставкой.
     *
     * <p>Терминальных — потому что слот пары держит не больше одной
     * незакрытой сделки, и окно чтения иначе не наполнить вовсе.
     * Инструменты чередуются: раскладка идентичностей обязана накрыть
     * оба ключа, а не первый попавшийся.
     *
     * @param count сколько сделок завести
     */
    private void insertClosedDeals(Integer count) {
        rows.put("""
                insert into deals (internal_id, exchange_account_id, instrument_id, status, direction,
                                   entry_reason)
                select 'D' || generated, ?, case when mod(generated, 2) = 0 then ? else ? end,
                       'CLOSED', 'LONG', 'RECOVERY'
                from generate_series(1, ?) as generated
                """, accountId(ACCOUNT), instrumentId(SECOND_INSTRUMENT),
                instrumentId(INSTRUMENT), count);
    }

    /** Ставит живую сделку счёта прямой записью и отдаёт её ключ. */
    private Long insertActiveDeal(String internalId) {
        return rows.insert("""
                insert into deals (internal_id, exchange_account_id, instrument_id, status, direction,
                                   entry_reason)
                values (?, ?, ?, 'ACTIVE', 'LONG', 'RECOVERY') returning id
                """, internalId, accountId(ACCOUNT), instrumentId(INSTRUMENT));
    }

    /** Ставит транш сделки прямой записью: своего писателя у ящика ещё нет. */
    private void insertTranche(String internalId, Long deal, Integer level) {
        rows.put("""
                insert into deal_tranches (internal_id, deal_id, level, status, entry_filled)
                values (?, ?, ?, 'MANAGING', 1)
                """, internalId, deal, level);
    }

    /** Проверка пары по трём идентичностям. */
    private Answer pairCheck(String tenantInternalId, String accountInternalId,
                             String instrumentInternalId) {
        return get(PAIR_CHECKS + "?tenantInternalId=" + tenantInternalId
                + "&exchangeAccountInternalId=" + accountInternalId
                + "&instrumentInternalId=" + instrumentInternalId);
    }

    /** Три признака ответа проверки пары в порядке объявления. */
    private List<Object> flags(Answer answer) {
        Map<String, Object> body = answer.asObject();
        return List.of(body.get("accountFound"), body.get("accountBelongsToTenant"),
                body.get("instrumentFound"));
    }

    /** Все читающие точки поверхности подряд, в объявленном порядке. */
    private List<Answer> readEveryPoint() {
        return List.of(
                get(DEALS + "?exchangeAccountInternalId=" + ACCOUNT),
                get(DEALS + "/D1"),
                get(SAFETY_STATES + "/" + ACCOUNT),
                get(RISK_APPETITES + "/" + TENANT),
                pairCheck(TENANT, ACCOUNT, INSTRUMENT));
    }
}
