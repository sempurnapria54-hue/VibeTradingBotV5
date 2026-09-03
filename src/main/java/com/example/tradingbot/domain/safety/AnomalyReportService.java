package com.example.tradingbot.domain.safety;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Ведёт {@link AnomalyReport} по lifecycle реактивной реакции холда:
 * CREATED (before-слепок) → IN_PROGRESS → KILL_SWITCH_EXECUTED → COMPLETED
 * (after-слепок), либо ERROR при сбое обработки. Severity приходит СО
 * СТУПЕНЬЮ сигнала: жёсткая (kill-switch в составе реакции) — CRITICAL,
 * мягкая (запрет новых входов без снятия риска) — NON_CRITICAL
 * (docs/rules/instrument-hold.md). Слепок двухносительный: локальное
 * (БД) состояние из {@link DealContext} + внешнее (биржа) состояние по instId
 * триггера (позиция + pending-ордера), читаемое через {@link IntegrationService}.
 * Чтение биржи best-effort: сбой read'а не валит реакцию — фиксируется маркером
 * в слепке. Схема внешнего слепка открытая/аддитивная (не финальная): поля
 * доливаются позже без переформатирования (PnL — шаг 7; биржа-широкая
 * реконсиляция и orphan/algo-сущности по всей бирже — проактивный AnomalyJob,
 * шаг 8). См. docs/lifecycles/AnomalyReport.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyReportService {

    /** Предел длины message под колонку varchar(1024) (V10). */
    private static final int MESSAGE_MAX_LENGTH = 1024;

    private final AnomalyReportDataService dataService;
    private final IntegrationService integrationService;
    private final ObjectMapper objectMapper;

    /** Создать отчёт в CREATED с before-слепками (локальный БД + внешний биржевой). */
    public AnomalyReport open(DealContext dealContext, HoldSignal signal) {
        return create(dealContext, signal, AnomalyReport.Status.CREATED);
    }

    /**
     * Журнальный отчёт: создаётся <b>уже завершённым</b> — обрабатывать
     * нечего, снятие риска в составе реакции отсутствует, и снимки
     * собираются один раз, при создании (docs/lifecycles/AnomalyReport.md
     * §«Две тропы обработки»). After-слепков у него нет по построению:
     * между «до» и «после» ничего не происходило.
     */
    public AnomalyReport journal(DealContext dealContext, HoldSignal signal) {
        return journal(dealContext, signal, null);
    }

    /**
     * Журнальный отчёт с названным ПРЕДМЕТОМ — сущностью, о которой он.
     * Предмет входит в ключ дедупа у отчёта без блокировки: его состояние
     * держится на сущности, а не на объекте радиуса
     * (docs/models/domain/other/AnomalyReport.md §«Ключ дедупа у STATE»).
     */
    public AnomalyReport journal(DealContext dealContext, HoldSignal signal, String subjectExternalId) {
        return create(dealContext, signal, AnomalyReport.Status.COMPLETED, subjectExternalId);
    }

    private AnomalyReport create(DealContext dealContext, HoldSignal signal, AnomalyReport.Status status) {
        return create(dealContext, signal, status, null);
    }

    private AnomalyReport create(DealContext dealContext, HoldSignal signal, AnomalyReport.Status status,
                                 String subjectExternalId) {
        AnomalyReport report = new AnomalyReport();
        report.setInternalId(ClientIdGenerator.generate());
        report.setExchangeId(dealContext.getExchange().getId());
        report.setInstrumentId(nonNull(dealContext.getInstrument())
                ? dealContext.getInstrument().getId()
                : null);
        report.setSubjectExternalId(subjectExternalId);
        report.setScope(signal.getScope());
        report.setSeverity(severityOf(signal));
        report.setStatus(status);
        report.setCode(signal.getCode());
        report.setInternalBefore(internalSnapshot(dealContext));
        report.setExternalBefore(externalSnapshot(dealContext));
        return dataService.save(report);
    }

    /** Класс отчёта по ступени сигнала: снимает ли реакция принятый риск. */
    private AnomalyReport.Severity severityOf(HoldSignal signal) {
        return isTrue(signal.tearsDownRisk())
                ? AnomalyReport.Severity.CRITICAL
                : AnomalyReport.Severity.NON_CRITICAL;
    }

    /** Продвинуть отчёт в указанный статус (IN_PROGRESS / KILL_SWITCH_EXECUTED). */
    public AnomalyReport advance(AnomalyReport report, AnomalyReport.Status status) {
        report.setStatus(status);
        return dataService.save(report);
    }

    /**
     * Завершить отчёт: after-слепки (локальный БД + внешний биржевой, читается
     * после kill-switch — остаточный риск частичного teardown виден в нём) +
     * COMPLETED.
     */
    public AnomalyReport complete(AnomalyReport report, DealContext dealContext) {
        report.setInternalAfter(internalSnapshot(dealContext));
        report.setExternalAfter(externalSnapshot(dealContext));
        report.setStatus(AnomalyReport.Status.COMPLETED);
        return dataService.save(report);
    }

    /** Терминал-ошибка обработки: сообщение (обрезано под колонку) + ERROR. */
    public AnomalyReport fail(AnomalyReport report, String message) {
        report.setMessage(StringUtils.abbreviate(message, MESSAGE_MAX_LENGTH));
        report.setStatus(AnomalyReport.Status.ERROR);
        return dataService.save(report);
    }

    /** Компактный слепок локального состояния (БД-граф сделки + статусы scope) в JSON. */
    /**
     * Слепок описывает то, что известно. Сделки и инструмента у
     * счёт-широкого детектора нет по построению (его радиус — биржа), и
     * разыменование их здесь роняло бы отчёт ровно на той тропе, ради
     * наблюдаемости которой отчёт и заводится.
     */
    private String internalSnapshot(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        Instrument instrument = dealContext.getInstrument();
        Exchange exchange = dealContext.getExchange();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (nonNull(deal)) {
            Position position = deal.livePosition();
            snapshot.put("dealId", deal.getId());
            snapshot.put("dealInternalId", deal.getInternalId());
            snapshot.put("dealStatus", deal.getStatus());
            snapshot.put("positionLiveRisk", nonNull(position) && isTrue(position.hasLiveRisk()));
            snapshot.put("orderIds", orderIds(deal.getOrders()));
            snapshot.put("algoOrderIds", algoOrderIds(deal.getAlgoOrders()));
        }
        if (nonNull(instrument)) {
            snapshot.put("instrumentId", instrument.getId());
            snapshot.put("instrumentExternalId", instrument.getExternalId());
            snapshot.put("instrumentStatus", instrument.getStatus());
        }
        if (nonNull(exchange)) {
            snapshot.put("exchangeId", exchange.getId());
            snapshot.put("exchangeStatus", exchange.getStatus());
        }
        return writeJson(snapshot);
    }

    private List<Long> orderIds(List<Order> orders) {
        if (isEmpty(orders)) {
            return List.of();
        }
        return orders.stream().map(Order::getId).collect(Collectors.toList());
    }

    private List<Long> algoOrderIds(List<AlgoOrder> algoOrders) {
        if (isEmpty(algoOrders)) {
            return List.of();
        }
        return algoOrders.stream().map(AlgoOrder::getId).collect(Collectors.toList());
    }

    /**
     * Слепок внешнего (биржевого) состояния по instId триггера: реальная позиция
     * и pending-ордера на стороне биржи. Открытая/аддитивная схема (не финальная):
     * orphan/algo-сущности и биржа-широкая реконсиляция (L4) — проактивный контур
     * шага 8.
     */
    /**
     * Внешний слепок адресуется инструментом; у счёт-широкого детектора
     * его нет, и добывать нечего — слепок остаётся пустым, а не роняет
     * отчёт. Собственного среза счёта отчёт не читает: срез уже добыт
     * проходом детекции, и второе чтение выбирало бы лимит частоты у
     * торговой петли.
     */
    private String externalSnapshot(DealContext dealContext) {
        if (isNull(dealContext.getInstrument())) {
            return null;
        }
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("instrumentExternalId", externalInstrumentId);
        snapshot.put("position", readExchange(externalInstrumentId,
                () -> integrationService.getPosition(externalInstrumentId)));
        snapshot.put("pendingOrders", readExchange(externalInstrumentId,
                () -> integrationService.getPendingOrders(externalInstrumentId)));
        return writeJson(snapshot);
    }

    /** Best-effort чтение биржи: сбой read'а не валит реакцию холда — фиксируется маркером в слепке. */
    private Object readExchange(String externalInstrumentId, Supplier<Object> read) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            log.error("Anomaly external snapshot read failed instId={}", externalInstrumentId, e);
            return Map.of("readError", e.getClass().getSimpleName());
        }
    }

    /** Сериализация слепка best-effort: сбой сериализации не валит реакцию холда. */
    private String writeJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Anomaly snapshot serialization failed snapshotKeys={}", snapshot.keySet(), e);
            return null;
        }
    }
}
