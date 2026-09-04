package com.example.tradingbot.domain.command.action;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.ActionKind;
import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.SystemActionType;
import com.example.tradingbot.domain.command.TargetEntityType;
import com.example.tradingbot.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Выдаёт следующую команду системного действия за проход и ревизует живые
 * системные исполнения сделки.
 *
 * <p><b>Точек входа две.</b> Первая ({@code next}) отвечает про одно
 * названное действие; вторая ({@code reviseLiveExecutions}) ревизует ВСЕ
 * живые системные исполнения сделки. Вторая нужна потому, что закрытие
 * неактуального исполнения по определению происходит там, где действие
 * больше никто не называет, — то есть первая до него не доходит.
 *
 * <p><b>Стадия выводится из подтверждённого факта</b> и полем не хранится:
 * у действий финализации звено выбирается по durable-факту самой сделки, у
 * добычи звено называет вызывающая сторона — она и есть тот, кто прочитал
 * факт, породивший надобность (перегрузка со звеном). Ожидающее повтора
 * исполнение отдаёт команду только по наступлении времени следующей
 * попытки и перевзводится в запланированное: звено выводится заново.
 * Завершённые и отказавшие строки терминальны — новая надобность заводит
 * новую строку.
 *
 * <p><b>Статус исполнения сервис сам не пишет</b>, кроме двух исключений,
 * и оба про актуальность, а не про исход звена: перевзвод ожидающего
 * повтора и закрытие исполнения, ставшего неактуальным.
 *
 * <p>См. docs/components/SystemActionExecutor.md.
 */
@Service
@RequiredArgsConstructor
public class SystemActionExecutor {

    private final DealActionStateDataService dealActionStateDataService;

    /**
     * Следующая команда системного действия, звено которого выводится из
     * подтверждённых фактов сделки (финализация и терминалы, а у добычи —
     * первая ненакрытая сущность). Пусто — исполнять нечего: звено ждёт
     * времени следующей попытки либо надобности больше нет.
     */
    public Optional<ServiceCommand> next(SystemActionType type, DealContext dealContext, DealTranche tranche) {
        return derivedLink(type, dealContext, tranche)
                .flatMap(link -> anchor(type, dealContext, tranche)
                        .map(state -> command(link.getType(), dealContext, state, link.getPayload())));
    }

    /**
     * Следующая команда системного действия с ЯВНО названным звеном.
     * Применяется к добыче: сущность, чей факт понадобился, называет
     * вызывающая сторона — она прочитала durable-факт надобности, и
     * повторять этот разбор здесь значило бы завести второго носителя
     * одного решения.
     */
    public Optional<ServiceCommand> next(SystemActionType type, DealContext dealContext, DealTranche tranche,
                                         ServiceCommandType link, ServiceCommandPayload payload) {
        return anchor(type, dealContext, tranche)
                .map(state -> command(link, dealContext, state, payload));
    }

    /**
     * Ревизия живых системных исполнений: исполнение, чья надобность
     * снята фактами сделки, закрывается как неактуальное. Иначе живая
     * строка держала бы частичный ключ и не давала завести новую, а её
     * бюджет тратился бы на надобность, которой больше нет.
     */
    public void reviseLiveExecutions(DealContext dealContext) {
        for (DealActionState state : dealContext.getActionStates()) {
            if (isFalse(state.isSystem()) || isFalse(state.isLive())) {
                continue;
            }
            if (isTrue(stale(state, dealContext))) {
                state.setStatus(DealActionStateStatus.SKIPPED);
                dealActionStateDataService.save(state);
            }
        }
    }

    // ------------------------------------------------------------------
    // Строка исполнения
    // ------------------------------------------------------------------

    /**
     * Строка исполнения под надобность: живая — та же, ожидающая повтора
     * — перевзведённая по наступлении времени, иначе новая. Пусто ровно в
     * одном случае: повтор ещё ждёт backoff.
     */
    private Optional<DealActionState> anchor(SystemActionType type, DealContext dealContext, DealTranche tranche) {
        DealActionState live = dealContext.liveSystemActionState(type, tranche).orElse(null);
        if (isNull(live)) {
            return Optional.of(createPlanned(type, dealContext, tranche));
        }
        if (isFalse(DealActionStateStatus.RETRY_PENDING.equals(live.getStatus()))) {
            return Optional.of(live);
        }
        if (isFalse(retryDue(live))) {
            return Optional.empty();
        }
        live.setStatus(DealActionStateStatus.PLANNED);
        return Optional.of(dealActionStateDataService.save(live));
    }

    private Boolean retryDue(DealActionState state) {
        return isNull(state.getNextRetryAt())
                || isFalse(OffsetDateTime.now(ZoneOffset.UTC).isBefore(state.getNextRetryAt()));
    }

    /**
     * Новая строка. Транш и номер эпизода непусты только у потраншевого
     * системного действия — консолидации входа транша; у остальных трёх
     * типов действие агрегатное, и транша у него нет ни одного.
     */
    private DealActionState createPlanned(SystemActionType type, DealContext dealContext, DealTranche tranche) {
        DealActionState state = new DealActionState();
        state.setDealId(dealContext.getDeal().getId());
        state.setActionKind(ActionKind.SYSTEM);
        state.setSystemActionType(type);
        state.setDealTrancheId(nonNull(tranche) ? tranche.getId() : null);
        state.setTrancheEpisodeSeq(nonNull(tranche) ? tranche.getEpisodeSeq() : null);
        state.setTargetEntityType(TargetEntityType.DEAL);
        state.setTargetEntityId(dealContext.getDeal().getId());
        state.setStatus(DealActionStateStatus.PLANNED);
        DealActionState saved = dealActionStateDataService.save(state);
        dealContext.register(saved);
        return saved;
    }

    // ------------------------------------------------------------------
    // Звено: вывод из подтверждённого факта
    // ------------------------------------------------------------------

    /**
     * Звено действия по durable-фактам сделки; пусто — надобности больше
     * нет: факт, который действие пишет, уже стои́т. Терминальное звено
     * эмитится только после того, как отработало предшествующее: у
     * штатной тропы — расчёт числа перед терминальным ребром, у аварийной
     * — вход в ошибочное состояние перед аварийным терминалом.
     */
    private Optional<SystemActionLink> derivedLink(SystemActionType type, DealContext dealContext,
                                                   DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        return switch (type) {
            case FINALIZE_DEAL_ENTRY_ACTION -> entryLink(tranche);
            case FINALIZE_DEAL_EXIT_ACTION -> exitLink(deal);
            case FINALIZE_DEAL_ERROR_ACTION -> errorLink(deal);
            case REFRESH_DEAL_CONTEXT_ACTION -> fetchLink(dealContext);
        };
    }

    /**
     * Консолидация входа надобна, пока транш стои́т в отправленном
     * входе: статус подтверждённого входа пишет само звено, и по нему же
     * читается, что оно отработало.
     */
    private Optional<SystemActionLink> entryLink(DealTranche tranche) {
        if (isNull(tranche) || isFalse(DealTranche.Status.ENTRY_SUBMITTED.equals(tranche.getStatus()))) {
            return Optional.empty();
        }
        return Optional.of(new SystemActionLink(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND, null));
    }

    /**
     * Штатная тропа: сперва расчёт числа, затем терминальное ребро;
     * терминал — конец надобности.
     *
     * <p><b>Тропа закрытия БЕЗ входа звена расчёта не имеет</b>: считать
     * там не по чему, ноль есть результат тропы, и пишет его само
     * терминальное ребро (docs/models/domain/aggregate/Deal.md §«Расчёт и
     * запись — писателей три»).
     */
    private Optional<SystemActionLink> exitLink(Deal deal) {
        if (isTrue(deal.isTerminal())) {
            return Optional.empty();
        }
        boolean computeDue = isTrue(deal.positionObserved()) && isNull(deal.getResultProfit());
        return Optional.of(new SystemActionLink(computeDue
                ? ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND
                : ServiceCommandType.MARK_DEAL_CLOSED_COMMAND, null));
    }

    /** Аварийная тропа: вход в ошибочное состояние и аварийный терминал — два отдельных звена. */
    private Optional<SystemActionLink> errorLink(Deal deal) {
        if (isTrue(deal.isTerminal())) {
            return Optional.empty();
        }
        return Optional.of(new SystemActionLink(Deal.Status.ERROR.equals(deal.getStatus())
                ? ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND
                : ServiceCommandType.MARK_DEAL_ERROR_COMMAND, null));
    }

    /**
     * Первая сущность сделки, чей факт ещё не подтверждён: живая заявка,
     * живая условная заявка, живая позиция. Состав цикла един для всех
     * троп; звено движений средств здесь не выводится — оно эмитится
     * только на выходной тропе и называется её обработчиком явно.
     */
    private Optional<SystemActionLink> fetchLink(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        Order liveOrder = firstLiveOrder(deal);
        if (nonNull(liveOrder)) {
            return Optional.of(new SystemActionLink(ServiceCommandType.REFRESH_ORDER_COMMAND,
                    new RefreshOrderCommandPayload(liveOrder.getId())));
        }
        AlgoOrder liveAlgo = firstLiveAlgoOrder(deal);
        if (nonNull(liveAlgo)) {
            return Optional.of(new SystemActionLink(ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND,
                    new RefreshAlgoOrderCommandPayload(liveAlgo.getId())));
        }
        return Optional.of(new SystemActionLink(ServiceCommandType.REFRESH_POSITION_COMMAND, null));
    }

    private Order firstLiveOrder(Deal deal) {
        List<Order> live = deal.liveOrders();
        return isEmpty(live) ? null : live.getFirst();
    }

    private AlgoOrder firstLiveAlgoOrder(Deal deal) {
        List<AlgoOrder> live = deal.liveAlgoOrders();
        return isEmpty(live) ? null : live.getFirst();
    }

    // ------------------------------------------------------------------
    // Актуальность исполнения
    // ------------------------------------------------------------------

    /**
     * Исполнение стало неактуальным: сделка терминальна (делать по ней
     * больше нечего), либо потраншевое исполнение пережило свой эпизод —
     * транш терминален или уже переоткрыт следующим номером.
     */
    private Boolean stale(DealActionState state, DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isTrue(deal.isTerminal())) {
            return true;
        }
        if (isFalse(state.isTrancheLevel())) {
            return false;
        }
        DealTranche tranche = trancheOf(deal, state.getDealTrancheId());
        if (isNull(tranche)) {
            return true;
        }
        return isTrue(tranche.isTerminal())
                || isFalse(java.util.Objects.equals(tranche.getEpisodeSeq(), state.getTrancheEpisodeSeq()));
    }

    private DealTranche trancheOf(Deal deal, Long trancheId) {
        if (isEmpty(deal.getTranches())) {
            return null;
        }
        return deal.getTranches().stream()
                .filter(candidate -> java.util.Objects.equals(trancheId, candidate.getId()))
                .findFirst()
                .orElse(null);
    }

    private ServiceCommand command(ServiceCommandType type, DealContext dealContext, DealActionState state,
                                   ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(dealContext.getDeal().getId())
                .dealActionStateId(state.getId())
                .payload(payload)
                .build();
    }
}
