package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.mapping.ExchangeMapper;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.tradingbot.util.Constant.ErrorCode.EXCHANGE_ALREADY_EXISTS;
import static com.example.tradingbot.util.Constant.ErrorCode.EXCHANGE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ExchangeDataService {

    private final ExchangeRepository repository;
    private final ExchangeMapper mapper;

    @Transactional
    public Exchange save(Exchange exchange) {
        ExchangeEntity exchangeEntity = mapper.domainToData(exchange);
        ExchangeEntity saved = repository.save(exchangeEntity);
        return mapper.dataToDomain(saved);
    }

    public Exchange findRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                         .map(mapper::dataToDomain)
                         .orElseThrow(() -> new RuntimeException(EXCHANGE_NOT_FOUND));
    }

    public List<Exchange> findAll() {
        List<ExchangeEntity> data = repository.findAll();
        return mapper.dataToDomain(data);
    }

    public void checkNotExists(String name) {
        if (repository.existsByName(name)) {
            throw new RuntimeException(EXCHANGE_ALREADY_EXISTS);
        }
    }

    public Exchange findRequiredById(Long id) {
        return repository.findById(id)
                         .map(mapper::dataToDomain)
                         .orElseThrow(() -> new RuntimeException(EXCHANGE_NOT_FOUND));
    }
}
