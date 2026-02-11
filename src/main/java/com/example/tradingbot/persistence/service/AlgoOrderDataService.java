package com.example.tradingbot.persistence.service;

import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.repository.AlgoOrderRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlgoOrderDataService {

    private final AlgoOrderRepository algoOrderRepository;

    @Transactional
    public AlgoOrderEntity save(AlgoOrderEntity algoOrderEntity) {
        return algoOrderRepository.save(algoOrderEntity);
    }

    @Transactional
    public List<AlgoOrderEntity> saveAll(List<AlgoOrderEntity> algoOrderEntities) {
        return algoOrderRepository.saveAll(algoOrderEntities);
    }

    public List<AlgoOrderEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId) {
        return algoOrderRepository.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
    }

    public Optional<AlgoOrderEntity> findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(
        Long exchangeId,
        Long instrumentId,
        String clientAlgoOrderId
    ) {
        return algoOrderRepository.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(
            exchangeId,
            instrumentId,
            clientAlgoOrderId
        );
    }
}
