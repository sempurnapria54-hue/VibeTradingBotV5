package com.example.tradingbot.domain.command;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.persistence.service.DealFinalizationStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Эмитит одну финализационную команду (FINALIZE_DEAL_* / MARK_DEAL_*) по
 * статусу DealFinalizationState(deal, type): отсутствует/PENDING → команда;
 * RETRY_PENDING → команда, только если наступил nextRetryAt (иначе ждём
 * backoff); COMPLETED → empty (сделано); FAILED → empty (исчерпано — сделку
 * на ошибочную тропу выводит handler, DEAL-Q2). Материализует строку (upsert
 * по UNIQUE(deal_id, type)) при первом обращении; команда привязывается к
 * dealFinalizationStateId. Strategy-action команды эмитят per-type
 * StrategyActionExecutor'ы (docs/decisions/fsm-execution-layering.md).
 */
@Service
@RequiredArgsConstructor
public class DealFinalizationCommandFactory {

    private final DealFinalizationStateDataService dealFinalizationStateDataService;

    public Optional<ServiceCommand> finalizationCommand(DealFinalizationType type, DealContext dealContext) {
        Long dealId = dealContext.getDeal().getId();
        DealFinalizationState existing = dealFinalizationStateDataService.findByDealIdAndType(dealId, type).orElse(null);
        if (nonNull(existing) && isFalse(retryDue(existing))) {
            return Optional.empty();
        }
        DealFinalizationState state = nonNull(existing) ? existing : createPending(dealId, type);
        return Optional.of(finalizationServiceCommand(type, dealId, state.getId()));
    }

    /** Команду по финализации можно эмитить: PENDING — да; RETRY_PENDING — по наступлении nextRetryAt; COMPLETED/FAILED — нет. */
    private Boolean retryDue(DealFinalizationState state) {
        return switch (state.getStatus()) {
            case PENDING -> true;
            case RETRY_PENDING -> isNull(state.getNextRetryAt())
                    || isFalse(OffsetDateTime.now(ZoneOffset.UTC).isBefore(state.getNextRetryAt()));
            case COMPLETED, FAILED -> false;
        };
    }

    private DealFinalizationState createPending(Long dealId, DealFinalizationType type) {
        DealFinalizationState state = new DealFinalizationState();
        state.setDealId(dealId);
        state.setType(type);
        state.setStatus(DealFinalizationStateStatus.PENDING);
        return dealFinalizationStateDataService.save(state);
    }

    private ServiceCommand finalizationServiceCommand(DealFinalizationType type, Long dealId,
                                                      Long finalizationStateId) {
        return ServiceCommand.builder()
                .type(toCommandType(type))
                .dealId(dealId)
                .dealFinalizationStateId(finalizationStateId)
                .build();
    }

    private ServiceCommandType toCommandType(DealFinalizationType type) {
        return switch (type) {
            case FINALIZE_ENTRY -> ServiceCommandType.FINALIZE_DEAL_ENTRY;
            case FINALIZE_EXIT -> ServiceCommandType.FINALIZE_DEAL_EXIT;
            case MARK_CLOSED -> ServiceCommandType.MARK_DEAL_CLOSED;
            case MARK_ERROR -> ServiceCommandType.MARK_DEAL_ERROR;
        };
    }
}
