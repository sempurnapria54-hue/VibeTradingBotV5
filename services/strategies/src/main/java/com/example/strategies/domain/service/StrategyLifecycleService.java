package com.example.strategies.domain.service;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.example.strategies.integration.TradingCoreReadClient;
import com.example.strategies.integration.model.PairCheckCoreResponse;
import com.example.strategies.mapping.StrategyApiMapper;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.tradingbot.domain.event.StrategyActivatedContent;
import com.example.tradingbot.domain.event.StrategyLifecycleContent;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ведёт жизненный цикл определения: предусловия перехода и его событие
 * (docs/lifecycles/Strategy.md).
 *
 * <p><b>Актор активации до фазы 4 — держатель</b>, и это разведочный
 * режим с названным условием выхода: гейт трек-рекорда меняет АКТОРА
 * тропы, а не саму тропу (docs/lifecycles/Strategy.md §«Кто управляет»).
 * Чего режим не даёт — бэктеста вне выборки, теневого периода и порога
 * выживаемости; смягчение названо там же.
 *
 * <p><b>Порядок несущий: чужие операнды добываются ДО транзакции.</b>
 * Сама запись — переход плюс строка outbox — лежит в отдельном
 * компоненте, потому что транзакцию открывает прокси на входе в бин
 * ({@link StrategyStatusWriter}).
 */
@Service
@RequiredArgsConstructor
public class StrategyLifecycleService {

    private final StrategyDataService strategyDataService;
    private final StrategyDefinitionValidator validator;
    private final TradingCoreReadClient tradingCoreReadClient;
    private final StrategyApiMapper mapper;
    private final TenantRiskAppetiteReader riskAppetiteReader;
    private final StrategyStatusWriter statusWriter;

    /**
     * Перевести определение в целевой статус.
     *
     * <p>Активация проходит предусловия готовности; прочие переходы
     * гардов сверх матрицы не имеют. {@code CREATED} целью не бывает: он
     * системный и ставится созданием.
     */
    public Strategy applyStatus(String internalId, String tenantInternalId, Strategy.Status target) {
        Strategy definition = requireOwnDefinition(internalId, tenantInternalId);
        if (isFalse(definition.canTransitionTo(target))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "STRATEGY_TRANSITION_NOT_ALLOWED: " + definition.getStatus() + " -> " + target);
        }
        if (Objects.equals(Strategy.Status.ACTIVE, target)) {
            return activate(definition, tenantInternalId);
        }
        return statusWriter.commit(definition, target,
                new StrategyLifecycleContent(definition.getInternalId(),
                        definition.getExchangeAccountInternalId(), definition.getInstrumentInternalId()));
    }

    /**
     * Активация: предусловия готовности снаружи транзакции, переход и
     * событие — внутри.
     *
     * <p>Снимок дерева читается <b>до</b> перехода: он же поедет
     * содержимым события, и читать его повторно внутри транзакции значило
     * бы держать её на время загрузки дерева.
     */
    private Strategy activate(Strategy definition, String tenantInternalId) {
        requireNoOtherActiveOnPair(definition);
        requireResolvableReferences(definition, tenantInternalId);
        Strategy snapshot = strategyDataService.findByInternalIdWithTree(definition.getInternalId())
                .orElseThrow(() -> new IllegalStateException(
                        "Strategy tree disappeared before activation: " + definition.getInternalId()));
        validator.validateRiskInequalities(mapper.domainToApi(snapshot).getDetails(),
                riskAppetiteReader.read(tenantInternalId));
        return statusWriter.commit(snapshot, Strategy.Status.ACTIVE,
                new StrategyActivatedContent(snapshot));
    }

    /**
     * Определение тенанта; чужое читается как ненайденное — иначе
     * поверхность отвечала бы на вопрос о существовании чужой сущности.
     */
    private Strategy requireOwnDefinition(String internalId, String tenantInternalId) {
        return strategyDataService.findByInternalId(internalId)
                .filter(found -> Objects.equals(tenantInternalId, found.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Strategy not found: " + internalId));
    }

    /**
     * Инвариант «одна активная на паре» — проверкой приложения; вторым
     * носителем того же инварианта стои́т частичный уникальный индекс, и
     * он же ловит гонку двух одновременных активаций.
     */
    private void requireNoOtherActiveOnPair(Strategy definition) {
        Optional<String> active = strategyDataService.findActiveInternalIdOnPair(
                definition.getExchangeAccountInternalId(), definition.getInstrumentInternalId());
        Optional<String> foreign =
                active.filter(found -> isFalse(Objects.equals(found, definition.getInternalId())));
        if (foreign.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "STRATEGY_ACTIVE_ALREADY_EXISTS: на паре уже активна " + foreign.orElseThrow());
        }
    }

    /**
     * Ссылки определения обязаны разрешаться и на активации: счёт и
     * инструмент живут у соседей и могли выйти из контекста тенанта после
     * создания. Отвечают проекции ядра, и ошибиться они могут только в
     * запрещающую сторону (docs/rules/strategy-validation.md §«Что
     * проверяется на активации»).
     */
    private void requireResolvableReferences(Strategy definition, String tenantInternalId) {
        PairCheckCoreResponse check = tradingCoreReadClient.checkPair(tenantInternalId,
                definition.getExchangeAccountInternalId(), definition.getInstrumentInternalId());
        if (isFalse(isTrue(check.accountFound()) && isTrue(check.accountBelongsToTenant()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "STRATEGY_ACCOUNT_NOT_FOUND: счёт определения не разрешается в контексте тенанта");
        }
        if (isFalse(check.instrumentFound())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "STRATEGY_INSTRUMENT_NOT_FOUND: инструмент определения не разрешается");
        }
    }
}
