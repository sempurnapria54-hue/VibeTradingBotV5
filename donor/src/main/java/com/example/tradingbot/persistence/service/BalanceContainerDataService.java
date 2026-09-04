package com.example.tradingbot.persistence.service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.mapping.BalanceContainerMapper;
import com.example.tradingbot.persistence.model.balance.BalanceContainerEntity;
import com.example.tradingbot.persistence.model.balance.BalanceEntity;
import com.example.tradingbot.persistence.repository.BalanceContainerRepository;
import com.example.tradingbot.persistence.repository.BalanceRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link BalanceContainer}. Currency-
 * балансы — дочерние строки balances: при сохранении replace semantics
 * (удаление старых + вставка новых). Адресуется по exchange_id.
 */
@Service
@RequiredArgsConstructor
public class BalanceContainerDataService {

    private final BalanceContainerRepository repository;
    private final BalanceRepository balanceRepository;
    private final BalanceContainerMapper mapper;

    @Transactional
    public BalanceContainer save(BalanceContainer container) {
        BalanceContainerEntity saved = repository.save(mapper.domainToPersistence(container));
        balanceRepository.deleteByBalanceContainerId(saved.getId());
        BalanceContainer result = mapper.persistenceToDomain(saved);
        result.setBalances(saveBalances(saved.getId(), container.getBalances()));
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<BalanceContainer> findByExchangeId(Long exchangeId) {
        return repository.findByExchangeId(exchangeId).map(this::toDomainWithBalances);
    }

    private BalanceContainer toDomainWithBalances(BalanceContainerEntity entity) {
        BalanceContainer container = mapper.persistenceToDomain(entity);
        container.setBalances(loadBalances(entity.getId()));
        return container;
    }

    private List<Balance> saveBalances(Long containerId, List<Balance> balances) {
        if (isEmpty(balances)) {
            return null;
        }
        return balances.stream()
                .map(balance -> saveBalance(containerId, balance))
                .collect(Collectors.toList());
    }

    private Balance saveBalance(Long containerId, Balance balance) {
        BalanceEntity entity = mapper.domainToPersistence(balance);
        entity.setBalanceContainerId(containerId);
        return mapper.persistenceToDomain(balanceRepository.save(entity));
    }

    private List<Balance> loadBalances(Long containerId) {
        List<BalanceEntity> entities = balanceRepository.findByBalanceContainerId(containerId);
        if (isEmpty(entities)) {
            return null;
        }
        return entities.stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
