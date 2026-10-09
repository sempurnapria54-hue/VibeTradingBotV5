package com.example.tradingcore.integration.internal.event;

import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingbot.message.EventEnvelopeMessage;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.mapping.CoreEventMessageMapper;
import com.example.tradingcore.persistence.model.OutboxEntity;
import com.example.tradingcore.persistence.service.OutboxDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Записывает факт ядра в outbox <b>в транзакции вызывающего</b>: решение и
 * его событие ложатся одним ходом (docs/architecture/contracts.md §«У
 * каждого класса события назван писатель, и он же писатель решения»).
 *
 * <p><b>Поверхность у него доменная, а форма провода за ней.</b> Метод на
 * класс события принимает доменные модели и идентичности; перевод в форму
 * сообщения, конверт и сериализацию держит этот класс
 * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»). Прежде
 * он жил в доменном пакете, принимал уже собранное содержимое параметром
 * {@code Object} и строил конверт сам — то есть доменный пакет и называл
 * форму сообщения, и сериализовал её.
 *
 * <p><b>Конверт строится и тут же раскладывается по колонкам строки.</b>
 * Из метода он не выходит, и это не забытый шаг: колонки заполняются
 * <b>из объявленной формы</b>, а не рядом с ней. Форма — бин
 * {@link EventEnvelopeMessage}, собранный так, чтобы его разобрал и
 * читатель формы целиком, когда он появится; довод формы живёт у самого
 * класса, а раскладка по заголовкам — в docs/architecture/contracts.md
 * §«Как конверт лежит на проводе — поимённо».
 *
 * <p><b>Публикацией не занимается вовсе</b> — её ведёт реле, и начинается
 * оно там, где транзакция решения закончилась
 * (docs/components/OutboxRelayJob.md).
 *
 * <p><b>Тема выводится, а не приходит параметром.</b> Правило имени —
 * «производитель и род» (docs/architecture/contracts.md §«Событие →
 * тема»), а род у всех классов ядра один: это события-происшествия.
 * Параметр темы позволил бы писателю положить факт в тему состояний, где
 * компакция его потеряет.
 *
 * <p><b>Контекст трассировки пуст, и пустота законна:</b> она означает
 * «трассировки не было», а не «потеряна» — инструментирование экспорта
 * трейсов в сервисах ещё не подключено
 * (docs/architecture/contracts.md §«Конверт события»).
 */
@Component
@RequiredArgsConstructor
public class CoreEventWriter {

    /** Тема ядра: производитель и род. Род у всех его классов один — факты. */
    private static final String FACTS_TOPIC = "trading-core.facts";

    /**
     * Версия формы содержимого. Растёт, когда меняется состав полей;
     * потребитель старой версии обязан оставаться рабочим.
     *
     * <p><b>Поднимается один раз на ход, а не на каждую форму:</b> версия
     * на класс потребовала бы второго носителя версии в конверте, у которого
     * поле одно. Вторая версия несла составы, приехавшие одним ходом, —
     * операнды отчёта и признаки отбора у терминала, контекст входа у
     * создания, транш у решения о заявке, актор у классов с ручной тропой;
     * третья — предшественника в цепочке замещений у решения о заявке и два
     * новых класса, решение об условной заявке и снятие ступени
     * (docs/architecture/contracts.md §События).
     */
    private static final Integer FORM_VERSION = 3;

    private final OutboxDataService outboxDataService;
    private final ObjectMapper objectMapper;
    private final CoreEventMessageMapper eventMessageMapper;

    /** Решение о заявке; идентичности радиуса резолвит вызывающий. */
    public void orderDecided(String tenantId, Order order, String dealInternalId,
                             String dealTrancheInternalId, String exchangeAccountInternalId,
                             String instrumentInternalId) {
        write(tenantId, CoreEventType.ORDER_DECIDED,
                eventMessageMapper.domainToOrderDecidedMessage(order, dealInternalId,
                        dealTrancheInternalId, exchangeAccountInternalId, instrumentInternalId));
    }

    /** Решение об отдельной условной заявке; идентичности радиуса резолвит вызывающий. */
    public void algoOrderDecided(String tenantId, AlgoOrder algoOrder, String dealInternalId,
                                 String dealTrancheInternalId, String exchangeAccountInternalId,
                                 String instrumentInternalId) {
        write(tenantId, CoreEventType.ALGO_ORDER_DECIDED,
                eventMessageMapper.domainToAlgoOrderDecidedMessage(algoOrder, dealInternalId,
                        dealTrancheInternalId, exchangeAccountInternalId, instrumentInternalId));
    }

    /** Создание сделки: идентичности радиуса плюс контекст входа. */
    public void dealOpened(String tenantId, Deal deal, String exchangeAccountInternalId,
                           String instrumentInternalId, String strategyInternalId) {
        write(tenantId, CoreEventType.DEAL_OPENED,
                eventMessageMapper.domainToDealOpenedMessage(deal, exchangeAccountInternalId,
                        instrumentInternalId, strategyInternalId));
    }

    /** Выход сделки из штатного ведения; актор различает тропы класса. */
    public void dealShutdownInitiated(String tenantId, Deal deal, String exchangeAccountInternalId,
                                      String instrumentInternalId, String strategyInternalId,
                                      String actor) {
        write(tenantId, CoreEventType.DEAL_SHUTDOWN_INITIATED,
                eventMessageMapper.domainToDealShutdownInitiatedMessage(deal,
                        exchangeAccountInternalId, instrumentInternalId, strategyInternalId, actor));
    }

    /**
     * Закрытие сделки; писателей у класса два, потому что терминалов два.
     *
     * @param stopExitSlippage проскок выхода по стопу, посчитанный писателем
     *                         терминала по графу прохода; пусто — не измерен
     *                         (docs/spec/stop-exit-slippage.json)
     */
    public void dealClosed(String tenantId, Deal deal, String exchangeAccountInternalId,
                           String instrumentInternalId, String strategyInternalId,
                           Boolean graphComplete, BigDecimal stopExitSlippage) {
        write(tenantId, CoreEventType.DEAL_CLOSED,
                eventMessageMapper.domainToDealClosedMessage(deal, exchangeAccountInternalId,
                        instrumentInternalId, strategyInternalId, graphComplete, stopExitSlippage));
    }

    /** Подъём ступени радиуса; инструмент пуст у счётного сигнала. */
    public void holdRaised(String tenantId, HoldSignal signal, String exchangeAccountInternalId,
                           String instrumentInternalId, String actor) {
        write(tenantId, CoreEventType.HOLD_RAISED,
                eventMessageMapper.domainToHoldRaisedMessage(signal, exchangeAccountInternalId,
                        instrumentInternalId, actor));
    }

    /**
     * Снятие ступени радиуса ручной поверхностью: радиус и СНЯТАЯ ступень;
     * инструмент пуст у счётного радиуса.
     */
    public void holdReleased(String tenantId, HoldScope scope, HoldRung rung,
                             String exchangeAccountInternalId, String instrumentInternalId, String actor) {
        write(tenantId, CoreEventType.HOLD_RELEASED,
                eventMessageMapper.domainToHoldReleasedMessage(scope, rung, exchangeAccountInternalId,
                        instrumentInternalId, actor));
    }

    /** Отчёт о происшествии; актор различает детекцию и ручную тропу. */
    public void anomalyReported(String tenantId, AnomalyReport report,
                                String exchangeAccountInternalId, String instrumentInternalId,
                                String actor) {
        write(tenantId, CoreEventType.ANOMALY_REPORTED,
                eventMessageMapper.domainToAnomalyReportedMessage(report, exchangeAccountInternalId,
                        instrumentInternalId, actor));
    }

    /**
     * Строка outbox: конверт, тема и сериализованное содержимое.
     *
     * <p>Приватен намеренно: публичным он принимал бы содержимое
     * параметром, и форма сообщения снова стала бы видна вызывающему.
     *
     * @param tenantId идентичность тенанта-владельца; она же ключ
     *                 партиции, и без неё порядок событий внутри тенанта
     *                 не гарантируется
     * @param type     класс события
     * @param content  форма сообщения, собранная маппером
     */
    private void write(String tenantId, CoreEventType type, Object content) {
        EventEnvelopeMessage envelope = EventEnvelopeMessage.builder()
                .eventId(InternalIdFactory.forInternalEntity())
                .tenantId(tenantId)
                .eventType(type.name())
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .version(FORM_VERSION)
                .build();
        OutboxEntity entity = new OutboxEntity();
        entity.setEventId(envelope.getEventId());
        entity.setTenantId(envelope.getTenantId());
        entity.setEventType(envelope.getEventType());
        entity.setVersion(envelope.getVersion());
        entity.setOccurredAt(envelope.getOccurredAt());
        entity.setTraceContext(envelope.getTraceContext());
        entity.setTopic(FACTS_TOPIC);
        entity.setPayload(serialize(content));
        outboxDataService.save(entity);
    }

    /**
     * Содержимое строкой JSON.
     *
     * <p>Отказ сериализации <b>роняет транзакцию решения</b>, и это
     * названный выбор: решение, записанное без своего события, оставляет
     * потребителя без факта навсегда — а требование атомарности как раз и
     * заведено против этого.
     */
    private String serialize(Object content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialization failed", e);
        }
    }
}
