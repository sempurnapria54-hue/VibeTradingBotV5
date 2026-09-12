package com.example.tradingcore.integration.internal.api.exchange;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.exchange.ExchangeFailureClass;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import com.example.tradingcore.config.NeighbourProperties;
import com.example.tradingcore.integration.internal.api.PeerReadException;
import com.example.tradingcore.integration.internal.api.PeerServiceUnavailableException;
import com.example.tradingcore.integration.internal.api.ServiceTokenProvider;
import com.example.tradingcore.integration.internal.api.model.ConnectorErrorResponse;
import com.example.tradingcore.util.Constants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Исходящий шлюз ядра к коннектору площадки: команды и добыча факта
 * (docs/architecture/contracts.md §«Синхронные вызовы»; перечень операций
 * — docs/components/IntegrationService.md §«Входной контракт»).
 *
 * <p><b>Счёт — операнд каждой приватной операции.</b> Коннектор стейтлесс
 * и обслуживает любой счёт любого тенанта; по идентификатору счёта он
 * берёт ключи из Vault (docs/architecture/tenant-and-exchange.md §Ключи).
 * Ключей здесь нет ни в каком виде.
 *
 * <p><b>Ответ команды — подтверждение ПРИЁМА, а не исход.</b> Что
 * произошло на площадке, узнаётся наблюдением
 * (docs/rules/ack-not-runtime-truth.md): {@code ExchangeAck} истиной не
 * является ни при каком коде ответа.
 *
 * <p><b>Коннектор один, и это названное ограничение.</b> Адрес приезжает
 * одной строкой конфигурации ({@code neighbours.connector}), код площадки
 * объявлен рядом с ним. Вторая площадка (фаза 3) вводит выбор коннектора
 * по коду площадки СЧЁТА; заводить реестр коннекторов раньше второй
 * площадки значило бы держать механизм без предмета
 * (.claude/rules/design-simplicity.md).
 *
 * <p><b>Время в запросе всегда UTC.</b> Смещение вида {@code +03:00} несёт
 * {@code +}, который приёмная сторона законно прочитает пробелом, — и
 * граница окна уехала бы молча. Проект и так живёт в UTC
 * (docs/rules/time-utc.md); приведение здесь охраняет провод, а не меняет
 * семантику.
 */
@Component
public class ExchangeOperationsClient {

    private static final String ACCOUNT_PATH = "/api/v1/accounts/{accountInternalId}";

    private static final ParameterizedTypeReference<List<Order>> ORDER_LIST =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<AlgoOrder>> ALGO_ORDER_LIST =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<AttachedAlgoOrder>> PROTECTION_LIST =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Position>> POSITION_LIST =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<DealCashFlow>> CASH_FLOW_LIST =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<TradeFeeRate>> FEE_RATE_LIST =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;
    private final String clientRegistrationId;

    public ExchangeOperationsClient(RestClient.Builder restClientBuilder,
                                    ServiceTokenProvider tokenProvider,
                                    NeighbourProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getConnector().getBaseUrl()).build();
        this.tokenProvider = tokenProvider;
        this.clientRegistrationId = properties.getConnector().getClientRegistrationId();
    }

    // --- команды площадке ---------------------------------------------------

    /** Выставить обычную заявку, со встроенной защитой либо без. */
    public ExchangeAck placeOrder(String accountInternalId, Order order, String externalInstrumentId) {
        return call("place-order", () -> restClient.post()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/orders")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(order)
                .retrieve()
                .body(ExchangeAck.class));
    }

    /** Снять выставленную заявку. */
    public ExchangeAck cancelOrder(String accountInternalId, Order order, String externalInstrumentId) {
        return call("cancel-order", () -> restClient.post()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/orders/cancellations")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(order)
                .retrieve()
                .body(ExchangeAck.class));
    }

    /** Выставить условную заявку. */
    public ExchangeAck placeAlgoOrder(String accountInternalId, AlgoOrder algoOrder,
                                      String externalInstrumentId) {
        return call("place-algo-order", () -> restClient.post()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/algo-orders")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(algoOrder)
                .retrieve()
                .body(ExchangeAck.class));
    }

    /** Снять условную заявку. */
    public ExchangeAck cancelAlgoOrder(String accountInternalId, AlgoOrder algoOrder,
                                       String externalInstrumentId) {
        return call("cancel-algo-order", () -> restClient.post()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/algo-orders/cancellations")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(algoOrder)
                .retrieve()
                .body(ExchangeAck.class));
    }

    /** Снять защиту, привязанную к заявке. */
    public ExchangeAck cancelAttachedProtection(String accountInternalId, AttachedAlgoOrder attached,
                                                String externalInstrumentId) {
        return call("cancel-attached-protection", () -> restClient.post()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/attached-protections/cancellations")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(attached)
                .retrieve()
                .body(ExchangeAck.class));
    }

    /** Рыночное закрытие позиции. */
    public ExchangeAck closePosition(String accountInternalId, String externalInstrumentId,
                                     String settleCurrency) {
        return call("close-position", () -> restClient.post()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/positions/closures")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("settleCurrency", settleCurrency)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ExchangeAck.class));
    }

    /** Настройка плеча счёта на инструменте. */
    public ExchangeAck setLeverage(String accountInternalId, String externalInstrumentId, Integer leverage) {
        return call("set-leverage", () -> restClient.post()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/leverage")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("leverage", leverage)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ExchangeAck.class));
    }

    // --- добыча факта -------------------------------------------------------

    /** Заявка по биржевому либо нашему идентификатору. */
    public Order getOrder(String accountInternalId, String externalInstrumentId, String externalId,
                          String internalId) {
        return call("order-lookup", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/orders/lookup")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("externalId", externalId)
                        .queryParam("internalId", internalId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(Order.class));
    }

    /** Все живые заявки счёта. */
    public List<Order> getAllPendingOrders(String accountInternalId) {
        return call("pending-orders", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/orders/pending").build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ORDER_LIST));
    }

    /** Живые заявки инструмента. */
    public List<Order> getPendingOrders(String accountInternalId, String externalInstrumentId) {
        return call("pending-instrument-orders", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/orders/pending/instrument")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ORDER_LIST));
    }

    /** История заявок инструмента. */
    public List<Order> getOrderHistory(String accountInternalId, String externalInstrumentId) {
        return call("order-history", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/orders/history")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ORDER_LIST));
    }

    /** Условная заявка по биржевому либо нашему идентификатору. */
    public AlgoOrder getAlgoOrder(String accountInternalId, String externalInstrumentId, String externalId,
                                  String internalId) {
        return call("algo-order-lookup", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/algo-orders/lookup")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("externalId", externalId)
                        .queryParam("internalId", internalId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(AlgoOrder.class));
    }

    /** Все живые условные заявки счёта. */
    public List<AlgoOrder> getAllPendingAlgoOrders(String accountInternalId) {
        return call("pending-algo-orders", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/algo-orders/pending").build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ALGO_ORDER_LIST));
    }

    /**
     * Живые условные заявки инструмента одного рода условия.
     *
     * <p>Род условия — операнд, а не удобство: у площадки условные заявки
     * разных родов лежат в разных перечнях, и запрос без рода вернул бы не
     * «все», а один произвольный.
     */
    public List<AlgoOrder> getPendingAlgoOrders(String accountInternalId, String externalInstrumentId,
                                                AlgoOrder.ConditionType conditionType) {
        return call("pending-instrument-algo-orders", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/algo-orders/pending/instrument")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("conditionType", conditionType)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ALGO_ORDER_LIST));
    }

    /** История условных заявок инструмента: исход сработавшей узнаётся только отсюда. */
    public List<AlgoOrder> getAlgoOrderHistory(String accountInternalId, String externalInstrumentId,
                                               AlgoOrder.ConditionType conditionType, String externalId) {
        return call("algo-order-history", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/algo-orders/history")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("conditionType", conditionType)
                        .queryParam("externalId", externalId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ALGO_ORDER_LIST));
    }

    /** Живые материализованные защиты инструмента. */
    public List<AttachedAlgoOrder> getPendingMaterializedProtections(String accountInternalId,
                                                                     String externalInstrumentId) {
        return call("pending-protections", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/attached-protections/pending")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(PROTECTION_LIST));
    }

    /** История материализованных защит по одной ноге. */
    public List<AttachedAlgoOrder> getMaterializedProtectionHistory(String accountInternalId,
                                                                    String externalInstrumentId,
                                                                    ProtectionHistoryLeg leg) {
        return call("protection-history", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/attached-protections/history")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("leg", leg)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(PROTECTION_LIST));
    }

    /** Позиция инструмента; пусто — живого эпизода на площадке нет. */
    public Position getPosition(String accountInternalId, String externalInstrumentId) {
        return call("position", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/positions/instrument")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(Position.class));
    }

    /** Все позиции счёта. */
    public List<Position> getPositions(String accountInternalId) {
        return call("positions", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/positions").build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(POSITION_LIST));
    }

    /** Закрытые эпизоды позиции в окне; эпизод возвращается той же моделью, в прошлом. */
    public List<Position> getPositionCloseRecords(String accountInternalId, String externalInstrumentId,
                                                  OffsetDateTime windowBegin) {
        return call("position-close-records", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/positions/closed")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("windowBegin", utc(windowBegin))
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(POSITION_LIST));
    }

    /** Баланс расчётной валюты. */
    public BalanceContainer getBalance(String accountInternalId, String settleCurrency) {
        return call("balance", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/balance")
                        .queryParam("settleCurrency", settleCurrency)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(BalanceContainer.class));
    }

    /** Движения средств за окно. */
    public List<DealCashFlow> getBills(String accountInternalId, OffsetDateTime begin, OffsetDateTime end) {
        return call("bills", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/bills")
                        .queryParam("begin", utc(begin))
                        .queryParam("end", utc(end))
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(CASH_FLOW_LIST));
    }

    /** Архив движений средств за окно. */
    public List<DealCashFlow> getBillsArchive(String accountInternalId, OffsetDateTime begin,
                                              OffsetDateTime end) {
        return call("bills-archive", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/bills/archive")
                        .queryParam("begin", utc(begin))
                        .queryParam("end", utc(end))
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(CASH_FLOW_LIST));
    }

    /** Ставки комиссии счёта: чтение приватное, ставка — атрибут уровня СЧЁТА. */
    public List<TradeFeeRate> getTradeFeeRates(String accountInternalId, String externalInstrumentType) {
        return call("trade-fee-rates", () -> restClient.get()
                .uri(builder -> builder.path(ACCOUNT_PATH + "/trade-fee-rates")
                        .queryParam("externalInstrumentType", externalInstrumentType)
                        .build(accountInternalId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(FEE_RATE_LIST));
    }

    /**
     * Свеча индекса на момент — вторая публичная операция ядра.
     *
     * <p><b>Нужна лестнице огрубления курса</b> (docs/components/RefreshBillsExecutor.md
     * §«Лестница огрубления разрешения»): движение в чужой валюте без
     * курса не складывается с остальными, и курс резолвится по свече
     * котировки на момент операции. Локально свеча не персистится —
     * свечных групп под неё не заводится.
     *
     * <p>Счёта операция не несёт: ключи ей не нужны, а брать её через
     * владельца рыночных данных нечего — курс нужен ровно здесь и ровно на
     * момент записи.
     */
    public Candle getIndexCandleAt(String indexInstrumentId, TimeFrame timeframe, OffsetDateTime at) {
        return call("index-candle", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/candles/index")
                        .queryParam("indexInstrumentId", indexInstrumentId)
                        .queryParam("timeframe", timeframe.name())
                        .queryParam("at", utc(at))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(Candle.class));
    }

    /**
     * Время площадки — публичная операция, которую зовёт ядро.
     *
     * <p><b>Счёта не несёт</b> (ключи ей не нужны), а нужна там, где обе
     * границы окна обязаны быть биржевыми: верхняя граница окна движений и
     * проверка свежести фазы сравниваются с биржевыми метками, локальные
     * часы в сравнение не входят
     * (docs/models/domain/aggregate/Deal.md, {@code billsWindowBegin}).
     * Через владельца рыночных данных её брать нечего: это не фича рынка,
     * а часы источника, и лишний ярус добавил бы только задержку.
     */
    public OffsetDateTime getServerTime() {
        return call("server-time", () -> restClient.get()
                .uri("/api/v1/market/time")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(OffsetDateTime.class));
    }

    private String bearer() {
        return Constants.Header.BEARER_PREFIX + tokenProvider.getTokenValue(clientRegistrationId);
    }

    private static String utc(OffsetDateTime moment) {
        return isNull(moment) ? null : moment.withOffsetSameInstant(ZoneOffset.UTC).toString();
    }

    /**
     * Исполняет вызов, переводя отказ в класс своей природы.
     *
     * <p><b>Класс приезжает телом, а не выводится из HTTP-статуса.</b>
     * Реакция на отказ живёт у ядра, и коннектор ради этого объявляет класс
     * отдельным полем (docs/components/IntegrationService.md §«Классы
     * отказа на границе — дом здесь»); угадывать его по статусу значило бы
     * держать второй, расходящийся разбор той же истины.
     *
     * <p><b>Молчащий коннектор — не отказ площадки.</b> Когда ответа нет
     * вовсе (транспорт, {@code 5xx} без класса), классифицировать нечего:
     * проход по объекту пропускается целиком, статуса не двигая
     * (docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу —
     * свой класс, и сделку в ошибку он не уводит»). Увести сделку в
     * {@code ERROR} на перезапуске коннектора было бы вдвойне напрасно:
     * safety-flow ходит через него же.
     *
     * <p><b>Нераспознанный класс — наш дефект.</b> Негодный вход
     * ({@code 400}) и отвергнутая идентичность ({@code 401}/{@code 403})
     * повтором не лечатся: тем же запросом придёт тот же отказ.
     */
    private <T> T call(String operation, Supplier<T> exchange) {
        try {
            return exchange.get();
        } catch (RestClientResponseException e) {
            throw translate(operation, e);
        } catch (RestClientException e) {
            throw new PeerServiceUnavailableException(
                    "Connector transport error on [" + operation + "]", e);
        }
    }

    private RuntimeException translate(String operation, RestClientResponseException failure) {
        ConnectorErrorResponse body = readBody(failure);
        ExchangeFailureClass failureClass = isNull(body)
                ? null
                : EnumUtils.getEnum(ExchangeFailureClass.class, body.getCode());
        String detail = "[" + operation + "] "
                + (isNull(body) ? failure.getStatusText() : body.getMessage());
        if (isNull(failureClass)) {
            return unclassified(detail, failure);
        }
        return switch (failureClass) {
            case CREDENTIALS_UNAVAILABLE -> new CredentialsUnavailableException("No account keys: " + detail);
            case SECRET_STORE_UNAVAILABLE ->
                    new SecretStoreUnavailableException("Secret store silent: " + detail);
            case EXCHANGE_CREDENTIALS_REJECTED -> new CredentialsRejectedException("Keys rejected: " + detail);
            case EXCHANGE_UNREACHABLE, EXCHANGE_ERROR ->
                    new ExchangeIntegrationException("Exchange call failed: " + detail);
            case EXTERNAL_STATUS -> new ExternalStatusException(reasonOf(body), "External status: " + detail);
            case EXTERNAL_INVARIANT_VIOLATION ->
                    new ExternalInvariantViolationException("Exchange invariant violated: " + detail);
        };
    }

    /** Отказ без узнаваемого класса: молчание коннектора либо наш дефект. */
    private static RuntimeException unclassified(String detail, RestClientResponseException failure) {
        if (failure.getStatusCode().is5xxServerError()) {
            return new PeerServiceUnavailableException("Connector failed on " + detail, failure);
        }
        return new PeerReadException("Connector refused " + detail
                + " with status " + failure.getStatusCode(), failure);
    }

    /**
     * Причина внутри класса {@code EXTERNAL_STATUS}.
     *
     * <p>Пустая либо неизвестная причина — {@code UNKNOWN_EXTERNAL_STATUS}:
     * подставить любую другую значило бы объяснить исход сущности тем,
     * чего источник не сообщал.
     */
    private static ExternalStatusReason reasonOf(ConnectorErrorResponse body) {
        if (isBlank(body.getReason())) {
            return ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS;
        }
        ExternalStatusReason reason = EnumUtils.getEnum(ExternalStatusReason.class, body.getReason());
        return isNull(reason) ? ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS : reason;
    }

    /** Тело отказа, если оно разобралось; иначе пусто — класс тогда неизвестен. */
    private static ConnectorErrorResponse readBody(RestClientResponseException failure) {
        try {
            return failure.getResponseBodyAs(ConnectorErrorResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }
}
