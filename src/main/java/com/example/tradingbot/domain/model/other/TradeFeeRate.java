package com.example.tradingbot.domain.model.other;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Актуальная ставка комиссии КОМИССИОННОЙ ГРУППЫ счёта на бирже —
 * источник прогнозной комиссии в риск-сайзинге. Одна строка на группу:
 * ставка есть атрибут комиссионного уровня счёта, а не справочника
 * инструмента, и инструмент несёт лишь ключ своей группы. См.
 * docs/models/domain/other/TradeFeeRate.md.
 *
 * <p><b>Ось группы — сырые значения источника.</b> Доменная проекция
 * схлопывает нераспознанные типы в одно значение, поэтому две разные
 * группы источника столкнулись бы в одном ключе, а правило истории
 * «значение изменилось — новая строка» выродилось бы в переключение
 * между ставками двух групп.
 *
 * <p><b>Ставки хранятся строкой</b> — названное исключение из численной
 * конвенции (docs/rules/persistence-representation.md): значение хранится
 * в форме источника, и эта форма — часть факта.
 */
@Getter
@Setter
@NoArgsConstructor
public class TradeFeeRate extends Auditable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Биржа-владелец ставки. */
    private Long exchangeId;

    /** Ось группы: сырой тип инструмента источника. */
    private String externalInstrumentType;

    /** Ось группы: сырой идентификатор комиссионной группы. */
    private String externalFeeGroupId;

    /** Доменная проекция типа для runtime-логики; осью группы не является. */
    private InstrumentExternalRules.InstrumentType instrumentType;

    /**
     * Ставка taker как ИЗДЕРЖКА: знак биржевой конвенции снят при
     * маппинге, комиссия положительна, ребейт отрицателен.
     */
    private String externalTakerFeeRate;

    /** Ставка maker, та же конвенция. Прогноз сайзинга берёт taker как худший случай. */
    private String externalMakerFeeRate;

    /** Комиссионный уровень счёта на момент чтения — датчик оси тира. */
    private String externalFeeLevel;

    /**
     * Время данных источника на момент последнего подтверждения строки.
     * Метка ответа, а не значение группы: обновляется на месте и новой
     * строки не порождает.
     */
    private OffsetDateTime externalModifiedAt;

    /**
     * Счётчик подтверждений строки: делает движение времени изменения
     * записанным, а не побочным.
     */
    private Long refreshCount;

    /** Ставка taker числом; пусто — ставки нет, и прогноз комиссии невычислим. */
    public BigDecimal takerFeeRate() {
        return toDecimal(externalTakerFeeRate);
    }

    /** Ставка maker числом; пусто — ставки нет. */
    public BigDecimal makerFeeRate() {
        return toDecimal(externalMakerFeeRate);
    }

    /**
     * Строка описывает ту же группу, что и наблюдение: ключ — пара СЫРЫХ
     * значений источника.
     */
    public Boolean sameGroupAs(String instrumentType, String feeGroupId) {
        return Objects.equals(externalInstrumentType, instrumentType)
                && Objects.equals(externalFeeGroupId, feeGroupId);
    }

    /**
     * Значение группы совпало с наблюдённым — строку подтверждаем, а не
     * заводим новую. Сравнение идёт по СТРОКАМ источника: сырое хранение
     * без объявленного предиката сравнения дало бы тихий отказ на
     * эквивалентных, но по-разному записанных числах, поэтому предикат
     * объявлен здесь и нормализует запись числа.
     */
    public Boolean sameValueAs(String takerFeeRate, String makerFeeRate) {
        return equalRates(externalTakerFeeRate, takerFeeRate)
                && equalRates(externalMakerFeeRate, makerFeeRate);
    }

    /** Подтверждение строки: счётчик растёт, метка времени источника обновляется на месте. */
    public void confirm(OffsetDateTime observedAt, String feeLevel) {
        this.refreshCount = isNull(refreshCount) ? 1L : refreshCount + 1;
        this.externalModifiedAt = observedAt;
        this.externalFeeLevel = feeLevel;
    }

    private Boolean equalRates(String left, String right) {
        BigDecimal leftValue = toDecimal(left);
        BigDecimal rightValue = toDecimal(right);
        if (isNull(leftValue) || isNull(rightValue)) {
            return isNull(leftValue) && isNull(rightValue);
        }
        return leftValue.compareTo(rightValue) == 0;
    }

    private BigDecimal toDecimal(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    /** Ставка есть и она резолвится числом. */
    public Boolean hasTakerFeeRate() {
        return nonNull(takerFeeRate());
    }
}
