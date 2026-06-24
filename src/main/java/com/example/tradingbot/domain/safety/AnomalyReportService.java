package com.example.tradingbot.domain.safety;

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
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Ведёт {@link AnomalyReport} по lifecycle реактивной реакции холда:
 * CREATED (before-слепок) → IN_PROGRESS → KILL_SWITCH_EXECUTED → COMPLETED
 * (after-слепок), либо ERROR при сбое обработки. Severity в реактивном
 * контуре всегда CRITICAL (граница захода). Внешние (биржевые) слепки —
 * форвард (реконсиляционный read биржи операционализируется проактивным
 * AnomalyJob, шаг 8); здесь собираются локальные (БД) слепки из
 * {@link DealContext}. См. docs/lifecycles/AnomalyReport.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyReportService {

    /** Предел длины message под колонку varchar(1024) (V10). */
    private static final int MESSAGE_MAX_LENGTH = 1024;

    private final AnomalyReportDataService dataService;
    private final ObjectMapper objectMapper;

    /** Создать отчёт в CREATED с before-слепком локального состояния. */
    public AnomalyReport open(DealContext dealContext, HoldSignal signal) {
        AnomalyReport report = new AnomalyReport();
        report.setInternalId(ClientIdGenerator.generate());
        report.setExchangeId(dealContext.getExchange().getId());
        report.setInstrumentId(dealContext.getInstrument().getId());
        report.setScope(signal.getScope());
        report.setSeverity(AnomalyReport.Severity.CRITICAL);
        report.setStatus(AnomalyReport.Status.CREATED);
        report.setCode(signal.getCode());
        report.setInternalBefore(internalSnapshot(dealContext));
        return dataService.save(report);
    }

    /** Продвинуть отчёт в указанный статус (IN_PROGRESS / KILL_SWITCH_EXECUTED). */
    public AnomalyReport advance(AnomalyReport report, AnomalyReport.Status status) {
        report.setStatus(status);
        return dataService.save(report);
    }

    /** Завершить отчёт: after-слепок локального состояния + COMPLETED. */
    public AnomalyReport complete(AnomalyReport report, DealContext dealContext) {
        report.setInternalAfter(internalSnapshot(dealContext));
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
    private String internalSnapshot(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        Instrument instrument = dealContext.getInstrument();
        Exchange exchange = dealContext.getExchange();
        Position position = deal.getPosition();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("dealId", deal.getId());
        snapshot.put("dealInternalId", deal.getInternalId());
        snapshot.put("dealStatus", deal.getStatus());
        snapshot.put("instrumentId", instrument.getId());
        snapshot.put("instrumentExternalId", instrument.getExternalId());
        snapshot.put("instrumentStatus", instrument.getStatus());
        snapshot.put("exchangeId", exchange.getId());
        snapshot.put("exchangeStatus", exchange.getStatus());
        snapshot.put("positionLiveRisk", nonNull(position) && isTrue(position.hasLiveRisk()));
        snapshot.put("orderIds", orderIds(deal.getOrders()));
        snapshot.put("algoOrderIds", algoOrderIds(deal.getAlgoOrders()));
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

    /** Сериализация слепка best-effort: сбой сериализации не валит реакцию холда. */
    private String writeJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Anomaly snapshot serialization failed dealId={}", snapshot.get("dealId"), e);
            return null;
        }
    }
}
