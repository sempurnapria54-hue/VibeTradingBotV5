package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.mapping.BalanceContainerMapper;
import com.example.tradingbot.persistence.model.balance.BalanceContainerEntity;
import com.example.tradingbot.persistence.repository.BalanceContainerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BalanceContainerDataService {

    private final BalanceContainerRepository balanceContainerRepository;
    private final BalanceContainerMapper balanceContainerMapper;

    public BalanceContainer findByExchangeIdWithBalances(Long exchangeId) {
        return balanceContainerRepository.findByExchangeIdWithBalances(exchangeId)
                                         .map(balanceContainerMapper::dataToDomain)
                                         .orElse(null);
    }

    @Transactional
    public BalanceContainer save(BalanceContainer balanceContainer) {
        BalanceContainerEntity entity = balanceContainerMapper.domainToData(balanceContainer);
        BalanceContainerEntity saved = balanceContainerRepository.save(entity);
        return balanceContainerMapper.dataToDomain(saved);
    }
}
