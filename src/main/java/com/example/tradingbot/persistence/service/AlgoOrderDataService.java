package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.search_params.AlgoOrderSearchParams;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.persistence.model.algo_order.AlgoOrderEntity;
import com.example.tradingbot.persistence.repository.AlgoOrderRepository;
import com.example.tradingbot.persistence.specification.AlgoOrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.Constant.ErrorCode.ALGO_ORDER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AlgoOrderDataService {

    private final AlgoOrderRepository algoOrderRepository;
    private final AlgoOrderMapper mapper;

    @Transactional
    public AlgoOrder save(AlgoOrder algoOrder) {
        AlgoOrderEntity data = mapper.domainToData(algoOrder);
        AlgoOrderEntity saved = algoOrderRepository.save(data);
        return mapper.dataToDomain(saved);
    }

    public AlgoOrder findRequiredByInternalId(String internalId) {
        return algoOrderRepository.findByInternalId(internalId)
                                  .map(mapper::dataToDomain)
                                  .orElseThrow(() -> new RuntimeException(ALGO_ORDER_NOT_FOUND));
    }

    public List<AlgoOrder> findByInstrumentId(Long instrumentId) {
        return algoOrderRepository.findAllByInstrumentId(instrumentId)
                                  .stream()
                                  .map(mapper::dataToDomain)
                                  .toList();
    }

    public Page<AlgoOrder> search(AlgoOrderSearchParams params, Pageable pageable) {
        Page<AlgoOrderEntity> data = algoOrderRepository.findAll(
                AlgoOrderSpecification.bySearchParams(params),
                pageable
        );
        return mapper.dataToDomain(data);
    }

}
