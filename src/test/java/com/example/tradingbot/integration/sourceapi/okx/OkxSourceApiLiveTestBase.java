package com.example.tradingbot.integration.sourceapi.okx;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * База живых код-тестов контура тестов API источника OKX
 * (`.claude/skills/test-code.md`, `.claude/decisions/source-api-target-rebase.md`).
 *
 * <p>Исполнитель прогона: каждый кейс плана бьёт в тот же единственный
 * generic-эндпоинт {@code POST /api/proxy/okx/raw}, что и Postman-коллекция
 * (механика 1:1), и ассертит **сырые поля JSON ответа OKX** ({@code b.code},
 * {@code b.data[0].sCode}, {@code b.data[0].<field>}). Подпись/креды — на
 * стороне поднятого app (signing interceptor), demo/non-prod — профилем
 * {@code test}. Контекст Spring Boot кэшируется между классами; профиль-гейт —
 * структурный no-prod (на prod кейсы пропускаются).
 *
 * <p><b>Throttle + rate-limit.</b> Все обращения к бирже идут воронкой через
 * {@link #raw}. Перед каждым запросом — пауза-троттл (пол {@link #throttle},
 * дефолт {@link #DEFAULT_THROTTLE}=1с, per-case override полем {@code throttle}),
 * измеряется глобально (статический {@link #LAST_REQUEST_NANOS}), поэтому
 * покрывает и стык между тестами — отдельная между-тестовая пауза не нужна.
 * Rate-limit-ответ OKX (HTTP 429 / {@link #RATE_LIMIT_CODES}) → экспоненциальный
 * backoff + повтор. Троттл — единственный пейсер поллинга: {@link #pollUntil}
 * не добавляет свой фикс-интервал поверх (только anti-spin floor), чтобы троттл
 * и poll-таймаут не складывались наивно.
 *
 * <p><b>Осадка (poll).</b> Адаптивный поллинг до {@link #pollTimeout} (дефолт
 * {@link #DEFAULT_POLL_TIMEOUT}=25с, per-case override полем {@code pollTimeout}
 * либо аргументом {@link #waitUntil(String, Duration, BooleanSupplier)}).
 * Троттл (≥1с) и poll-таймаут (≥25с) — два разных per-case числа, разные цели.
 *
 * <p><b>Модель безопасности (sweep + halt).</b> Инвариант восстановления
 * каждого stateful-кейса проверяет {@link #assertRestoredOrHalt} — одинаково
 * для сущностей (ордера/позиции/algo) и настроек (acctLv/leverage/posMode):
 * невозврат к старту → <b>жёсткий фейл</b> (не глушится) + <b>принудительный
 * sweep</b> (снять/закрыть сущность; настройку — re-set к снапшоту) + если
 * sweep не вычистил → <b>halt</b> прогона (последующие кейсы абортятся).
 * Находка C3 логируется <b>в любом случае</b>. Teardown-хелперы {@code *Quietly}
 * — первая попытка sweep'а (глушат ошибки); авторитет «вернулось ли» —
 * {@code assertRestoredOrHalt}. Halt осмысленен только при <b>детерминированном
 * порядке прогона</b>: классы и методы упорядочены {@code @Order} по структуре
 * плана (M1–M21 → TG → AG → MG → PG; кейсы внутри эндпоинта — по плану), а
 * орендереры включены в {@code src/test/resources/junit-platform.properties}.
 * «Дальше не идём» = всё, что после в этом порядке.
 *
 * <p><b>Формат ошибки.</b> Каждый путь фейла несёт контекст: кейс
 * ({@link #currentCaseId}, метка плана) + запрос к бирже ({@link RawRequest},
 * протянут в {@link RawResponse}) + ошибку. См. {@link #ctx(RawResponse)}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("source-api-live")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class OkxSourceApiLiveTestBase {

    protected static final String INST_ID = "ETH-USDT-SWAP";
    protected static final String INST_TYPE = "SWAP";
    protected static final String INST_FAMILY = "ETH-USDT";
    /** Индекс (не торговый инструмент) для index-* эндпоинтов. */
    protected static final String INDEX_INST_ID = "ETH-USDT";
    /** Минимальный размер ордера (spec ETH-USDT-SWAP: minSz=lotSz=0.01). */
    protected static final String MIN_SZ = "0.01";

    protected static final boolean SIGNED = true;
    protected static final boolean PUBLIC = false;

    /** Дефолтная пауза-троттл между запросами к бирже (per-case override полем {@code throttle}). */
    protected static final Duration DEFAULT_THROTTLE = Duration.ofSeconds(1);
    /** Дефолтный таймаут осадки/Verify.end (per-case override полем {@code pollTimeout}). */
    protected static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofSeconds(25);

    // Сырые пути OKX, общие для write-цепочек CORE/gap-групп.
    protected static final String TICKER_PATH = "/api/v5/market/ticker";
    protected static final String ORDER_PATH = "/api/v5/trade/order";
    protected static final String CANCEL_ORDER_PATH = "/api/v5/trade/cancel-order";
    protected static final String ORDERS_PENDING_PATH = "/api/v5/trade/orders-pending";
    protected static final String ORDERS_HISTORY_PATH = "/api/v5/trade/orders-history";
    protected static final String FILLS_PATH = "/api/v5/trade/fills";
    protected static final String FILLS_HISTORY_PATH = "/api/v5/trade/fills-history";
    protected static final String POSITIONS_PATH = "/api/v5/account/positions";
    protected static final String CLOSE_POSITION_PATH = "/api/v5/trade/close-position";
    protected static final String ORDER_ALGO_PATH = "/api/v5/trade/order-algo";
    protected static final String ALGO_PENDING_PATH = "/api/v5/trade/orders-algo-pending";
    protected static final String ALGO_HISTORY_PATH = "/api/v5/trade/orders-algo-history";
    protected static final String CANCEL_ALGOS_PATH = "/api/v5/trade/cancel-algos";
    protected static final String CANCEL_ADVANCE_ALGOS_PATH = "/api/v5/trade/cancel-advance-algos";
    protected static final String ACCOUNT_CONFIG_PATH = "/api/v5/account/config";

    private static final String RAW_ENDPOINT = "/api/proxy/okx/raw";
    private static final BigDecimal TICK = new BigDecimal("0.01");
    private static final long POLL_FLOOR_MILLIS = 50L;
    private static final int MAX_RATE_LIMIT_RETRIES = 4;
    private static final long MAX_BACKOFF_MILLIS = 16_000L;
    /** OKX rate-limit / system-busy коды (наряду с HTTP 429) → backoff+retry. */
    private static final Set<String> RATE_LIMIT_CODES = Set.of("50011", "50013", "50061");
    private static final AtomicLong ID_SEQ = new AtomicLong();
    private static final AtomicLong LAST_REQUEST_NANOS = new AtomicLong(0L);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Halt-флаг прогона (статический): выставляется при невычищаемом грязном состоянии. */
    private static volatile boolean halted = false;
    private static volatile String haltReason = null;

    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final ObjectMapper json = new ObjectMapper();

    /** Пауза-троттл текущего кейса (per-case override; сбрасывается в {@code @BeforeEach}). */
    protected Duration throttle = DEFAULT_THROTTLE;
    /** Таймаут осадки текущего кейса (per-case override; сбрасывается в {@code @BeforeEach}). */
    protected Duration pollTimeout = DEFAULT_POLL_TIMEOUT;
    /** Текущая метка кейса/шага — протягивается в сообщения ошибок. */
    private String currentCaseId;

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @BeforeEach
    void resetPerCase(TestInfo testInfo) {
        throttle = DEFAULT_THROTTLE;
        pollTimeout = DEFAULT_POLL_TIMEOUT;
        currentCaseId = testInfo.getDisplayName();
        if (halted) {
            Assumptions.abort("RUN HALTED — " + haltReason
                    + " — not proceeding (unrecoverable dirty exchange state from a prior case)");
        }
        Assumptions.assumeFalse(List.of(environment.getActiveProfiles()).contains("prod"),
                "source-api-live tests are structurally disabled on the prod profile");
    }

    // ---------------------------------------------------------------------
    // raw passthrough — конверт {method, path, query, body, signed} → /raw
    // (throttle-gate + rate-limit retry)
    // ---------------------------------------------------------------------

    /**
     * Отправляет конверт в {@code POST /api/proxy/okx/raw} с троттлом и
     * retry-on-429 и возвращает сырой ответ (HTTP-статус + распарсенный конверт
     * OKX + породивший {@link RawRequest}). {@code query}/{@code body}
     * опускаются, если пусты/null.
     */
    protected RawResponse raw(String method, String path, Map<String, Object> query, Object body, boolean signed) {
        RawRequest req = new RawRequest(method, path, query, body, signed);
        String payload = serialize(req);
        long backoffMillis = throttle.toMillis();
        RawResponse last = null;
        for (int attempt = 0; attempt <= MAX_RATE_LIMIT_RETRIES; attempt++) {
            throttleGate();
            last = send(req, payload);
            if (!isRateLimited(last)) {
                return last;
            }
            if (attempt < MAX_RATE_LIMIT_RETRIES) {
                log.warn("[throttle] {} rate-limited (http={}, b.code={}) — backoff {}ms, retry {}/{}",
                        ctx(req), last.status(), last.code(), backoffMillis, attempt + 1, MAX_RATE_LIMIT_RETRIES);
                sleepMillis(backoffMillis);
                backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
            }
        }
        log.warn("[throttle] {} still rate-limited after {} retries — returning last response",
                ctx(req), MAX_RATE_LIMIT_RETRIES);
        return last;
    }

    protected RawResponse get(String path, Map<String, Object> query, boolean signed) {
        return raw("GET", path, query, null, signed);
    }

    protected RawResponse post(String path, Object body, boolean signed) {
        return raw("POST", path, null, body, signed);
    }

    /** Гейт троттла: спит остаток {@link #throttle} с момента прошлого запроса к бирже. */
    private void throttleGate() {
        long last = LAST_REQUEST_NANOS.get();
        if (last != 0L) {
            long waitNanos = throttle.toNanos() - (System.nanoTime() - last);
            if (waitNanos > 0L) {
                sleepNanos(waitNanos);
            }
        }
        LAST_REQUEST_NANOS.set(System.nanoTime());
    }

    private String serialize(RawRequest req) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("method", req.method());
        envelope.put("path", req.path());
        if (req.query() != null && !req.query().isEmpty()) {
            envelope.put("query", req.query());
        }
        if (req.body() != null) {
            envelope.put("body", req.body());
        }
        envelope.put("signed", req.signed());
        try {
            return json.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new SourceApiException(ctx(req) + " → failed to serialize /raw envelope: " + e.getMessage(), e);
        }
    }

    private RawResponse send(RawRequest req, String payload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + RAW_ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return new RawResponse(response.statusCode(), parse(response.body()), response.body(), req);
        } catch (IOException e) {
            throw new SourceApiException(ctx(req) + " → I/O error calling /raw: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceApiException(ctx(req) + " → interrupted calling /raw", e);
        }
    }

    /**
     * Rate-limited ли ответ: HTTP 429 или OKX rate-limit/квота-код
     * ({@link #RATE_LIMIT_CODES}). Используется и внутренним retry-backoff
     * {@link #raw}, и кейсами, для которых rate-limit/исчерпание квоты —
     * предусмотренный валидный исход (напр. AG5.1 bills-history-archive, лимит
     * 12 заявок/сутки): {@code raw()} уже исчерпал backoff-ретраи, поэтому
     * возвращённый rate-limit терминален.
     */
    protected boolean isRateLimited(RawResponse r) {
        // r.code() == null на ответах без top-level "code" (наш 5xx-эрор,
        // пустое тело). RATE_LIMIT_CODES — immutable Set.of(...), его
        // contains(null) бросает NPE, поэтому проверяем code на null.
        String code = r.code();
        return r.status() == 429 || (nonNull(code) && RATE_LIMIT_CODES.contains(code));
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return json.readTree(raw);
        } catch (JsonProcessingException e) {
            return MissingNode.getInstance();
        }
    }

    /** Строит {@code LinkedHashMap} из чередующихся пар ключ/значение. */
    protected Map<String, Object> map(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("map(...) requires an even number of arguments");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ---------------------------------------------------------------------
    // Контекст ошибки — кейс + запрос
    // ---------------------------------------------------------------------

    /** Переопределить метку текущего кейса/шага (для контекста ошибок), напр. {@code "M19tr.place"}. */
    protected void step(String label) {
        this.currentCaseId = label;
    }

    private String caseLabel() {
        return currentCaseId == null ? "?" : currentCaseId;
    }

    private String ctx(RawRequest req) {
        return "[case=" + caseLabel() + "] " + (req == null ? "(no request)" : req.describe());
    }

    /** Контекст ошибки: {@code [case=...] METHOD path query=... body=...}. */
    protected String ctx(RawResponse r) {
        return ctx(r == null ? null : r.request());
    }

    // ---------------------------------------------------------------------
    // Ассерты сырого JSON OKX (с контекстом кейс+запрос)
    // ---------------------------------------------------------------------

    protected void assertHttp200(RawResponse r) {
        assertThat(r.status()).as("%s → HTTP status", ctx(r)).isEqualTo(200);
    }

    /** Успех верхнего уровня: HTTP 200 + {@code b.code="0"}. */
    protected void assertOk(RawResponse r) {
        assertHttp200(r);
        assertThat(r.code()).as("%s → OKX b.code (msg=%s)", ctx(r), r.msg()).isEqualTo("0");
    }

    /** Поэлементный успех приёма write-ACK: {@code b.data[0].sCode="0"}. */
    protected void assertFirstElementOk(RawResponse r) {
        assertThat(r.sCode()).as("%s → OKX b.data[0].sCode (sMsg=%s)", ctx(r), r.sMsg()).isEqualTo("0");
    }

    /** Реджект верхнего уровня: HTTP 200 + {@code b.code≠"0"}. */
    protected void assertBusinessReject(RawResponse r) {
        assertHttp200(r);
        assertThat(r.code()).as("%s → OKX b.code (expected reject)", ctx(r)).isNotEqualTo("0");
    }

    /**
     * Реджект на любом уровне: {@code b.code≠"0"} ИЛИ {@code b.data[0].sCode≠"0"}.
     * Точный код — наблюдение (логируется), не выдумывается.
     */
    protected void assertRejectedAnyLevel(String caseId, RawResponse r) {
        assertHttp200(r);
        observe(caseId, r);
        assertThat(r.businessReject() || r.firstElementReject())
                .as("%s → expected reject at some level (b.code=%s, b.data[0].sCode=%s)",
                        ctx(r), r.code(), r.sCode())
                .isTrue();
    }

    /**
     * Реджект ИЛИ пустой {@code data} — для несущ. инструментов/фильтров вне
     * окна, где OKX может вернуть и реджект, и валидный пустой массив.
     */
    protected void assertRejectOrEmpty(String caseId, RawResponse r) {
        assertHttp200(r);
        observe(caseId, r);
        assertThat(r.businessReject() || r.dataEmpty())
                .as("%s → expected reject or empty data (b.code=%s, data.size=%s)",
                        ctx(r), r.code(), r.dataSize())
                .isTrue();
    }

    /** Логирует факт/наблюдение кейса (питает бланк отчёта RUN). */
    protected void observe(String caseId, RawResponse r) {
        log.info("[{}] OBSERVE {} → http={} b.code={} b.msg={} data.size={} data0.sCode={} data0.sMsg={}",
                caseId, r.request().describe(), r.status(), r.code(), r.msg(),
                r.dataSize(), r.sCode(), r.sMsg());
    }

    // ---------------------------------------------------------------------
    // Поллинг (wait-until-condition) — троттл-пейсинг, per-case таймаут
    // ---------------------------------------------------------------------

    /** Поллинг условия до {@link #pollTimeout}; по таймауту — {@link AssertionError} с контекстом. */
    protected void waitUntil(String description, BooleanSupplier condition) {
        waitUntil(description, pollTimeout, condition);
    }

    /** Поллинг условия до заданного таймаута (per-call override); по таймауту — {@link AssertionError}. */
    protected void waitUntil(String description, Duration timeout, BooleanSupplier condition) {
        PollOutcome outcome = pollUntil(condition, timeout);
        if (!outcome.satisfied()) {
            String detail = outcome.lastError() == null ? ""
                    : " | last assertion: " + outcome.lastError().getMessage();
            throw new AssertionError("[case=" + caseLabel() + "] wait-until-condition timed out after "
                    + timeout.toSeconds() + "s: " + description + detail, outcome.lastError());
        }
    }

    /** Поллит условие до таймаута, возвращая boolean (без исключения). */
    protected boolean pollUntilBool(BooleanSupplier condition, Duration timeout) {
        return pollUntil(condition, timeout).satisfied();
    }

    /**
     * Цикл опроса. Пейсинг даёт троттл внутри {@link #raw} (вызываемого
     * условием) — отдельный poll-интервал не добавляется поверх, только
     * anti-spin floor {@link #POLL_FLOOR_MILLIS}.
     */
    private PollOutcome pollUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        AssertionError lastError = null;
        while (true) {
            try {
                if (condition.getAsBoolean()) {
                    return new PollOutcome(true, null);
                }
            } catch (AssertionError e) {
                lastError = e;
            }
            if (System.nanoTime() >= deadline) {
                return new PollOutcome(false, lastError);
            }
            sleepMillis(POLL_FLOOR_MILLIS);
        }
    }

    // ---------------------------------------------------------------------
    // Модель безопасности — sweep + halt (инвариант восстановления)
    // ---------------------------------------------------------------------

    /**
     * Verify.end с моделью sweep+halt — единый для сущностей и настроек.
     * Вернулось к старту ({@code restored}) → ок. Иначе: находка C3 (всегда) →
     * принудительный {@code forceSweep} (снять/закрыть сущность; настройку —
     * re-set к снапшоту) → если после sweep всё ещё грязно → {@link #halt} +
     * fail; если sweep вычистил → всё равно fail (невозврат — это фейл).
     *
     * @param caseId     метка кейса/шага (контекст ошибки)
     * @param what       что проверяется (для сообщения/находки)
     * @param restored   условие «вернулось к старту»
     * @param forceSweep принудительная очистка/восстановление
     */
    protected void assertRestoredOrHalt(String caseId, String what, BooleanSupplier restored, Runnable forceSweep) {
        step(caseId);
        if (pollUntilBool(restored, pollTimeout)) {
            return;
        }
        // Невозврат → находка C3 (всегда логируется), затем принудительный sweep.
        log.error("[{}] C3 FINDING: state did NOT return to start ({}) — non-return is a failure", caseId, what);
        try {
            forceSweep.run();
        } catch (RuntimeException | AssertionError e) {
            log.warn("[{}] forced sweep error: {}", caseId, e.getMessage());
        }
        boolean cleaned = pollUntilBool(restored, pollTimeout);
        if (!cleaned) {
            halt(caseId + ": unrecoverable dirty state after forced sweep (" + what + ")");
            Assertions.fail("[case=" + caseId + "] " + what
                    + " did NOT return to start; forced sweep FAILED → RUN HALTED (C3 finding logged)");
        }
        Assertions.fail("[case=" + caseId + "] " + what
                + " did NOT return to start; forced sweep cleaned it (non-return is a failure; C3 finding logged)");
    }

    /** Выставляет halt-флаг прогона: последующие кейсы абортятся в {@code @BeforeEach}. */
    protected void halt(String reason) {
        halted = true;
        haltReason = reason;
        log.error("RUN HALT triggered: {}", reason);
    }

    // ---------------------------------------------------------------------
    // Цена/идентификаторы — live с сырого ticker, минимальный риск
    // ---------------------------------------------------------------------

    /** live {@code last} с сырого {@code GET /market/ticker} (PUBLIC). */
    protected BigDecimal lastPrice() {
        RawResponse r = get(TICKER_PATH, map("instId", INST_ID), PUBLIC);
        assertOk(r);
        String last = r.d0().path("last").asText("");
        assertThat(last).as("%s → ticker last", ctx(r)).isNotBlank();
        return new BigDecimal(last);
    }

    /**
     * Цена, выровненная по {@code tickSz=0.01}: {@code floor(last·multiplier)}
     * вниз до кратного тику, строкой. Множитель {@code <1} — неисполнимая buy,
     * {@code >1} — далёкий TP/sell-триггер.
     */
    protected String tickPrice(BigDecimal last, double multiplier) {
        BigDecimal raw = last.multiply(BigDecimal.valueOf(multiplier));
        BigDecimal ticks = raw.divide(TICK, 0, RoundingMode.FLOOR);
        return ticks.multiply(TICK).setScale(2, RoundingMode.FLOOR).toPlainString();
    }

    protected String tickPrice(double multiplier) {
        return tickPrice(lastPrice(), multiplier);
    }

    /** Уникальный alphanumeric clOrdId/algoClOrdId (≤32, letters+digits). */
    protected String newId(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36) + ID_SEQ.incrementAndGet();
    }

    // ---------------------------------------------------------------------
    // A0-фикстура — условный open/close позиции при reduce-only без позиции
    // ---------------------------------------------------------------------

    /** Есть ли открытая позиция по инструменту ({@code pos≠0}). */
    protected boolean hasOpenPosition() {
        RawResponse r = get(POSITIONS_PATH, map("instType", INST_TYPE, "instId", INST_ID), SIGNED);
        if (!r.codeZero()) {
            return false;
        }
        JsonNode pos = r.d0().path("pos");
        if (pos.isMissingNode()) {
            return false;
        }
        String value = pos.asText("");
        return !value.isBlank() && new BigDecimal(value).signum() != 0;
    }

    /**
     * A0: открыть минимальную market-позицию (как Cmarket.place) и дождаться
     * её появления. Используется, когда reduce-only algo реджектится «нет
     * позиции».
     */
    protected void openMinMarketPosition() {
        RawResponse place = post(ORDER_PATH, map(
                "instId", INST_ID, "tdMode", "isolated", "side", "buy",
                "ordType", "market", "sz", MIN_SZ, "clOrdId", newId("a0"), "reduceOnly", false), SIGNED);
        assertOk(place);
        assertFirstElementOk(place);
        waitUntil("A0 min market position opened", this::hasOpenPosition);
    }

    /**
     * Идемпотентное закрытие позиции (первая попытка sweep'а в teardown):
     * close-position(autoCxl) + поллинг до flat. Ошибки гасятся — авторитет
     * «вернулось ли» у {@link #assertRestoredOrHalt}, не у этого хелпера.
     */
    protected void closePositionQuietly() {
        try {
            post(CLOSE_POSITION_PATH, map("instId", INST_ID, "mgnMode", "isolated",
                    "posSide", "net", "autoCxl", true, "ccy", "USDT"), SIGNED);
            waitUntil("position flat after teardown close", () -> !hasOpenPosition());
        } catch (RuntimeException | AssertionError e) {
            log.warn("closePositionQuietly swallowed: {}", e.getMessage());
        }
    }

    /** Идемпотентная отмена ordinary-algo по {@code algoId} (sweep-попытка), ошибки гасятся. */
    protected void cancelAlgoQuietly(String algoId) {
        if (algoId == null || algoId.isBlank()) {
            return;
        }
        try {
            post(CANCEL_ALGOS_PATH, List.of(map("instId", INST_ID, "algoId", algoId)), SIGNED);
        } catch (RuntimeException e) {
            log.warn("cancelAlgoQuietly swallowed: {}", e.getMessage());
        }
    }

    /**
     * Идемпотентная отмена advance-algo по {@code algoId} (sweep-попытка семьи
     * advance, И-2), ошибки гасятся. Эндпоинт {@code cancel-advance-algos}
     * выведен из офдока — фейл здесь = находка C3.
     */
    protected void cancelAdvanceAlgoQuietly(String algoId) {
        if (algoId == null || algoId.isBlank()) {
            return;
        }
        try {
            post(CANCEL_ADVANCE_ALGOS_PATH, List.of(map("instId", INST_ID, "algoId", algoId)), SIGNED);
        } catch (RuntimeException e) {
            log.warn("cancelAdvanceAlgoQuietly swallowed: {}", e.getMessage());
        }
    }

    /** Идемпотентная отмена ордера по {@code ordId} (sweep-попытка), ошибки гасятся. */
    protected void cancelOrderQuietly(String ordId) {
        if (ordId == null || ordId.isBlank()) {
            return;
        }
        try {
            post(CANCEL_ORDER_PATH, map("instId", INST_ID, "ordId", ordId), SIGNED);
        } catch (RuntimeException e) {
            log.warn("cancelOrderQuietly swallowed: {}", e.getMessage());
        }
    }

    /** Нет ни одного живого ордера по инструменту в orders-pending. */
    protected boolean noPendingOrders() {
        RawResponse r = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        return r.codeZero() && r.dataEmpty();
    }

    /** В orders-pending нет ордера с данным {@code ordId}. */
    protected boolean pendingHasNo(String ordId) {
        RawResponse r = get(ORDERS_PENDING_PATH, map("instId", INST_ID), SIGNED);
        return r.codeZero() && !containsField(r, "ordId", ordId);
    }

    /** В algo-pending данной семьи нет algo с данным {@code algoId}. */
    protected boolean algoPendingHasNo(String ordType, String algoId) {
        RawResponse r = get(ALGO_PENDING_PATH, map("instId", INST_ID, "ordType", ordType), SIGNED);
        return r.codeZero() && !containsField(r, "algoId", algoId);
    }

    /** Есть ли в {@code data} элемент, у которого {@code field == value}. */
    protected boolean containsField(RawResponse r, String field, String value) {
        if (value == null || !r.data().isArray()) {
            return false;
        }
        for (JsonNode element : r.data()) {
            if (value.equals(element.path(field).asText(null))) {
                return true;
            }
        }
        return false;
    }

    private void sleepMillis(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while throttling/polling", e);
        }
    }

    private void sleepNanos(long nanos) {
        if (nanos <= 0L) {
            return;
        }
        sleepMillis((nanos + 999_999L) / 1_000_000L);
    }

    // ---------------------------------------------------------------------
    // Модель запроса/ответа
    // ---------------------------------------------------------------------

    /** Породивший запрос (для контекста ошибок): method/path/query/body/signed. */
    protected record RawRequest(String method, String path, Map<String, Object> query, Object body, boolean signed) {

        String describe() {
            StringBuilder sb = new StringBuilder(method).append(' ').append(path);
            if (query != null && !query.isEmpty()) {
                sb.append(" query=").append(query);
            }
            if (body != null) {
                sb.append(" body=").append(body);
            }
            return sb.toString();
        }
    }

    /** Сырой ответ {@code /raw}: HTTP-статус + распарсенный конверт OKX + породивший запрос. */
    protected record RawResponse(int status, JsonNode body, String rawBody, RawRequest request) {

        public String code() {
            return text("code");
        }

        public String msg() {
            return text("msg");
        }

        public boolean codeZero() {
            return "0".equals(code());
        }

        public boolean businessReject() {
            return body != null && !body.isMissingNode() && !codeZero();
        }

        public JsonNode data() {
            return body == null ? MissingNode.getInstance() : body.path("data");
        }

        public boolean dataEmpty() {
            return !data().isArray() || data().isEmpty();
        }

        public int dataSize() {
            return data().isArray() ? data().size() : 0;
        }

        /** Элемент {@code data[0]} (MissingNode, если нет). */
        public JsonNode d0() {
            return data().path(0);
        }

        public JsonNode at(int index) {
            return data().path(index);
        }

        public String sCode() {
            return d0().path("sCode").asText("");
        }

        public String sMsg() {
            return d0().path("sMsg").asText("");
        }

        public boolean firstElementOk() {
            return "0".equals(sCode());
        }

        public boolean firstElementReject() {
            JsonNode sCode = d0().path("sCode");
            return !sCode.isMissingNode() && !"0".equals(sCode.asText());
        }

        private String text(String field) {
            return body == null ? null : body.path(field).asText(null);
        }
    }

    /** Исход поллинга: дождались ли + последняя пойманная assertion-ошибка (для сообщения). */
    private record PollOutcome(boolean satisfied, AssertionError lastError) {
    }

    /** Ошибка инфраструктуры {@code /raw} (I/O, сериализация) с контекстом кейс+запрос. */
    protected static final class SourceApiException extends RuntimeException {

        SourceApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
