package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.util.Constants;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Держит последовательность полной реакции ступени — блокировки со
 * снятием живого риска (docs/components/SafetyHoldCoordinator.md). Сам
 * ничего не исполняет напрямую.
 *
 * <p><b>Последовательность:</b> жёсткий статус объекта блокировки → отчёт
 * {@code CREATED} со снимком «до» → снятие живого риска → терминал отчёта
 * со снимком «после» либо эскалация → каскад активных сделок радиуса в
 * ошибочное состояние.
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
 * риска приоритетнее записи о нём.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyHoldCoordinator {

    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final AnomalyReportService anomalyReportService;
    private final KillSwitchService killSwitchService;
    private final DealDataService dealDataService;

    /** Объекты радиуса, по которым полная реакция идёт прямо сейчас. */
    private final Set<String> runningObjects = ConcurrentHashMap.newKeySet();

    /**
     * Поднять полную реакцию по сигналу. Идемпотентно по статусу объекта
     * блокировки.
     *
     * @return ступень ПЕРЕСТАВИЛАСЬ этим вызовом; поглощённый сигнал даёт
     *         {@code false}, и событие подъёма по нему не пишется — писать
     *         его значило бы объявлять фактом ход, которого не было
     */
    public Boolean react(HoldSignal signal, DealContext dealContext) {
        return react(signal, dealContext, false);
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
    public Boolean react(HoldSignal signal, DealContext dealContext, Boolean teardownRetry) {
        if (HoldScope.EXCHANGE_ACCOUNT.equals(signal.getScope())) {
            return reactExchangeAccount(signal, dealContext, teardownRetry);
        }
        return reactInstrument(signal, dealContext, teardownRetry);
    }

    private Boolean reactInstrument(HoldSignal signal, DealContext dealContext, Boolean teardownRetry) {
        Long accountId = dealContext.getExchangeAccount().getId();
        Long instrumentId = dealContext.getInstrument().getId();
        if (isFalse(accountInstrumentStateDataService.raiseRung(accountId, instrumentId,
                Instrument.SafetyRung.TRADE_BLOCKED))) {
            if (isFalse(teardownRetry)) {
                absorbed(signal, dealContext);
                return false;
            }
            return retryTeardown(signal, dealContext,
                    () -> killSwitchService.fireInstrument(dealContext),
                    () -> dealDataService.cascadeInstrumentToError(accountId, instrumentId));
        }
        runReaction(signal, dealContext, () -> killSwitchService.fireInstrument(dealContext));
        dealDataService.cascadeInstrumentToError(accountId, instrumentId);
        return true;
    }

    private Boolean reactExchangeAccount(HoldSignal signal, DealContext dealContext, Boolean teardownRetry) {
        Long accountId = dealContext.getExchangeAccount().getId();
        if (isFalse(exchangeAccountDataService.raiseRung(accountId,
                ExchangeAccount.SafetyRung.TRADE_BLOCKED))) {
            if (isFalse(teardownRetry)) {
                absorbed(signal, dealContext);
                return false;
            }
            return retryTeardown(signal, dealContext,
                    () -> killSwitchService.fireExchangeAccount(accountId),
                    () -> dealDataService.cascadeAccountToError(accountId));
        }
        runReaction(signal, dealContext, () -> killSwitchService.fireExchangeAccount(accountId));
        dealDataService.cascadeAccountToError(accountId);
        return true;
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
     * @return снятие риска ЗАПУЩЕНО этим вызовом
     */
    private Boolean retryTeardown(HoldSignal signal, DealContext dealContext,
                                  Supplier<Boolean> killSwitch, Runnable cascade) {
        String key = objectKey(signal, dealContext);
        if (isFalse(runningObjects.add(key))) {
            log.warn("Full reaction is already running on the object: teardown retry skipped key={}", key);
            return false;
        }
        try {
            runReaction(signal, dealContext, killSwitch);
            cascade.run();
        } finally {
            runningObjects.remove(key);
        }
        return true;
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
            // поглощает штатно.
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
