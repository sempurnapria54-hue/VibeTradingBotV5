package com.example.tradingbot.persistence.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingbot.mapping.TradeFeeRateMapper;
import com.example.tradingbot.persistence.model.fee.TradeFeeRateEntity;
import com.example.tradingbot.persistence.repository.TradeFeeRateRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link TradeFeeRate}
 * (docs/models/domain/other/TradeFeeRate.md). Держит правило истории:
 * <b>значение группы изменилось — новая строка; совпало — подтверждение
 * последней на месте</b> (счётчик и метка времени источника). Правило
 * доменное, поэтому живёт здесь, а не в маппере.
 *
 * <p>Актуальная строка группы — <b>последняя по id</b>: признака
 * актуальности у модели нет, история же требует нескольких строк на
 * группу, поэтому порядок и есть резолв.
 */
@Service
@RequiredArgsConstructor
public class TradeFeeRateDataService {

    private static final PageRequest LATEST = PageRequest.of(0, 1);

    private final TradeFeeRateRepository repository;
    private final TradeFeeRateMapper mapper;

    /** Актуальная ставка группы; пусто — группа ещё не наблюдалась. */
    @Transactional(readOnly = true)
    public Optional<TradeFeeRate> findCurrent(Long exchangeId, String externalInstrumentType,
                                              String externalFeeGroupId) {
        return latestEntity(exchangeId, externalInstrumentType, externalFeeGroupId)
                .map(mapper::persistenceToDomain);
    }

    /**
     * Записывает наблюдение группы: совпало значение — подтверждаем
     * последнюю строку, изменилось (или строки нет) — заводим новую с
     * первым подтверждением.
     */
    @Transactional
    public TradeFeeRate record(TradeFeeRate observed) {
        Optional<TradeFeeRateEntity> current = latestEntity(observed.getExchangeId(),
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

    private Optional<TradeFeeRateEntity> latestEntity(Long exchangeId, String externalInstrumentType,
                                                      String externalFeeGroupId) {
        List<TradeFeeRateEntity> rows = repository
                .findByExchangeIdAndExternalInstrumentTypeAndExternalFeeGroupIdOrderByIdDesc(
                        exchangeId, externalInstrumentType, externalFeeGroupId, LATEST);
        return rows.stream().findFirst();
    }
}
