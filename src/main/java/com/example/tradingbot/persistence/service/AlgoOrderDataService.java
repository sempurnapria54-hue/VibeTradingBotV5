package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.persistence.repository.AlgoOrderRepository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.Constant.ErrorCode.ALGO_ORDER_NOT_FOUND;
import static com.example.tradingbot.util.Constant.ErrorCode.EXCHANGE_NOT_FOUND;

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


    public List<AlgoOrderEntity> findAllByExchangeIdAndInstrumentIdAndExchangeAlgoOrderId(
            Long exchangeId,
            Long instrumentId,
            String exchangeAlgoOrderId
    ) {
        return algoOrderRepository.findAllByExchangeIdAndInstrumentIdAndExchangeAlgoOrderId(exchangeId, instrumentId, exchangeAlgoOrderId);
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

    public AlgoOrderEntity findRequiredByExchangeIdAndInstrumentIdAndClientAlgoOrderId(Long exchangeId,
                                                                                       Long instrumentId,
                                                                                       String clientAlgoOrderId) {
        return algoOrderRepository.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchangeId, instrumentId, clientAlgoOrderId)
                .orElseThrow(() -> new RuntimeException(ALGO_ORDER_NOT_FOUND));
    }
}
