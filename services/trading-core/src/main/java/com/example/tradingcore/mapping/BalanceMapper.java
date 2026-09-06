package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingcore.persistence.model.BalanceContainerEntity;
import com.example.tradingcore.persistence.model.BalanceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг снимка средств domain ↔ persistence
 * (docs/models/mapping/Balance.md).
 *
 * <p>Форм источника здесь нет: с площадкой говорит коннектор, ядро видит
 * доменную модель. Валютные строки — дочерние, оркестрацию их замещения
 * держит {@code BalanceContainerDataService}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BalanceMapper {

    BalanceContainerEntity domainToPersistence(BalanceContainer container);

    BalanceContainer persistenceToDomain(BalanceContainerEntity entity);

    BalanceEntity domainToPersistence(Balance balance);

    Balance persistenceToDomain(BalanceEntity entity);
}
