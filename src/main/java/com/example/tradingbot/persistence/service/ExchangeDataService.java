package com.example.tradingbot.persistence.service;

import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeDataService {

    private final ExchangeRepository exchangeRepository;

    @Transactional
    public ExchangeEntity save(ExchangeEntity exchangeEntity) {
        return exchangeRepository.save(exchangeEntity);
    }

    @Transactional
    public ExchangeEntity create(ExchangeEntity exchangeEntity) {
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

    public List<ExchangeEntity> findAll() {
        return exchangeRepository.findAll();
    }

    public boolean existsById(Long id) {
        return exchangeRepository.existsById(id);
    }
}
