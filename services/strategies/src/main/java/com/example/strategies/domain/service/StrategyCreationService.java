package com.example.strategies.domain.service;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.example.strategies.integration.internal.api.TradingCoreReadClient;
import com.example.strategies.integration.internal.api.model.PairCheckCoreResponse;
import com.example.strategies.mapping.StrategyApiMapper;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.util.InternalIdFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Заводит определение стратегии: проверяет его и пишет дерево.
 *
 * <p><b>Порядок несущий: чужие операнды добываются ДО записи.</b> Ссылки
 * определения и числа риск-аппетита живут у соседа, и читаются они
 * снаружи транзакции — сеть внутри неё удерживала бы соединение пула, а
 * на отказе соседа транзакция висела бы до таймаута
 * (docs/rules/strategy-validation.md §«Что проверяется на активации»,
 * .claude/work/backlog.md §«Перф-форвард»).
 *
 * <p><b>Тенант и идентичность приходят НЕ из тела.</b> Тенант — из
 * контекста вызова, идентичность присваивает этот сервис до первой
 * записи (docs/models/domain/aggregate/Strategy.md §«У каждого поля
 * контекста назван писатель и момент»).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyCreationService {

    private final StrategyDefinitionValidator validator;
    private final TradingCoreReadClient tradingCoreReadClient;
    private final TenantRiskAppetiteReader riskAppetiteReader;
    private final StrategyApiMapper mapper;
    private final StrategyDataService strategyDataService;

    /**
     * Завести определение. Отказ — единственный исход негодного входа:
     * место ошибки конфигурации — создание, а не рантайм
     * (docs/rules/strategy-validation.md §«Линия реза»).
     */
    public Strategy create(CreateStrategyApiRequest request, String tenantInternalId) {
        validateReferences(request, tenantInternalId);
        validator.validateCreate(request, riskAppetiteReader.read(tenantInternalId));
        Strategy definition = mapper.apiToDomain(request);
        definition.setInternalId(InternalIdFactory.forInternalEntity());
        definition.setTenantId(tenantInternalId);
        definition.setStatus(Strategy.Status.CREATED);
        Strategy saved = strategyDataService.saveTree(definition);
        log.info("Strategy definition created internalId={} tenant={} account={} instrument={}",
                saved.getInternalId(), saved.getTenantId(), saved.getExchangeAccountInternalId(),
                saved.getInstrumentInternalId());
        return saved;
    }

    /**
     * Ссылки определения разрешаются у соседа, и отказ адресует тот
     * конъюнкт, который ложен: «не годится» без указания, что именно,
     * автору ничего не говорит (docs/concept.md П3).
     */
    private void validateReferences(CreateStrategyApiRequest request, String tenantInternalId) {
        PairCheckCoreResponse check = tradingCoreReadClient.checkPair(tenantInternalId,
                request.getExchangeAccountInternalId(), request.getInstrumentInternalId());
        List<String> violations = new ArrayList<>();
        if (isFalse(check.accountFound())) {
            violations.add("exchangeAccountInternalId STRATEGY_ACCOUNT_NOT_FOUND: счёта с такой "
                    + "идентичностью в реестре нет");
        } else if (isFalse(check.accountBelongsToTenant())) {
            violations.add("exchangeAccountInternalId STRATEGY_ACCOUNT_NOT_FOUND: счёт принадлежит "
                    + "другому тенанту");
        }
        if (isFalse(check.instrumentFound())) {
            violations.add("instrumentInternalId STRATEGY_INSTRUMENT_NOT_FOUND: инструмента с такой "
                    + "идентичностью в каталоге нет");
        }
        if (isFalse(violations.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", violations));
        }
    }

}
