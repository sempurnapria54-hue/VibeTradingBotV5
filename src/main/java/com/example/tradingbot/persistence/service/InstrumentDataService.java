package com.example.tradingbot.persistence.service;

import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.repository.InstrumentRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstrumentDataService {

    private final InstrumentRepository instrumentRepository;

    @Transactional
    public InstrumentEntity save(InstrumentEntity instrumentEntity) {
        return instrumentRepository.save(instrumentEntity);
    }

    @Transactional
    public InstrumentEntity create(InstrumentEntity instrumentEntity) {
        return instrumentRepository.save(instrumentEntity);
    }

    @Transactional
    public List<InstrumentEntity> saveAll(List<InstrumentEntity> instrumentEntities) {
        return instrumentRepository.saveAll(instrumentEntities);
    }

    public Optional<InstrumentEntity> findById(Long id) {
        return instrumentRepository.findById(id);
    }

    public Optional<InstrumentEntity> findByExchangeIdAndName(Long exchangeId, String name) {
        return instrumentRepository.findByExchangeIdAndName(exchangeId, name);
    }

    public Optional<InstrumentEntity> findByExchangeIdAndInstId(Long exchangeId, String instId) {
        return instrumentRepository.findByExchangeIdAndInstId(exchangeId, instId);
    }

    public List<InstrumentEntity> findAll() {
        return instrumentRepository.findAll();
    }

    public List<InstrumentEntity> findAllByExchangeId(Long exchangeId) {
        return instrumentRepository.findAllByExchangeId(exchangeId);
    }

    public boolean existsById(Long id) {
        return instrumentRepository.existsById(id);
    }
}
