package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.fee.TradeFeeRateEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeFeeRateRepository extends JpaRepository<TradeFeeRateEntity, Long> {

    /**
     * Актуальная строка группы — последняя по id: правило истории заводит
     * новую строку при смене значения, и старые остаются. Окно
     * ограничивается пейджингом, а не отдаётся целиком.
     */
    List<TradeFeeRateEntity> findByExchangeIdAndExternalInstrumentTypeAndExternalFeeGroupIdOrderByIdDesc(
            Long exchangeId, String externalInstrumentType, String externalFeeGroupId, Pageable pageable);
}
