package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.event.AnomalyReportedContent;
import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingcore.config.AnomalyReportProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.AnomalyReportDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ведёт журнал происшествий
 * (docs/models/domain/other/AnomalyReport.md,
 * docs/lifecycles/AnomalyReport.md).
 *
 * <p><b>Троп две, и разводит их критичность — гоняется ли снятие живого
 * риска</b> (docs/lifecycles/AnomalyReport.md §«Две тропы обработки»).
 * Журнальная создаёт отчёт уже завершённым: обрабатывать нечего, снимки
 * собираются один раз, при создании. Критичная ведёт строку по статусам:
 * создание со снимком «до» → обработка → подтверждённое снятие риска →
 * терминал со снимком «после», либо {@code ERROR} при сбое обработки.
 *
 * <p><b>Порядок критичной тропы держит не этот сервис,</b> а координатор
 * последовательности (docs/components/SafetyHoldCoordinator.md): здесь
 * только запись состояния строки.
 *
 * <p><b>Природу факта различает ТРОПА, а не колонка.</b> Отчёт о
 * СОСТОЯНИИ дедупится стоящей строкой в окне наблюдения; отчёт о
 * ПРОИСШЕСТВИИ заводит свою строку всегда — иначе два разных происшествия
 * по одному инструменту схлопнулись бы. Величина известна писателю на
 * месте вызова и ни одному читателю не нужна
 * (docs/models/domain/other/AnomalyReport.md §«Природа факта — свойство
 * тропы, а не колонка»).
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyReportService {

    /** Предел длины текста ошибки под колонку {@code message varchar(1024)}. */
    private static final int MESSAGE_MAX_LENGTH = 1024;

    private final AnomalyReportDataService dataService;
    private final ExchangeOperationsClient exchangeOperationsClient;
    private final ObjectMapper objectMapper;
    private final AnomalyReportProperties properties;
    private final ActorProvider actorProvider;
    private final OutboxWriter outboxWriter;

    /**
     * Журнальный отчёт о факте-СОСТОЯНИИ: пока строка по ключу стои́т в
     * окне наблюдения, второй не заводится. Возвращает пусто, если строка
     * уже стои́т.
     *
     * <p>Операнд дедупа — стоящее СОСТОЯНИЕ объекта, а не статус отчёта:
     * журнальная тропа создаёт отчёт сразу завершённым, поэтому множество
     * незавершённых пусто по построению и такой дедуп не срабатывал бы
     * никогда (docs/rules/error-handling-policy.md).
     */
    @Transactional
    public AnomalyReport journalState(DealContext dealContext, HoldSignal signal, String subjectExternalId) {
        if (isTrue(standing(dealContext, signal, subjectExternalId))) {
            return null;
        }
        return create(dealContext, signal, AnomalyReport.Status.COMPLETED, subjectExternalId);
    }

    private Boolean standing(DealContext dealContext, HoldSignal signal, String subjectExternalId) {
        OffsetDateTime until = OffsetDateTime.now(ZoneOffset.UTC);
        return dataService.existsStanding(accountId(dealContext), instrumentId(dealContext),
                subjectExternalId, signal.getCode(), severityOf(signal),
                until.minus(properties.getObservationWindow()), until);
    }

    /**
     * Журнальный отчёт создаётся <b>уже завершённым по существу</b>:
     * после-снимков у него нет по построению — между «до» и «после»
     * ничего не происходило (docs/lifecycles/AnomalyReport.md §«Две тропы
     * обработки»).
     */
    private AnomalyReport create(DealContext dealContext, HoldSignal signal, AnomalyReport.Status status,
                                 String subjectExternalId) {
        AnomalyReport report = new AnomalyReport();
        report.setInternalId(InternalIdFactory.forInternalEntity());
        report.setExchangeAccountId(accountId(dealContext));
        report.setInstrumentId(instrumentId(dealContext));
        report.setSubjectExternalId(subjectExternalId);
        report.setScope(signal.getScope());
        report.setSeverity(severityOf(signal));
        report.setStatus(status);
        report.setCode(signal.getCode());
        report.setInternalBefore(internalSnapshot(dealContext));
        report.setExternalBefore(externalSnapshot(dealContext));
        AnomalyReport saved = dataService.save(report);
        publishReported(dealContext, saved);
        return saved;
    }

    /**
     * Событие происшествия — <b>той же транзакцией</b>, которой заведена
     * строка отчёта (docs/architecture/contracts.md §«У каждого класса
     * события назван писатель, и он же писатель решения»).
     *
     * <p><b>При поглощении дедупом строки нет — и события тоже:</b> ход
     * идёт отсюда, а поглощённая тропа сюда не доходит вовсе.
     *
     * <p><b>Актор едет содержимым, потому что тропа у класса не одна:</b>
     * отчёт заводит и детекция, и ручная операция — на постановке ступени и
     * на снятии (docs/rules/manual-halt.md §«Наблюдаемость: ручное отличимо и от
     * автоматики, и друг от друга»), — и различает их
     * в данных именно он (docs/models/domain/other/Auditable.md §«Область
     * значений актора»).
     */
    private void publishReported(DealContext dealContext, AnomalyReport report) {
        outboxWriter.write(tenantId(dealContext), CoreEventType.ANOMALY_REPORTED,
                new AnomalyReportedContent(report.getInternalId(), accountInternalId(dealContext),
                        instrumentInternalId(dealContext), report.getScope().name(),
                        report.getSeverity().name(), report.getCode(), actorProvider.currentActor()));
    }

    private String tenantId(DealContext dealContext) {
        return isNull(dealContext.getExchangeAccount()) ? null
                : dealContext.getExchangeAccount().getTenantId();
    }

    private String accountInternalId(DealContext dealContext) {
        return isNull(dealContext.getExchangeAccount()) ? null
                : dealContext.getExchangeAccount().getInternalId();
    }

    private String instrumentInternalId(DealContext dealContext) {
        return isNull(dealContext.getInstrument()) ? null : dealContext.getInstrument().getInternalId();
    }

    /**
     * Журнальный отчёт о факте-ПРОИСШЕСТВИИ: своя строка всегда, дедупа
     * нет. Два разных происшествия по одному инструменту схлопывать
     * нельзя — они обязаны быть счётными
     * (docs/lifecycles/AnomalyReport.md §«Идемпотентность зависит от
     * природы факта»).
     */
    @Transactional
    public AnomalyReport journal(DealContext dealContext, HoldSignal signal) {
        return create(dealContext, signal, AnomalyReport.Status.COMPLETED, null);
    }

    /**
     * Открыть отчёт критичной тропы: {@code CREATED} плюс снимки «до».
     *
     * <p><b>Раньше снятия риска, а не позже</b>
     * (docs/rules/error-handling-policy.md §«Идемпотентность реакции и
     * идемпотентность отчёта — разные ключи»): снимок «до» существует,
     * только если строка заведена прежде, чем снятие отработало. Дедупа у
     * этой тропы нет — снятие риска есть происшествие своего момента.
     */
    @Transactional
    public AnomalyReport open(DealContext dealContext, HoldSignal signal) {
        return create(dealContext, signal, AnomalyReport.Status.CREATED, null);
    }

    /** Продвинуть отчёт в названный статус обработки. */
    @Transactional
    public AnomalyReport advance(AnomalyReport report, AnomalyReport.Status status) {
        report.setStatus(status);
        return dataService.save(report);
    }

    /**
     * Терминал критичной тропы: снимки «после» плюс {@code COMPLETED}.
     *
     * <p>Внешний снимок читается ПОСЛЕ снятия риска намеренно: остаточный
     * риск частичного снятия виден именно в нём.
     */
    @Transactional
    public AnomalyReport complete(AnomalyReport report, DealContext dealContext) {
        report.setInternalAfter(internalSnapshot(dealContext));
        report.setExternalAfter(externalSnapshot(dealContext));
        report.setStatus(AnomalyReport.Status.COMPLETED);
        return dataService.save(report);
    }

    /**
     * Непредвиденная ошибка обработки: текст плюс {@code ERROR}. Рёбра из
     * него автоматическим актором не проходятся — тропа отдала ход
     * человеку (docs/lifecycles/AnomalyReport.md §«Матрица переходов»).
     */
    @Transactional
    public AnomalyReport fail(AnomalyReport report, String message) {
        report.setMessage(StringUtils.abbreviate(message, MESSAGE_MAX_LENGTH));
        report.setStatus(AnomalyReport.Status.ERROR);
        return dataService.save(report);
    }

    /** Класс отчёта по ступени сигнала: снимает ли реакция принятый риск. */
    private static AnomalyReport.Severity severityOf(HoldSignal signal) {
        return isTrue(signal.tearsDownRisk())
                ? AnomalyReport.Severity.CRITICAL
                : AnomalyReport.Severity.NON_CRITICAL;
    }

    private static Long accountId(DealContext dealContext) {
        ExchangeAccount account = dealContext.getExchangeAccount();
        return nonNull(account) ? account.getId() : null;
    }

    private static Long instrumentId(DealContext dealContext) {
        Instrument instrument = dealContext.getInstrument();
        return nonNull(instrument) ? instrument.getId() : null;
    }

    /**
     * Снимок локального состояния: то, что известно проходу.
     *
     * <p>Сделки и инструмента у счёт-широкой тропы нет по построению, и
     * разыменование их здесь роняло бы отчёт ровно там, ради
     * наблюдаемости чего он и заводится.
     *
     * <p>Ноги берутся обходом траншей — донорского поля агрегата ядро не
     * читает.
     */
    private String internalSnapshot(DealContext dealContext) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Deal deal = dealContext.getDeal();
        if (nonNull(deal)) {
            Position position = deal.livePosition();
            snapshot.put("dealId", deal.getId());
            snapshot.put("dealInternalId", deal.getInternalId());
            snapshot.put("dealStatus", deal.getStatus());
            snapshot.put("positionLiveRisk", nonNull(position) && isTrue(position.hasLiveRisk()));
            snapshot.put("orderIds", legIds(deal));
        }
        Instrument instrument = dealContext.getInstrument();
        if (nonNull(instrument)) {
            snapshot.put("instrumentId", instrument.getId());
            snapshot.put("instrumentExternalId", instrument.getExternalId());
            snapshot.put("instrumentStatus", instrument.getStatus());
        }
        ExchangeAccount account = dealContext.getExchangeAccount();
        if (nonNull(account)) {
            snapshot.put("exchangeAccountId", account.getId());
            snapshot.put("exchangeAccountInternalId", account.getInternalId());
            snapshot.put("exchangeAccountSafetyRung", account.getSafetyRung());
        }
        return writeJson(snapshot);
    }

    private static List<Long> legIds(Deal deal) {
        return emptyIfNull(deal.getTranches()).stream()
                .flatMap(tranche -> emptyIfNull(tranche.getOrders()).stream())
                .map(Order::getId)
                .collect(Collectors.toList());
    }

    /**
     * Снимок внешнего состояния по инструменту прохода: позиция и живые
     * заявки на стороне площадки. Схема снимка открытая — поля доливаются
     * без переформатирования.
     *
     * <p>У счёт-широкой тропы инструмента нет, и добывать нечего — снимок
     * остаётся пустым, а не роняет отчёт.
     */
    private String externalSnapshot(DealContext dealContext) {
        Instrument instrument = dealContext.getInstrument();
        ExchangeAccount account = dealContext.getExchangeAccount();
        if (isNull(instrument) || isNull(account)) {
            return null;
        }
        String externalInstrumentId = instrument.getExternalId();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("instrumentExternalId", externalInstrumentId);
        snapshot.put("position", readExchange(externalInstrumentId, () ->
                exchangeOperationsClient.getPosition(account.getInternalId(), externalInstrumentId)));
        snapshot.put("pendingOrders", readExchange(externalInstrumentId, () ->
                exchangeOperationsClient.getPendingOrders(account.getInternalId(), externalInstrumentId)));
        return writeJson(snapshot);
    }

    /**
     * Чтение площадки best-effort: сбой чтения отчёт не валит — иначе
     * недоступность источника отнимала бы наблюдаемость ровно тогда, когда
     * она нужнее всего. Маркер отказа остаётся в снимке.
     */
    private Object readExchange(String externalInstrumentId, Supplier<Object> read) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            log.error("Anomaly external snapshot read failed instId={}", externalInstrumentId, e);
            return Map.of("readError", e.getClass().getSimpleName());
        }
    }

    /** Сериализация снимка best-effort: её сбой отчёт тоже не валит. */
    private String writeJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Anomaly snapshot serialization failed snapshotKeys={}", snapshot.keySet(), e);
            return null;
        }
    }
}
