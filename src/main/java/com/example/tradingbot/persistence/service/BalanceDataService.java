package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.balance.Balance;
import com.example.tradingbot.mapping.BalanceMapper;
import com.example.tradingbot.persistence.model.balance.BalanceEntity;
import com.example.tradingbot.persistence.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BalanceDataService {

    private final BalanceRepository balanceRepository;
    private final BalanceMapper balanceMapper;

    public Optional<Balance> findByExchangeIdAndCurrency(Long exchangeId, String currency) {
        return balanceRepository.findByExchangeIdAndCurrency(exchangeId, currency)
                                .map(balanceMapper::toDomain);
    }

    @Transactional
    public Balance save(Balance balance) {
        BalanceEntity data = balanceMapper.toEntity(balance);
        BalanceEntity saved = balanceRepository.save(data);
        return balanceMapper.toDomain(saved);
    }
}
