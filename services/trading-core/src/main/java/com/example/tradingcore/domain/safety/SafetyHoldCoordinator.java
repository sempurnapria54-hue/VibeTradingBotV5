package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealStatusEdgeService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.util.Constants;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Держит последовательность полной реакции ступени — блокировки со
 * снятием живого риска (docs/components/SafetyHoldCoordinator.md). Сам
 * ничего не исполняет напрямую.
 *
 * <p><b>Последовательность:</b> жёсткий статус объекта блокировки вместе
 * со своим фактом подъёма → отчёт {@code CREATED} со снимком «до» →
 * снятие живого риска → терминал отчёта со снимком «после» либо
 * эскалация → каскад активных сделок радиуса в ошибочное состояние.
 *
 * <p><b>Ступень и её факт применяет {@link HoldRungEdgeService} — одной
 * транзакцией.</b> Координатор транзакции не открывает, поэтому запись
 * факта рядом с вызовом легла бы отдельной транзакцией — уже после
 * внешних вызовов снятия риска и всего каскада
 * (docs/architecture/contracts.md §«У каждого класса события назван
 * писатель, и он же писатель решения»).
 *
 * <p><b>Статус — первым, и это одновременно гейт и анкер.</b> Повторный
 * сигнал на объект, уже стоящий в этой ступени, снятия риска повторно не
 * гоняет. Тот же анкер закрывает и параллельный прогон: подъём
 * гардирован стоящей ступенью в самом запросе, поэтому второй вызов по
 * тому же объекту переход не применяет и реакции не начинает — двух
 * одновременных прогонов снятия риска по одному объекту не бывает
 * (docs/spec/manual-halt.json, величина {@code killSwitchRun}).
 *
 * <p><b>Мягкие формы сюда не доходят:</b> мягкий запрет входов исполняет
 * сам сервис блокировки. До координатора доходит только полная реакция.
 *
 * <p><b>Доведение недоделанного построено, и право на него ровно одно.</b>
 * Из-под поглощения выведен единственный случай — явный вызов держателя в
 * полном режиме, пока живой риск на радиусе не погашен
 * (docs/spec/manual-halt.json, {@code riskTeardownRetry}); признак
 * происхождения несёт сам вызов, и приносит его ручная поверхность
 * (docs/rules/manual-halt.md). Автоматический сигнал права на доведение
 * не имеет по построению: периодический детектор непогашенного риска слал
 * бы аварийное закрытие каждым тиком, — поэтому у него поглощается всякий
 * повтор.
 *
 * <p><b>Наружу реакция исключений не пробрасывает:</b> сбой обработки
 * фиксируется в отчёте, проход живёт. Журнал реакцию не гейтит — снятие
 * риска приоритетнее записи о нём (дом клаузы —
 * docs/rules/error-handling-policy.md §«Отказ журнального носителя реакцию
 * не гейтит»).
 *
 * <p><b>Отказ самого перехода ступени — названное исключение из этого.</b>
 * Ребро подъёма атомарно, поэтому отказ записи факта откатывает и
 * переход, и уходит вызывающему: применённого на этом шаге нет, ловит
 * отказ перехват прохода, а сигнал повторяется ближайшим тиком
 * (docs/components/SafetyHoldCoordinator.md §«Политика отказов»).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyHoldCoordinator {

    private final AnomalyReportService anomalyReportService;
    private final KillSwitchService killSwitchService;
    private final DealDataService dealDataService;
    private final DealStatusEdgeService dealStatusEdgeService;
    private final HoldRungEdgeService holdRungEdgeService;
    private final HardRungShutdownReasonResolver hardRungShutdownReasonResolver;

    /** Объекты радиуса, по которым полная реакция идёт прямо сейчас. */
    private final Set<String> runningObjects = ConcurrentHashMap.newKeySet();

    /**
     * Поднять полную реакцию по сигналу. Идемпотентно по статусу объекта
     * блокировки.
     *
     * <p><b>Исхода реакция не отдаёт, и это не потеря операнда.</b>
     * Единственным его читателем был писатель события, стоявший НАД
     * переходом; писатель стои́т теперь на самом переходе и о ходе реакции
     * не спрашивает (docs/components/HoldService.md).
     */
    public void react(HoldSignal signal, DealContext dealContext) {
        react(signal, dealContext, false);
    }

    /**
     * Та же реакция с правом на <b>доведение недоделанного</b>.
     *
     * <p><b>Право есть только у явного вызова держателя</b>
     * (docs/rules/manual-halt.md §«Идемпотентность наследуется, а не
     * обходится»). Автоматический сигнал такого права не имеет по
     * построению: периодический детектор непогашенного риска слал бы
     * аварийное закрытие каждым тиком.
     *
     * <p><b>Доведение идёт под исключающим ключом объекта радиуса.</b>
     * Пока риск не погашен, работу находят ОБА конкурирующих вызова, и без
     * сериализации оба ушли бы в снятие риска по одному живому риску;
     * стоящий статус здесь анкером уже не служит — он для того и обойдён.
     *
     * @param teardownRetry вызов держателя, доводящий неподтверждённое
     *                      снятие риска
     */
    public void react(HoldSignal signal, DealContext dealContext, Boolean teardownRetry) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(signal.getScope())) {
            reactExchangeAccount(signal, dealContext, teardownRetry);
            return;
        }
        reactInstrument(signal, dealContext, teardownRetry);
    }

    private void reactInstrument(HoldSignal signal, DealContext dealContext, Boolean teardownRetry) {
        Long accountId = dealContext.getExchangeAccount().getId();
        Long instrumentId = dealContext.getInstrument().getId();
        if (isFalse(holdRungEdgeService.raise(signal, dealContext))) {
            if (isFalse(teardownRetry)) {
                absorbed(signal, dealContext);
                return;
            }
            retryTeardown(signal, dealContext,
                    () -> killSwitchService.fireInstrument(dealContext),
                    () -> cascadeInstrumentToError(accountId, instrumentId));
            return;
        }
        runReaction(signal, dealContext, () -> killSwitchService.fireInstrument(dealContext));
        cascadeInstrumentToError(accountId, instrumentId);
    }

    private void reactExchangeAccount(HoldSignal signal, DealContext dealContext, Boolean teardownRetry) {
        Long accountId = dealContext.getExchangeAccount().getId();
        if (isFalse(holdRungEdgeService.raise(signal, dealContext))) {
            if (isFalse(teardownRetry)) {
                absorbed(signal, dealContext);
                return;
            }
            retryTeardown(signal, dealContext,
                    () -> killSwitchService.fireExchangeAccount(accountId),
                    () -> cascadeAccountToError(accountId));
            return;
        }
        runReaction(signal, dealContext, () -> killSwitchService.fireExchangeAccount(accountId));
        cascadeAccountToError(accountId);
    }

    /**
     * Доведение недоделанного: ступень уже стои́т, а риск не погашен —
     * снятие риска гоняется ЗАНОВО. Без этой тропы состояние не имело бы
     * выхода вовсе: снятие холда отвергается предусловием живого риска, а
     * всякий следующий сигнал гасится анкером
     * (docs/rules/manual-halt.md §«Выход из ступени сворачивания при
     * неподтверждённом kill-switch»).
     *
     * <p><b>Идущая реакция служит тем же анкером, что и стоящий
     * статус:</b> второй вызов её не дублирует и второго снятия риска не
     * запускает.
     *
     * <p><b>Факта подъёма эта тропа не производит, и это не исключение из
     * привязки класса события к писателю:</b> ступень на объекте уже
     * стои́т, перехода нет — а писатель стои́т на переходе
     * (docs/rules/manual-halt.md §«Идемпотентность наследуется, а не
     * обходится»).
     */
    private void retryTeardown(HoldSignal signal, DealContext dealContext,
                               Supplier<Boolean> killSwitch, Runnable cascade) {
        String key = objectKey(signal, dealContext);
        if (isFalse(runningObjects.add(key))) {
            log.warn("Full reaction is already running on the object: teardown retry skipped key={}", key);
            return;
        }
        try {
            runReaction(signal, dealContext, killSwitch);
            cascade.run();
        } finally {
            runningObjects.remove(key);
        }
    }

    /**
     * Первый ход энфорсмента жёсткой ступени биржевого радиуса: активные
     * сделки счёта уводятся в ошибочное состояние
     * (docs/rules/error-handling-policy.md §«Жёсткая ступень энфорсится
     * непрерывно, а не одним ходом»).
     */
    private void cascadeAccountToError(Long accountId) {
        cascadeToError(dealDataService.findCascadeSourceOnAccount(accountId),
                HoldScope.EXCHANGE_ACCOUNT);
    }

    /** Тот же ход радиусом пары «счёт, инструмент». */
    private void cascadeInstrumentToError(Long accountId, Long instrumentId) {
        cascadeToError(dealDataService.findCascadeSourceOnPair(accountId, instrumentId),
                HoldScope.INSTRUMENT);
    }

    /**
     * Каскад идёт <b>тем же ребром энфорсмента, что применяет проход</b>:
     * статус, резолвленная причина и факт остановки — одной транзакцией на
     * сделку (docs/lifecycles/Deal.md §«Причина выхода из штатного
     * ведения»).
     *
     * <p><b>Причину резолвит тот же читатель, что и у шага прохода</b>
     * ({@link HardRungShutdownReasonResolver}), а не радиус поднимаемого
     * сигнала. Радиус сигнала называет, ЧТО поднято; поле сделки
     * отвечает, под какой стоящей ступенью она встала, — и на сделке под
     * обеими ступенями это разные ответы.
     *
     * <p><b>Радиус собственной реакции — последний резерв, а не операнд
     * первого выбора.</b> Ступень, снятую держателем между подъёмом и
     * каскадом, каскад уже не увидит, а риск сделки к этому моменту
     * погашен шагом 3; пропустить её нельзя — шаг прохода её не подберёт,
     * а расхождение экспозиции поднимет ступень на ВЕСЬ счёт. Поэтому
     * такая сделка уводится причиной радиуса, реакцию исполнявшего.
     * Стоящая ступень резерва старше, и расхождение двух затребователей
     * этим не возвращается: резерв доходит только до сделки, под которой
     * не стои́т НИ ОДНОЙ ступени, а там читать нечего обоим.
     *
     * <p><b>Одним bulk-запросом каскад не идёт, и довод не в удобстве.</b>
     * Запрос по радиусу пишет статус без причины, а гард ребра причины
     * требует активного статуса — то есть уведённая сделка durable-ответа
     * «почему» не получила бы уже никогда, и факта остановки о ней не
     * производилось бы вовсе. Цена названа: сделка, ставшая активной между
     * чтением популяции и ребром, этим ходом не уводится — её подберёт шаг
     * энфорсмента ближайшего прохода, потому что каскад есть первый ход
     * энфорсмента, а не весь (docs/components/SafetyHoldCoordinator.md).
     *
     * <p><b>Отказ одной сделки каскад не останавливает:</b> сделки
     * независимы, и оставленное подберёт тот же шаг прохода.
     */
    private void cascadeToError(List<Deal> deals, HoldScope reactionScope) {
        Map<Long, Deal.ShutdownReason> reasons = hardRungShutdownReasonResolver.resolveForFirstMove(
                deals.stream().map(Deal::getId).collect(Collectors.toList()), reactionScope);
        for (Deal deal : deals) {
            Deal.ShutdownReason reason = reasons.get(deal.getId());
            try {
                dealStatusEdgeService.enforceHardRung(deal, reason);
            } catch (RuntimeException e) {
                log.error("Hard rung cascade failed on a deal, it is left to the next pass dealId={} reason={}",
                        deal.getId(), reason, e);
            }
        }
    }

    /**
     * Ключ объекта радиуса. Собственный, а не защита джобы: это не
     * джоба, и общий с ней ключ означал бы, что ручной вызов и тик
     * вытесняют друг друга (docs/rules/manual-halt.md §«Параллельный
     * вызов»).
     */
    private String objectKey(HoldSignal signal, DealContext dealContext) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(signal.getScope())) {
            return "account:" + dealContext.getExchangeAccount().getId();
        }
        return "pair:" + dealContext.getExchangeAccount().getId()
                + ":" + dealContext.getInstrument().getId();
    }

    /**
     * Поглощённый сигнал: ступень уже стои́т, смена статуса и снятие риска
     * не гоняются — <b>но строка остаётся</b>
     * (docs/rules/error-handling-policy.md §«Идемпотентность реакции и
     * идемпотентность отчёта — разные ключи»). Второе основание несёт свой
     * машинный код, и без строки различимость оснований пропадала бы ровно
     * там, где нужна: контур стои́т, а почему — в данных нет. Дедуп держит
     * ключ состояния, не гард перехода, поэтому повтор того же основания
     * строки не множит.
     *
     * <p>Снятия риска эта тропа не гоняет: права на доведение
     * недоделанного у автоматического сигнала нет.
     */
    private void absorbed(HoldSignal signal, DealContext dealContext) {
        try {
            anomalyReportService.journalState(dealContext, signal, null);
        } catch (RuntimeException e) {
            log.error("Journal of an absorbed safety signal failed scope={} code={}",
                    signal.getScope(), signal.getCode(), e);
        }
    }

    private void runReaction(HoldSignal signal, DealContext dealContext, Supplier<Boolean> killSwitch) {
        AnomalyReport report = openSafely(signal, dealContext);
        advanceSafely(report, AnomalyReport.Status.IN_PROGRESS);
        try {
            Boolean closeConfirmed = killSwitch.get();
            advanceSafely(report, AnomalyReport.Status.KILL_SWITCH_EXECUTED);
            completeOrEscalate(report, signal, dealContext, closeConfirmed);
        } catch (RuntimeException e) {
            log.error("Safety hold kill-switch failed scope={}", signal.getScope(), e);
            failSafely(report, e.getMessage());
        }
    }

    /**
     * Терминал отчёта — только по <b>подтверждённому</b> снятию риска.
     *
     * <p>Не подтверждено на инструментном радиусе — эскалация на счётный:
     * неустранимый остаток означает, что интеграции нельзя доверять, а
     * радиус ущерба неизвестен, и реакция соразмеряется с неизвестностью
     * (docs/rules/error-handling-policy.md §«Нарушение контракта
     * интеграции: радиус неизвестен по построению»).
     *
     * <p>На счётном радиусе эскалировать некуда — отчёт остаётся
     * незакрытым, и это <b>исход по назначению</b>: незакрытая строка и
     * есть требуемый след неподтверждённой реакции. Продолжение у него
     * одно и оно названо — доведение по вызову держателя.
     */
    private void completeOrEscalate(AnomalyReport report, HoldSignal signal, DealContext dealContext,
                                    Boolean closeConfirmed) {
        if (isTrue(closeConfirmed)) {
            completeSafely(report, dealContext);
            return;
        }
        if (HoldScope.INSTRUMENT.equals(signal.getScope())) {
            log.error("Instrument kill-switch is not confirmed flat; escalating to the account radius"
                    + " exchangeAccountId={}", dealContext.getExchangeAccount().getId());
            // Эскалация — АВТОМАТИЧЕСКИЙ ход: права на доведение
            // недоделанного у неё нет, и стоящая счётная ступень её
            // поглощает штатно. Свой факт подъёма счётной ступени она
            // пишет тем же ребром, что и всякая другая тропа: ход
            // состоялся, и объявлять его нечем иным.
            reactExchangeAccount(
                    HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_KILL_SWITCH_RESIDUAL), dealContext,
                    false);
            return;
        }
        log.warn("Account kill-switch is not confirmed flat; the anomaly report is kept open"
                + " anomalyReportId={}", isNull(report) ? null : report.getId());
    }

    /**
     * Создать отчёт best-effort. Сбой создания снятия риска <b>не</b>
     * подавляет: возвращаем пусто, дальнейшие записи журнала становятся
     * холостыми, ход продолжается.
     */
    private AnomalyReport openSafely(HoldSignal signal, DealContext dealContext) {
        try {
            return anomalyReportService.open(dealContext, signal);
        } catch (RuntimeException e) {
            log.error("Anomaly report open failed scope={} code={}", signal.getScope(), signal.getCode(), e);
            return null;
        }
    }

    private void advanceSafely(AnomalyReport report, AnomalyReport.Status status) {
        if (isNull(report)) {
            return;
        }
        try {
            anomalyReportService.advance(report, status);
        } catch (RuntimeException e) {
            log.error("Anomaly report advance failed anomalyReportId={} status={}", report.getId(), status, e);
        }
    }

    private void completeSafely(AnomalyReport report, DealContext dealContext) {
        if (isNull(report)) {
            return;
        }
        try {
            anomalyReportService.complete(report, dealContext);
        } catch (RuntimeException e) {
            log.error("Anomaly report complete failed anomalyReportId={}", report.getId(), e);
        }
    }

    private void failSafely(AnomalyReport report, String message) {
        if (isNull(report)) {
            return;
        }
        try {
            anomalyReportService.fail(report, message);
        } catch (RuntimeException e) {
            log.error("Anomaly report fail-write failed anomalyReportId={}", report.getId(), e);
        }
    }
}
