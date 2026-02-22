package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.example.tradingbot.util.Constant.ErrorCode.EXCHANGE_ALREADY_EXISTS;
import static com.example.tradingbot.util.Constant.ErrorCode.EXCHANGE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ExchangeDataService {

    private final ExchangeRepository exchangeRepository;

    @Transactional
    public ExchangeEntity save(ExchangeEntity exchangeEntity) {
        return exchangeRepository.save(exchangeEntity);
    }

    public Optional<ExchangeEntity> findById(Long id) {
        return exchangeRepository.findById(id);
    }

    public Optional<ExchangeEntity> findRequiredById(Long id) {
        return exchangeRepository.findById(id);
    }

    public Optional<ExchangeEntity> findByName(String name) {
        return exchangeRepository.findByName(name);
    }

    public ExchangeEntity findRequiredByInternalId(String internalId) {
        return exchangeRepository.findByInternalId(internalId)
                .orElseThrow(() -> new RuntimeException(EXCHANGE_NOT_FOUND));
    }

    public Long getRequiredIdByInternalId(String internalId) {
        return exchangeRepository.findIdByInternalId(internalId)
                .orElseThrow(() -> new RuntimeException(EXCHANGE_NOT_FOUND));
    }

    public List<ExchangeEntity> findAll() {
        return exchangeRepository.findAll();
    }

    public void checkNotExists(String name) {
        if (exchangeRepository.existsByName(name)) {
            throw new RuntimeException(EXCHANGE_ALREADY_EXISTS);
        }
    }
}
