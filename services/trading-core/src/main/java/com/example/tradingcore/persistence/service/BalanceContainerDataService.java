package com.example.tradingcore.persistence.service;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingcore.mapping.BalanceMapper;
import com.example.tradingcore.persistence.model.BalanceContainerEntity;
import com.example.tradingcore.persistence.model.BalanceEntity;
import com.example.tradingcore.persistence.repository.BalanceContainerRepository;
import com.example.tradingcore.persistence.repository.BalanceRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для снимка средств счёта.
 *
 * <p><b>Валютные строки ЗАМЕЩАЮТСЯ целиком, а не сливаются.</b> Источник
 * отдаёт состояние, а не дельту: строка, исчезнувшая из ответа, означает
 * «валюты больше нет», и слияние оставило бы её жить навсегда
 * (docs/models/domain/core/BalanceContainer.md).
 */
@Service
@RequiredArgsConstructor
public class BalanceContainerDataService {

    private final BalanceContainerRepository repository;
    private final BalanceRepository balanceRepository;
    private final BalanceMapper mapper;

    /** Снимок средств счёта; пусто — команда добычи по счёту ещё не отрабатывала. */
    @Transactional(readOnly = true)
    public Optional<BalanceContainer> findByExchangeAccountId(Long exchangeAccountId) {
        return repository.findByExchangeAccountId(exchangeAccountId).map(this::withBalances);
    }

    /** Приземляет снимок вместе с его валютными строками — одной транзакцией. */
    @Transactional
    public BalanceContainer save(BalanceContainer container) {
        BalanceContainerEntity saved = repository.save(mapper.domainToPersistence(container));
        BalanceContainer result = mapper.persistenceToDomain(saved);
        result.setBalances(replaceBalances(saved.getId(), container.getBalances()));
        return result;
    }

    private BalanceContainer withBalances(BalanceContainerEntity entity) {
        BalanceContainer container = mapper.persistenceToDomain(entity);
        container.setBalances(balanceRepository.findByBalanceContainerId(entity.getId()).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList()));
        return container;
    }

    /**
     * Замещение набора валютных строк. Пустой набор в ответе означает «у
     * счёта нет средств ни в одной валюте» — прежние строки снимаются и
     * тогда: пропуск удаления сделал бы пустой ответ неотличимым от
     * недобытого.
     */
    private List<Balance> replaceBalances(Long containerId, List<Balance> balances) {
        balanceRepository.deleteByBalanceContainerId(containerId);
        if (isNull(balances)) {
            return null;
        }
        return emptyIfNull(balances).stream()
                .map(balance -> saveBalance(containerId, balance))
                .collect(Collectors.toList());
    }

    private Balance saveBalance(Long containerId, Balance balance) {
        BalanceEntity entity = mapper.domainToPersistence(balance);
        entity.setId(null);
        entity.setBalanceContainerId(containerId);
        return mapper.persistenceToDomain(balanceRepository.save(entity));
    }
}
