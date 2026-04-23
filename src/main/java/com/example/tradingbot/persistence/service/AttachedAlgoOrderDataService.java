package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.mapping.AttachedAlgoOrderMapper;
import com.example.tradingbot.persistence.model.deal.order.AttachedAlgoOrderEntity;
import com.example.tradingbot.persistence.repository.AttachedAlgoOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachedAlgoOrderDataService {

    private final AttachedAlgoOrderRepository attachedAlgoOrderRepository;
    private final AttachedAlgoOrderMapper attachedAlgoOrderMapper;

    public List<AttachedAlgoOrder> findAllByOrderId(Long orderId) {
        return attachedAlgoOrderRepository.findAllByOrderId(orderId)
                                          .stream()
                                          .map(attachedAlgoOrderMapper::dataToDomain)
                                          .toList();
    }

    public List<AttachedAlgoOrder> findAllByInstrumentIdAndStatuses(Long instrumentId, Set<String> statuses) {
        return attachedAlgoOrderRepository.findAllByInstrumentIdAndStatuses(instrumentId, statuses)
                                          .stream()
                                          .map(attachedAlgoOrderMapper::dataToDomain)
                                          .toList();
    }

    @Transactional
    public AttachedAlgoOrder save(AttachedAlgoOrder attachedAlgoOrder) {
        AttachedAlgoOrderEntity entity = attachedAlgoOrderMapper.domainToData(attachedAlgoOrder);
        AttachedAlgoOrderEntity saved = attachedAlgoOrderRepository.save(entity);
        return attachedAlgoOrderMapper.dataToDomain(saved);
    }
}
