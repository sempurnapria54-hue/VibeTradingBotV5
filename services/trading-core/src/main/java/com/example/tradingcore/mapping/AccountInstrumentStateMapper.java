package com.example.tradingcore.mapping;

import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.persistence.model.AccountInstrumentStateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Граница domain ↔ persistence для состояния счёта на инструменте.
 * Переносит только данные; ленивую материализацию строки держит
 * {@code AccountInstrumentStateDataService}.
 *
 * <p>Имена полей совпадают, перечни конвертирует MapStruct через
 * {@code name()} / {@code valueOf} — явных {@code @Mapping} здесь нет
 * (.claude/rules/codestyle.md §Маппинг).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountInstrumentStateMapper {

    AccountInstrumentState persistenceToDomain(AccountInstrumentStateEntity entity);

    AccountInstrumentStateEntity domainToPersistence(AccountInstrumentState state);
}
