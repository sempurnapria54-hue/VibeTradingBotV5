package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.smokelive.Perimeter.Reply;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.awaitility.Awaitility.await;

/**
 * Прогон дыма целиком — одно состояние на все группы
 * (.claude/tests/cases/smoke-live.md).
 *
 * <p><b>Группы идут цепочкой, и состояние одно:</b> группа {@code E4}
 * начинается тем, что поставила {@code E3}, группа-конец {@code E7} читает то,
 * что оставили все. Классы групп бегут подряд в одной JVM в порядке полного
 * имени ({@code runOrder} модуля), и состояние живёт здесь, а не в классе.
 *
 * <p><b>Предусловия прогон ЧИТАЕТ, а не ставит</b> — кроме двух, у которых
 * прямого чтения нет ни у одной точки и которые поэтому ставятся ходом (ставка
 * комиссии, плечо пары); невыполненное — отказ прогона
 * ({@link #requireCommonPreconditions()}).
 *
 * <p><b>Счёт должен быть свободен целиком, а не только по инструменту дыма.</b>
 * Живая сделка на счёте одна (docs/rules/trading-constraints.md), поэтому
 * сделка по чужому инструменту закрывает вход дыма, а сделка дыма — чужие
 * определения. Чужие активные определения на счёте (на стенде — эталонные
 * стратегии на ETH-USDT-SWAP и SOL-USDT-SWAP) открыли бы сделку посреди
 * прогона: по умолчанию это отказ прогона, и снять их — ход держателя.
 * Свойство {@code -Dsmoke.foreign-strategies=tolerate} отказ снимает —
 * ценой того, что вход дыма может быть закрыт чужой сделкой, а счёт журнала и
 * агрегатов несёт чужие строки; выбирает держатель.
 *
 * <p>Свойства прогона: {@code smoke.instrument} — биржевое имя инструмента дыма
 * (умолчание {@value #DEFAULT_INSTRUMENT}); {@code smoke.margin-mode} — режим
 * маржи пары; {@code smoke.timeout.exchange}, {@code smoke.timeout.reception},
 * {@code smoke.timeout.recompute} — потолки ожидания следа (ISO-8601).
 */
final class SmokeRun {

    /** Инструмент дыма по умолчанию: на стенде у него есть ряды и плечо пары. */
    static final String DEFAULT_INSTRUMENT = "ETH-USDT-SWAP";

    /** Терминальные статусы сделки (docs/lifecycles/Deal.md). */
    static final List<String> TERMINAL_DEAL = List.of("CLOSED", "EMERGENCY_CLOSED");

    private static final String TOLERATE = "tolerate";

    /** Классы ручного вмешательства инструментного радиуса — слова провода ядра (docs/rules/manual-halt.md). */
    private static final String FULL_HALT = "FULL";

    private static final String SOFT_HALT = "SOFT";

    private static final Integer INSTRUMENT_PAGE = 1000;

    private static SmokeRun instance;

    private final HolderToken holder = new HolderToken();

    private final Perimeter perimeter = new Perimeter(holder);

    private final Instant startedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private OkxReadChannel okx;

    private String tenant;

    private String account;

    private Integer accountCountAtStart;

    private String instrument;

    private Boolean preconditionsMet;

    private JsonNode configAtStart;

    private JsonNode leverageAtStart;

    private Integer leverage;

    /** Закрытых сделок счёта в сделочном зерне за сутки прогона — до первого хода. */
    private Long closedDealsAtStart;

    /** Определение и сделка заявки, стоящей в книге ({@code E4.1}-{@code E4.5}). */
    private String restingDefinition;

    private String restingDeal;

    /** Клиентский идентификатор заявки дыма, найденный ключом прогона ({@code E4.3}). */
    private String restingClientOrderId;

    /** Тождество счёта ключа прогона и счёта системы предъявлено ({@code E4.3}). */
    private Boolean accountIdentityProven = Boolean.FALSE;

    /** Определение и сделка рыночного входа ({@code E5.1}-{@code E5.3}). */
    private String marketDefinition;

    private String marketDeal;

    private SmokeRun() {
    }

    /** Прогон этой JVM. */
    static synchronized SmokeRun get() {
        if (isNull(instance)) {
            instance = new SmokeRun();
        }
        return instance;
    }

    HolderToken holder() {
        return holder;
    }

    Perimeter perimeter() {
        return perimeter;
    }

    Instant startedAt() {
        return startedAt;
    }

    /** Канал чтения площадки; открывается первым обращением — с проверкой прав ключа. */
    synchronized OkxReadChannel okx() {
        if (isNull(okx)) {
            okx = OkxReadChannel.open();
        }
        return okx;
    }

    /** Тенант предъявителя — из контекста периметра. */
    String tenant() {
        return tenant(false);
    }

    /** Тенант предъявителя; {@code refresh} — прочитать контекст заново. */
    synchronized String tenant(Boolean refresh) {
        if (isNull(tenant) || isTrue(refresh)) {
            Reply context = perimeter.get("/api/v1/bff/context");
            require(context.status() == 200, "контекст тенанта не прочитан: " + context);
            String read = context.json().path("tenantId").asString("");
            require(isFalse(read.isBlank()), "контекст без тенанта: " + context);
            if (isNull(tenant)) {
                tenant = read;
            }
            return read;
        }
        return tenant;
    }

    /** Путь реестра счетов тенанта. */
    String accountsPath() {
        return "/api/v1/auth/exchange-accounts/tenant/" + tenant();
    }

    /** Биржевой счёт дыма — единственный счёт OKX контура {@code DEMO} тенанта. */
    synchronized String account() {
        if (isNull(account)) {
            Reply accounts = perimeter.get(accountsPath());
            require(accounts.status() == 200, "реестр счетов не прочитан: " + accounts);
            List<JsonNode> demo = new ArrayList<>();
            accounts.json().forEach(row -> {
                if ("OKX".equals(row.path("exchangeCode").asString(""))
                        && "DEMO".equals(row.path("contour").asString(""))
                        && "ACTIVE".equals(row.path("status").asString(""))) {
                    demo.add(row);
                }
            });
            require(demo.size() == 1, "у тенанта не ровно один действующий счёт OKX контура DEMO: " + accounts);
            account = demo.getFirst().path("internalId").asString();
            accountCountAtStart = accounts.json().size();
        }
        return account;
    }

    /** Число счетов тенанта до первого хода прогона. */
    Integer accountCountAtStart() {
        account();
        return accountCountAtStart;
    }

    /** Биржевое имя инструмента дыма. */
    String externalInstrument() {
        return System.getProperty("smoke.instrument", DEFAULT_INSTRUMENT);
    }

    /** Режим маржи пары дыма в словаре площадки. */
    String marginMode() {
        return System.getProperty("smoke.margin-mode", "isolated");
    }

    /** Идентичность инструмента дыма в каталоге {@code market-data}; пусто — инструмента в каталоге нет. */
    synchronized String instrument() {
        if (isNull(instrument)) {
            JsonNode found = findInstrument();
            require(nonNull(found), "инструмента " + externalInstrument() + " в каталоге market-data нет");
            instrument = found.path("internalId").asString();
        }
        return instrument;
    }

    /**
     * Строка каталога {@code market-data} по биржевому имени инструмента дыма —
     * обходом окон листинга.
     *
     * @return строка либо {@code null}
     */
    JsonNode findInstrument() {
        String after = "";
        while (true) {
            Reply page = perimeter.get("/api/v1/market-data/instruments?limit=" + INSTRUMENT_PAGE
                    + (after.isEmpty() ? "" : "&after=" + after));
            require(page.status() == 200, "каталог инструментов не прочитан: " + page);
            for (JsonNode row : page.json()) {
                if (Objects.equals(externalInstrument(), row.path("externalId").asString(""))) {
                    return row;
                }
            }
            if (page.json().size() < INSTRUMENT_PAGE) {
                return null;
            }
            after = page.json().get(page.json().size() - 1).path("internalId").asString();
        }
    }

    /**
     * Общие предусловия дыма (.claude/tests/cases/smoke-live.md §«Предусловия
     * дыма — что он обязан застать»), проверяемые один раз до первого хода;
     * снимок режимов счёта и плеча снимается тем же ходом.
     *
     * <p><b>Строка «поды всех восьми единиц живы» читается не пробой живости</b>
     * — пробы снаружи окружения не маршрутизируются, — а ответом каждого
     * владельца через периметр: отказ {@code 503} есть недоступный владелец.
     * Коннектор снаружи недостижим вовсе; его живость предъявляет первое же
     * чтение ядра, идущее через него.
     */
    synchronized void requireCommonPreconditions() {
        if (nonNull(preconditionsMet)) {
            require(preconditionsMet, "общие предусловия не выполнены — см. первый отказ прогона");
            return;
        }
        preconditionsMet = Boolean.FALSE;
        Stand.requireHostResolves();
        Reply anonymous = perimeter.call("GET", "/api/v1/bff/context", null, null);
        require(anonymous.status() == 401, "имя хоста не маршрутизирует /api/v1 на периметр: " + anonymous);
        tenant();
        account();
        OkxReadChannel channel = okx();
        configAtStart = channel.accountConfig();
        leverageAtStart = channel.leverage(externalInstrument(), marginMode());
        require(instrument().length() > 0, "инструмент дыма не разрешён");

        Reply checks = perimeter.get("/api/v1/trading-core/pair-checks?tenantInternalId=" + tenant()
                + "&exchangeAccountInternalId=" + account() + "&instrumentInternalId=" + instrument());
        require(checks.status() == 200 && checks.json().path("accountFound").asBoolean(false)
                && checks.json().path("accountBelongsToTenant").asBoolean(false)
                && checks.json().path("instrumentFound").asBoolean(false),
                "проекции счёта и инструмента у ядра не сняты: " + checks);
        Reply fee = perimeter.post("/api/v1/trading-core/jobs/trade-fee-rates", null);
        require(fee.status() == 202, "тик ставки комиссии не принят: " + fee);
        JsonNode appetite = perimeter.get("/api/v1/trading-core/risk-appetite").json();
        appetite.properties().forEach(entry -> require(isFalse(entry.getValue().isNull()),
                "число риск-аппетита не проставлено: " + entry.getKey()));
        leverage = leverageToAssign(appetite);
        Reply pair = perimeter.put("/api/v1/trading-core/pair-settings/" + account() + "/" + instrument(),
                "{\"leverage\": " + leverage + "}");
        require(pair.status() == 200 && Objects.equals(leverage, pair.json().path("leverage").asInt(0)),
                "плечо пары дыма не назначено: " + pair);
        require("ISOLATED".equals(pair.json().path("marginMode").asString("")),
                "режим маржи пары дыма не изолированный: " + pair);
        Reply rules = perimeter.get("/api/v1/market-data/instruments/" + instrument() + "/rules");
        require(rules.status() == 200 && "LIVE".equals(rules.json().path("status").asString("")),
                "инструмент дыма не торгуем на площадке: " + rules);

        require(liveDeals().isEmpty(), "на счёте есть живая сделка — живая сделка на счёте одна,"
                + " и вход дыма она закрывает: " + liveDeals());
        requireNoForeignStrategies();
        require(channel.pendingOrders(externalInstrument()).isEmpty(), "на счёте ключа есть открытые заявки по "
                + externalInstrument());
        require(channel.pendingAlgoOrders(externalInstrument()).isEmpty(), "на счёте ключа есть условные заявки по "
                + externalInstrument());
        require(channel.openPositions(externalInstrument()).isEmpty(), "на счёте ключа есть позиция по "
                + externalInstrument());
        JsonNode safety = safetyState();
        require("ACTIVE".equals(safety.path("accountSafetyRung").asString(""))
                        && isFalse(safety.path("standingInstrumentRungs").has(instrument())),
                "на счёте либо паре дыма стои́т ступень защиты: " + safety);
        closedDealsAtStart = closedDeals(aggregates("DEAL"));
        preconditionsMet = Boolean.TRUE;
    }

    /** Закрытых сделок счёта в сделочном зерне до первого хода. */
    Long closedDealsAtStart() {
        return closedDealsAtStart;
    }

    /** Сумма закрытых сделок счёта дыма по строкам сделочного зерна. */
    Long closedDeals(JsonNode page) {
        Long sum = 0L;
        for (JsonNode row : page.path("dealRows")) {
            if (Objects.equals(account(), row.path("exchangeAccountInternalId").asString(""))) {
                sum += row.path("closedDeals").asLong(0);
            }
        }
        return sum;
    }

    /** Снимок {@code GET /api/v5/account/config} до первого хода. */
    JsonNode configAtStart() {
        return configAtStart;
    }

    /** Снимок {@code GET /api/v5/account/leverage-info} по инструменту дыма до первого хода. */
    JsonNode leverageAtStart() {
        return leverageAtStart;
    }

    /** Сделки счёта поверхностью ядра. */
    JsonNode deals() {
        Reply deals = perimeter.get("/api/v1/trading-core/deals?exchangeAccountInternalId=" + account());
        require(deals.status() == 200, "сделки счёта не прочитаны: " + deals);
        return deals.json();
    }

    /** Подпись сделок счёта — «идентичность:статус» каждой, для сверки «следа нет». */
    List<String> dealSignatures() {
        List<String> signatures = new ArrayList<>();
        deals().forEach(deal -> signatures.add(deal.path("internalId").asString() + ":"
                + deal.path("status").asString()));
        return signatures.stream().sorted().collect(Collectors.toList());
    }

    /** Нетерминальные сделки счёта. */
    List<JsonNode> liveDeals() {
        List<JsonNode> live = new ArrayList<>();
        deals().forEach(deal -> {
            if (isFalse(TERMINAL_DEAL.contains(deal.path("status").asString("")))) {
                live.add(deal);
            }
        });
        return live;
    }

    /** Сделка по идентичности. */
    JsonNode deal(String internalId) {
        Reply deal = perimeter.get("/api/v1/trading-core/deals/" + internalId);
        require(deal.status() == 200, "сделка " + internalId + " не прочитана: " + deal);
        return deal.json();
    }

    /** Состояние защиты счёта. */
    JsonNode safetyState() {
        Reply state = perimeter.get("/api/v1/trading-core/safety/states/" + account());
        require(state.status() == 200, "состояние защиты не прочитано: " + state);
        return state.json();
    }

    /**
     * Заводит определение дыма и активирует его, затем подаёт реле
     * владельца определений.
     *
     * @param body тело определения
     * @return идентичность определения
     */
    String activateDefinition(String body) {
        Reply created = perimeter.post("/api/v1/strategies", body);
        require(created.status() == 201, "определение дыма не заведено: " + created);
        String definition = created.json().path("internalId").asString();
        Reply activated = perimeter.put("/api/v1/strategies/" + definition + "/status", "{\"status\": \"ACTIVE\"}");
        require(activated.status() == 200, "определение дыма не активировано: " + activated);
        perimeter.post("/api/v1/strategies/jobs/outbox-relay", null);
        return definition;
    }

    /** Переводит определение в неактивный статус — снятие его следа прогоном. */
    Reply deactivateDefinition(String definition) {
        return perimeter.put("/api/v1/strategies/" + definition + "/status", "{\"status\": \"INACTIVE\"}");
    }

    /**
     * Полная ступень инструментного радиуса на пару дыма — единственный ход,
     * которым дым сворачивает риск (.claude/tests/cases/smoke-live.md, кейсы
     * {@code E4.5}, {@code E5.3}, {@code E7.1}).
     */
    Reply fullHalt() {
        return perimeter.post("/api/v1/trading-core/safety/halts", haltBody(FULL_HALT));
    }

    /**
     * Обратный ход полной ступени до рабочего состояния пары — ДВА хода, а не
     * один: жёсткая ступень снимается только в мягкую своего радиуса, и
     * рабочее состояние возвращает второй, осознанный ход — снятие мягкой
     * (docs/rules/manual-halt.md). Ступень названа явно у каждого хода,
     * поэтому повтор на уже снятой ступени холостой, а не шаг по лестнице.
     */
    Reply pairHaltClearance() {
        Reply hard = perimeter.post("/api/v1/trading-core/safety/halt-clearances", haltBody(FULL_HALT));
        if (hard.status() != 204) {
            return hard;
        }
        return perimeter.post("/api/v1/trading-core/safety/halt-clearances", haltBody(SOFT_HALT));
    }

    /** Подаёт проходы оркестратора и реле ядра, пока условие не станет истинным. */
    void driveUntil(String what, Duration ceiling, Supplier<Boolean> condition) {
        await(what).atMost(ceiling).pollInterval(Duration.ofSeconds(15)).until(() -> {
            perimeter.post("/api/v1/trading-core/jobs/deal-orchestrator", null);
            perimeter.post("/api/v1/trading-core/jobs/outbox-relay", null);
            return isTrue(condition.get());
        });
    }

    /**
     * Подаёт реле ядра и владельца определений, пока условие не станет
     * истинным: след в журнале ждётся за публикацией, а приём {@code audit}
     * идёт сам.
     */
    void relayUntil(String what, Duration ceiling, Supplier<Boolean> condition) {
        await(what).atMost(ceiling).pollInterval(Duration.ofSeconds(15)).until(() -> {
            perimeter.post("/api/v1/trading-core/jobs/outbox-relay", null);
            perimeter.post("/api/v1/strategies/jobs/outbox-relay", null);
            return isTrue(condition.get());
        });
    }

    /** Живая сделка счёта по инструменту дыма; {@code null} — её нет. */
    JsonNode liveSmokeDeal() {
        return liveDeals().stream()
                .filter(deal -> Objects.equals(instrument(), deal.path("instrumentInternalId").asString("")))
                .findFirst()
                .orElse(null);
    }

    /**
     * Идентичность заявки сделки из строки журнала о решении по заявке —
     * она же клиентский идентификатор заявки на площадке
     * (docs/models/mapping/Order.md: {@code clOrdId = internalId}). Поверхность
     * ядра заявок не отдаёт: строка журнала — единственный внешний носитель.
     *
     * @return идентичность заявки либо {@code null}, пока строки нет
     */
    String orderOf(String dealInternalId) {
        List<JsonNode> decided = journal(startedAt).of("ORDER_DECIDED", dealInternalId);
        return decided.isEmpty() ? null : decided.getLast().path("content").path("orderInternalId").asString(null);
    }

    /** Ждёт без подачи ходов — след сторон, чьих фасадов нет. */
    void awaitTrace(String what, Duration ceiling, Supplier<Boolean> condition) {
        await(what).atMost(ceiling).pollInterval(Duration.ofSeconds(30)).until(() -> isTrue(condition.get()));
    }

    /**
     * Страницы журнала тенанта окном {@code [from, now]} по счёту дыма.
     *
     * @param from начало окна
     * @return записи, новые сначала, и полнота последней страницы
     */
    JournalRead journal(Instant from) {
        List<JsonNode> records = new ArrayList<>();
        JsonNode completeness = null;
        String cursor = "";
        String base = "/api/v1/audit/journal/records?from=" + iso(from) + "&to=" + iso(Instant.now())
                + "&exchangeAccountInternalId=" + account();
        while (true) {
            Reply page = perimeter.get(base + cursor);
            require(page.status() == 200, "журнал не прочитан: " + page);
            page.json().path("records").forEach(records::add);
            completeness = page.json().path("completeness");
            JsonNode next = page.json().path("nextCursor");
            if (next.isNull() || next.isMissingNode()) {
                return new JournalRead(records, completeness);
            }
            cursor = "&cursorOccurredAt=" + encodedIso(next.path("occurredAt").asString())
                    + "&cursorEventId=" + next.path("eventId").asString();
        }
    }

    /**
     * Строки агрегатов сделочного либо происшественного зерна за сутки прогона.
     *
     * @param grain {@code DEAL} либо {@code INCIDENT}
     * @return страница выдачи
     */
    JsonNode aggregates(String grain) {
        String day = startedAt.atOffset(ZoneOffset.UTC).toLocalDate().toString();
        String today = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().toString();
        Reply page = perimeter.get("/api/v1/statistics/aggregates/rows?grain=" + grain + "&from=" + day + "&to=" + today);
        require(page.status() == 200, "агрегаты не прочитаны: " + page);
        return page.json();
    }

    /** Потолок ожидания следа площадки и оркестратора. */
    Duration exchangeTimeout() {
        return Duration.parse(System.getProperty("smoke.timeout.exchange", "PT5M"));
    }

    /** Потолок ожидания приёма журнала: реле ядра и владельца определений плюс приём {@code audit}. */
    Duration receptionTimeout() {
        return Duration.parse(System.getProperty("smoke.timeout.reception", "PT5M"));
    }

    /**
     * Потолок ожидания такта пересчёта агрегатов: пересчёт идёт по расписанию
     * окружения раз в час ({@code JOB_AGGREGATE_RECOMPUTE_CRON}), и ручного
     * фасада у него нет намеренно.
     */
    Duration recomputeTimeout() {
        return Duration.parse(System.getProperty("smoke.timeout.recompute", "PT75M"));
    }

    String restingDefinition() {
        return restingDefinition;
    }

    void restingDefinition(String value) {
        restingDefinition = value;
    }

    String restingDeal() {
        return restingDeal;
    }

    void restingDeal(String value) {
        restingDeal = value;
    }

    String restingClientOrderId() {
        return restingClientOrderId;
    }

    void restingClientOrderId(String value) {
        restingClientOrderId = value;
    }

    Boolean accountIdentityProven() {
        return accountIdentityProven;
    }

    void accountIdentityProven(Boolean value) {
        accountIdentityProven = value;
    }

    String marketDefinition() {
        return marketDefinition;
    }

    void marketDefinition(String value) {
        marketDefinition = value;
    }

    String marketDeal() {
        return marketDeal;
    }

    void marketDeal(String value) {
        marketDeal = value;
    }

    /**
     * Проверка предусловия: ложь — отказ прогона с названной причиной.
     *
     * @throws IllegalStateException условие ложно
     */
    static void require(Boolean condition, String refusal) {
        if (isFalse(condition)) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: " + refusal);
        }
    }

    /** Число узла — числом провода либо строкой (поля правил инструмента едут строками). */
    static BigDecimal decimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asString());
    }

    private void requireNoForeignStrategies() {
        Reply definitions = perimeter.get("/api/v1/strategies");
        require(definitions.status() == 200, "определения стратегий не прочитаны: " + definitions);
        List<String> foreign = new ArrayList<>();
        List<String> onPair = new ArrayList<>();
        definitions.json().forEach(definition -> {
            if ("ACTIVE".equals(definition.path("status").asString(""))
                    && Objects.equals(account(), definition.path("exchangeAccountInternalId").asString(""))) {
                String described = definition.path("internalId").asString() + " ("
                        + definition.path("instrumentInternalId").asString() + ")";
                foreign.add(described);
                if (Objects.equals(instrument(), definition.path("instrumentInternalId").asString(""))) {
                    onPair.add(described);
                }
            }
        });
        require(onPair.isEmpty(), "на паре дыма уже активно определение — активация дыма будет отвергнута;"
                + " снять его — ход держателя: " + onPair);
        require(foreign.isEmpty() || TOLERATE.equals(System.getProperty("smoke.foreign-strategies", "")),
                "на счёте активны чужие определения — они откроют сделку посреди прогона; снять их — ход"
                        + " держателя, либо -Dsmoke.foreign-strategies=tolerate: " + foreign);
    }

    /**
     * Плечо, назначаемое паре: то, что уже стои́т на площадке, — тогда
     * назначение режимов счёта не меняет и возвращать нечего ({@code E7.2}).
     * Выше предела риск-аппетита — отказ прогона: назначить другое значило бы
     * менять счёт и возвращать его.
     */
    private Integer leverageToAssign(JsonNode appetite) {
        JsonNode row = leverageAtStart.isArray() && leverageAtStart.size() > 0 ? leverageAtStart.get(0) : null;
        require(nonNull(row), "площадка не отдала плечо по " + externalInstrument() + " в режиме " + marginMode());
        Integer onExchange = new BigDecimal(row.path("lever").asString("0")).intValue();
        Integer ceiling = appetite.path("globalMaxLeverage").asInt(0);
        require(onExchange > 0 && onExchange <= ceiling, "плечо пары на площадке (" + onExchange
                + ") вне предела риск-аппетита (" + ceiling + ") — выставить его — ход держателя");
        return onExchange;
    }

    private String haltBody(String haltClass) {
        return "{\"haltClass\": \"%s\", \"exchangeAccountInternalId\": \"%s\", \"instrumentInternalId\": \"%s\"}"
                .formatted(haltClass, account(), instrument());
    }

    private static String iso(Instant moment) {
        return encodedIso(moment.truncatedTo(ChronoUnit.SECONDS).toString());
    }

    private static String encodedIso(String moment) {
        return moment.replace("+", "%2B").replace(":", "%3A");
    }

    /**
     * Чтение журнала.
     *
     * @param records      записи окна, новые сначала
     * @param completeness полнота приёма, едущая со страницей
     */
    record JournalRead(List<JsonNode> records, JsonNode completeness) {

        /** Записи названного класса по сделке. */
        List<JsonNode> of(String eventType, String dealInternalId) {
            return records.stream()
                    .filter(row -> Objects.equals(eventType, row.path("eventType").asString("")))
                    .filter(row -> isNull(dealInternalId)
                            || Objects.equals(dealInternalId, row.path("dealInternalId").asString("")))
                    .collect(Collectors.toList());
        }
    }
}
