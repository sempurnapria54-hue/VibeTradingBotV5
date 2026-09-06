package com.example.tradingcore.persistence.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingcore.mapping.TradeFeeRateMapper;
import com.example.tradingcore.persistence.model.TradeFeeRateEntity;
import com.example.tradingcore.persistence.repository.TradeFeeRateRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для ставки комиссии
 * (docs/models/domain/other/TradeFeeRate.md).
 *
 * <p><b>Правило истории живёт здесь, а не в маппере:</b> значение группы
 * изменилось — новая строка, совпало — подтверждение последней на месте
 * (счётчик и метка времени источника). Это доменное решение записи, а
 * маппер только переносит данные.
 *
 * <p><b>Актуальная строка группы — последняя по идентификатору.</b>
 * Признака актуальности у модели нет ни одного, а история требует
 * нескольких строк на группу, поэтому порядок и есть резолв; инвариант
 * «одна актуальная» держит писатель — запись идёт одним ходом.
 */
@Service
@RequiredArgsConstructor
public class TradeFeeRateDataService {

    private static final PageRequest LATEST = PageRequest.of(0, 1);

    private final TradeFeeRateRepository repository;
    private final TradeFeeRateMapper mapper;

    /** Актуальная ставка группы счёта; пусто — группа ещё не наблюдалась. */
    @Transactional(readOnly = true)
    public Optional<TradeFeeRate> findCurrent(Long exchangeAccountId, String externalInstrumentType,
                                              String externalFeeGroupId) {
        return latestEntity(exchangeAccountId, externalInstrumentType, externalFeeGroupId)
                .map(mapper::persistenceToDomain);
    }

    /**
     * Записывает наблюдение группы: совпало значение — подтверждаем
     * последнюю строку, изменилось (или строки нет) — заводим новую с
     * первым подтверждением.
     */
    @Transactional
    public TradeFeeRate record(TradeFeeRate observed) {
        Optional<TradeFeeRateEntity> current = latestEntity(observed.getExchangeAccountId(),
                observed.getExternalInstrumentType(), observed.getExternalFeeGroupId());
        if (current.isPresent()) {
            TradeFeeRate stored = mapper.persistenceToDomain(current.get());
            if (isTrue(stored.sameValueAs(observed.getExternalTakerFeeRate(), observed.getExternalMakerFeeRate()))) {
                stored.confirm(observed.getExternalModifiedAt(), observed.getExternalFeeLevel());
                TradeFeeRateEntity entity = current.get();
                entity.setRefreshCount(stored.getRefreshCount());
                entity.setExternalModifiedAt(stored.getExternalModifiedAt());
                entity.setExternalFeeLevel(stored.getExternalFeeLevel());
                return mapper.persistenceToDomain(repository.save(entity));
            }
        }
        if (isNull(observed.getRefreshCount())) {
            observed.setRefreshCount(1L);
        }
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(observed)));
    }

    private Optional<TradeFeeRateEntity> latestEntity(Long exchangeAccountId, String externalInstrumentType,
                                                      String externalFeeGroupId) {
        List<TradeFeeRateEntity> rows = repository
                .findByExchangeAccountIdAndExternalInstrumentTypeAndExternalFeeGroupIdOrderByIdDesc(
                        exchangeAccountId, externalInstrumentType, externalFeeGroupId, LATEST);
        return rows.stream().findFirst();
    }
}
