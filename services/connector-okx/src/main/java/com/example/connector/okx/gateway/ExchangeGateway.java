package com.example.connector.okx.gateway;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Входной контракт коннектора — исполнимая форма перечня, объявленного в
 * {@code docs/components/IntegrationService.md} §«Входной контракт».
 *
 * <p><b>Два свойства, из которых выведён весь перечень.</b>
 *
 * <ul>
 *   <li><b>На входе доменные запросы, на выходе общая модель.</b>
 *       Граничный снапшот наружу не выходит: он несёт форму источника, а
 *       у второй площадки она своя. Перевод снапшот → домен делает сам
 *       коннектор, последним шагом чтения
 *       ({@code docs/rules/raw-exchange-dto-boundary.md} — там же о том,
 *       что границ две и правило адресует первую).</li>
 *   <li><b>Коннектор стейтлесс и обслуживает любой счёт любого
 *       тенанта.</b> Отсюда {@code accountInternalId} первым параметром у
 *       КАЖДОЙ приватной операции: по нему берутся ключи из Vault
 *       ({@code docs/architecture/tenant-and-exchange.md} §Ключи).
 *       Публичные операции счёта не несут — ключи им не нужны, и
 *       требовать их значило бы связать чтение листинга с наличием
 *       счёта.</li>
 * </ul>
 *
 * <p><b>Замещения заявки здесь нет, и это не пропуск.</b> Замещение —
 * доменная операция, и собирает её ядро из «выставить» плюс «отменить»;
 * биржевым {@code amend} мы не пользуемся сознательно
 * ({@code docs/rules/replace-not-amend.md}).
 *
 * <p><b>Контракт чтения:</b> найдено → модель; успешно, но не найдено →
 * пусто; ошибка API, разбора или инварианта → контролируемое исключение.
 * Пустой ответ означает «не найдено в этом источнике», а не ошибку.
 */
public interface ExchangeGateway {

    // --- приватные операции: команды площадке ---------------------------
    // Все возвращают ExchangeAck — подтверждение ПРИЁМА, которое истиной
    // не является (docs/rules/ack-not-runtime-truth.md): исход операции
    // узнаётся наблюдением, а не ответом на команду.

    /** Выставить обычную заявку, со встроенной защитой либо без. */
    ExchangeAck placeOrder(String accountInternalId, Order order, String externalInstrumentId);

    /** Снять выставленную заявку. */
    ExchangeAck cancelOrder(String accountInternalId, Order order, String externalInstrumentId);

    /** Выставить условную заявку. */
    ExchangeAck placeAlgoOrder(String accountInternalId, AlgoOrder algoOrder, String externalInstrumentId);

    /** Снять условную заявку. */
    ExchangeAck cancelAlgoOrder(String accountInternalId, AlgoOrder algoOrder, String externalInstrumentId);

    /** Снять защиту, привязанную к заявке. */
    ExchangeAck cancelAttachedProtection(String accountInternalId, AttachedAlgoOrder attached,
                                         String externalInstrumentId);

    /** Рыночное закрытие позиции. */
    ExchangeAck closePosition(String accountInternalId, String externalInstrumentId, String settleCurrency);

    /** Настройка плеча счёта на инструменте. */
    ExchangeAck setLeverage(String accountInternalId, String externalInstrumentId, Integer leverage);

    // --- приватные операции: добыча факта -------------------------------

    /** Заявка по идентификатору. */
    Order getOrder(String accountInternalId, String externalInstrumentId, String externalId, String internalId);

    /** Живые заявки инструмента. */
    List<Order> getPendingOrders(String accountInternalId, String externalInstrumentId);

    /**
     * Все живые заявки счёта.
     *
     * <p>Счёт-широкий срез: он читается по счёту целиком, и строку в нём
     * адресует инструмент. Без счёта такой запрос в мультитенантной
     * конструкции смысла не имеет — «все живые заявки» чьи?
     */
    List<Order> getAllPendingOrders(String accountInternalId);

    /** История заявок инструмента. */
    List<Order> getOrderHistory(String accountInternalId, String externalInstrumentId);

    /** Условная заявка по идентификатору. */
    AlgoOrder getAlgoOrder(String accountInternalId, String externalInstrumentId, String externalId,
                           String internalId);

    /** Все живые условные заявки счёта. */
    List<AlgoOrder> getAllPendingAlgoOrders(String accountInternalId);

    /**
     * Живые условные заявки инструмента одного рода условия.
     *
     * <p>Род условия — операнд, а не удобство: у площадки условные заявки
     * разных родов лежат в разных перечнях, и запрос без рода вернул бы
     * не «все», а один произвольный.
     */
    List<AlgoOrder> getPendingAlgoOrders(String accountInternalId, String externalInstrumentId,
                                         AlgoOrder.ConditionType conditionType);

    /**
     * История условных заявок инструмента.
     *
     * <p>Читается, когда живой заявки уже нет: исход условной узнаётся
     * только отсюда — в живом перечне сработавшей заявки не будет.
     */
    List<AlgoOrder> getAlgoOrderHistory(String accountInternalId, String externalInstrumentId,
                                        AlgoOrder.ConditionType conditionType, String externalId);

    /** Живые материализованные защиты инструмента. */
    List<AttachedAlgoOrder> getPendingMaterializedProtections(String accountInternalId,
                                                              String externalInstrumentId);

    /**
     * История материализованных защит по одной ноге.
     *
     * <p>Нога — доменный перечень ({@code ProtectionHistoryLeg}), а не
     * строка площадки: у истории защит стороны разные, и читатель
     * спрашивает про ту, исход которой ему нужен.
     */
    List<AttachedAlgoOrder> getMaterializedProtectionHistory(String accountInternalId,
                                                             String externalInstrumentId,
                                                             ProtectionHistoryLeg leg);

    /** Позиция инструмента. */
    Position getPosition(String accountInternalId, String externalInstrumentId);

    /** Все позиции счёта. */
    List<Position> getPositions(String accountInternalId);

    /**
     * Закрытые эпизоды позиции в окне.
     *
     * <p><b>Возвращается {@code Position}, и модель несёт только то, что
     * сообщает запись закрытия</b> — эпизод завершён, живого размера у
     * него нет ({@code docs/models/mapping/PositionCloseResult.md}).
     * Отдельной доменной модели для записи закрытия нет намеренно: это
     * та же позиция, просто в прошлом.
     */
    List<Position> getPositionCloseRecords(String accountInternalId, String externalInstrumentId,
                                           OffsetDateTime windowBegin);

    /** Баланс расчётной валюты. */
    BalanceContainer getBalance(String accountInternalId, String settleCurrency);

    /** Движения средств за окно. */
    List<DealCashFlow> getBills(String accountInternalId, OffsetDateTime begin, OffsetDateTime end);

    /** Архив движений средств за окно. */
    List<DealCashFlow> getBillsArchive(String accountInternalId, OffsetDateTime begin, OffsetDateTime end);

    /**
     * Ставки комиссии.
     *
     * <p><b>Чтение приватное, и это не мелочь:</b> ставка есть атрибут
     * комиссионного уровня СЧЁТА
     * ({@code docs/models/domain/other/TradeFeeRate.md}), а не свойство
     * площадки; публичным её чтение сделало бы ставку общей для всех
     * тенантов.
     */
    List<TradeFeeRate> getTradeFeeRates(String accountInternalId, String externalInstrumentType);

    // --- публичные операции: рыночные данные ----------------------------
    // Счёта не несут: ключи им не нужны.

    /** Инструмент площадки. */
    Instrument getInstrument(String externalInstrumentId, String externalInstrumentType);

    /** Листинг инструментов площадки. */
    List<Instrument> getInstruments(String externalInstrumentType);

    /**
     * Правила торговли инструментом.
     *
     * <p><b>Отдельная операция, а не часть чтения инструмента.</b>
     * Правила меняются площадкой независимо от листинга, и читатель
     * синхронизирует их своим тиком: сложи их в одно чтение — и
     * обновление правил стоило бы обхода всего листинга.
     */
    InstrumentExternalRules getInstrumentRules(String externalInstrumentId, String externalInstrumentType);

    /** Последние свечи инструмента. */
    List<Candle> getLatestCandles(String externalInstrumentId, String externalBar, Integer limit);

    /**
     * Исторические свечи инструмента окном назад от момента.
     *
     * <p>Окно ограничено пределом, а не «всё, что есть»: минутные свечи
     * за годы кладут и площадку, и базу читателя
     * ({@code .claude/rules/codestyle.md} §«Выборка данных»).
     */
    List<Candle> getHistoryCandles(String externalInstrumentId, String externalBar, Long afterMillis,
                                   Integer limit);

    /** Свеча индекса на момент. */
    Candle getIndexCandleAt(String indexInstrumentId, String externalBar, OffsetDateTime at);

    /** Цены момента: last, mark, index. */
    MarketPriceData getMarketPriceData(String externalInstrumentId);

    /**
     * Тикеры всего листинга одним чтением — срез цен по типу инструмента.
     *
     * <p>Марк-цены и индекса в тикере НЕТ: площадка их не отдаёт
     * ({@code docs/models/domain/other/MarketTicker.md}). Их берут
     * соседние операции, а срез собирает вызывающий.
     */
    List<MarketTicker> getTickers(String externalInstrumentType);

    /** Книга заявок инструмента на заданную глубину каждой стороны. */
    MarketOrderBook getOrderBook(String externalInstrumentId, Integer depth);

    /**
     * Марк-цены листинга: инструмент площадки → цена.
     *
     * <p>Картой, а не моделью: марк-цена — одна величина на инструмент, и
     * модель с единственным полем была бы носителем ради формы.
     */
    Map<String, BigDecimal> getMarkPrices(String externalInstrumentType);

    /** Цены индексов одной расчётной валюты: индекс → цена. */
    Map<String, BigDecimal> getIndexPrices(String quoteCurrency);

    /** Время площадки. */
    OffsetDateTime getServerTime();
}
