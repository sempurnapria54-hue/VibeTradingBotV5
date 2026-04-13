package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.balance.BalanceContainer;
import com.example.tradingbot.mapping.BalanceContainerMapper;
import com.example.tradingbot.persistence.model.balance.BalanceContainerEntity;
import com.example.tradingbot.persistence.repository.BalanceContainerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BalanceContainerDataService {

    private final BalanceContainerRepository balanceContainerRepository;
    private final BalanceContainerMapper balanceContainerMapper;

    public Optional<BalanceContainer> findByExchangeId(Long exchangeId) {
        return balanceContainerRepository.findByExchangeId(exchangeId)
                                         .map(balanceContainerMapper::toDomain);
    }

    public Optional<BalanceContainer> findByExchangeIdWithBalances(Long exchangeId) {
        return balanceContainerRepository.findByExchangeIdWithBalances(exchangeId)
                                         .map(balanceContainerMapper::toDomain);
    }

    @Transactional
    public BalanceContainer save(BalanceContainer balanceContainer) {
        BalanceContainerEntity entity = balanceContainerMapper.toEntity(balanceContainer);
        BalanceContainerEntity saved = balanceContainerRepository.save(entity);
        return balanceContainerMapper.toDomain(saved);
    }
}
