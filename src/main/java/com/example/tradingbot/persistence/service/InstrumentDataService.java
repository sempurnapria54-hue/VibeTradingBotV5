package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.persistence.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.example.tradingbot.util.Constant.ErrorCode.INSTRUMENT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InstrumentDataService {

    private final InstrumentRepository instrumentRepository;

    @Transactional
    public InstrumentEntity save(InstrumentEntity instrumentEntity) {
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

    public InstrumentEntity findRequiredByExchangeIdAndName(Long exchangeId, String name) {
        return instrumentRepository.findByExchangeIdAndName(exchangeId, name)
                .orElseThrow(() -> new RuntimeException(INSTRUMENT_NOT_FOUND));
    }

    public Optional<InstrumentEntity> findByExchangeIdAndInstId(Long exchangeId, String instId) {
        return instrumentRepository.findByExchangeIdAndInstId(exchangeId, instId);
    }

//    public List<InstrumentEntity> findAll() {
//        return instrumentRepository.findAll();
//    }

    public List<InstrumentEntity> findAllByExchangeId(Long exchangeId) {
        return instrumentRepository.findAllByExchangeId(exchangeId);
    }

    public boolean existsById(Long id) {
        return instrumentRepository.existsById(id);
    }
}
