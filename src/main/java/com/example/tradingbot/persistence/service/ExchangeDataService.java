package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.example.tradingbot.util.Constant.ErrorCode.EXCHANGE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ExchangeDataService {

    private final ExchangeRepository exchangeRepository;

    @Transactional
    public ExchangeEntity save(ExchangeEntity exchangeEntity) {
        return exchangeRepository.save(exchangeEntity);
    }

    @Transactional
    public List<ExchangeEntity> saveAll(List<ExchangeEntity> exchangeEntities) {
        return exchangeRepository.saveAll(exchangeEntities);
    }

    public Optional<ExchangeEntity> findById(Long id) {
        return exchangeRepository.findById(id);
    }

    public Optional<ExchangeEntity> findByName(String name) {
        return exchangeRepository.findByName(name);
    }

    public ExchangeEntity findRequiredByName(String name) {
        return exchangeRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException(EXCHANGE_NOT_FOUND));
    }

    public List<ExchangeEntity> findAll() {
        return exchangeRepository.findAll();
    }

    public boolean existsById(Long id) {
        return exchangeRepository.existsById(id);
    }
}
