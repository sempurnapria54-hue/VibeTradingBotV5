package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.TradeFeeRateEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeFeeRateRepository extends JpaRepository<TradeFeeRateEntity, Long> {

    /**
     * Актуальная строка группы — последняя по идентификатору: правило
     * истории заводит новую строку при смене значения, и прежние
     * остаются. Окно ограничивается пейджингом, а не отдаётся целиком
     * (.claude/rules/codestyle.md §«Выборка данных»).
     */
    List<TradeFeeRateEntity> findByExchangeAccountIdAndExternalInstrumentTypeAndExternalFeeGroupIdOrderByIdDesc(
            Long exchangeAccountId, String externalInstrumentType, String externalFeeGroupId, Pageable pageable);
}
