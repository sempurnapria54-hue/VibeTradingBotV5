package com.example.tradingbot.mapping;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.util.Constants;
import com.example.tradingbot.util.OkxParse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Конвертеры сырых OKX-строк для мапперов снапшотов (подбор MapStruct по
 * типам / qualifiedByName): empty→null, epoch-ms→время UTC, abs/знак pos
 * позиции. Общий для OrderMapper/PositionMapper/AlgoOrderMapper.
 */
@Component
public class OkxResponseConverter {

    /** OKX numeric строка → BigDecimal; empty→null. */
    public BigDecimal okxDecimal(String value) {
        return OkxParse.decimal(value);
    }

    /** OKX epoch-ms строка → OffsetDateTime (UTC); empty→null. */
    public OffsetDateTime okxTimeToOffset(String millis) {
        return OkxParse.offsetTime(millis);
    }

    /** OKX epoch-ms строка → Instant; empty→null. */
    public Instant okxTimeToInstant(String millis) {
        return OkxParse.instant(millis);
    }

    /** OKX pos → размер по модулю (abs); empty→null. */
    @Named("okxAbsSize")
    public BigDecimal absSize(String pos) {
        BigDecimal value = okxDecimal(pos);
        return isNull(value) ? null : value.abs();
    }

    /** OKX pos → направление по знаку (LONG/SHORT); 0/empty→null. */
    @Named("okxDirection")
    public Position.Direction direction(String pos) {
        BigDecimal value = okxDecimal(pos);
        if (isNull(value) || value.signum() == 0) {
            return null;
        }
        return value.signum() > 0 ? Position.Direction.LONG : Position.Direction.SHORT;
    }

    /** BigDecimal → OKX строка суммы (plain, без экспоненты); null→null. */
    public String okxAmount(BigDecimal value) {
        return isNull(value) ? null : value.toPlainString();
    }

    /** OKX sCode → принят ли запрос (успех). */
    @Named("okxAckSuccess")
    public Boolean ackSuccess(String code) {
        return Objects.equals(Constants.Okx.SUCCESS_CODE, code);
    }

    /** Доменное направление algo → OKX side (buy/sell). */
    @Named("okxAlgoSide")
    public String algoSide(AlgoOrder.Direction direction) {
        return isNull(direction) ? null : direction.name().toLowerCase(Locale.ROOT);
    }

    /** Внутренний тип trigger-цены → OKX тип (last/index/mark). */
    @Named("okxTriggerType")
    public String triggerType(AlgoOrder.TriggerPriceType type) {
        return isNull(type) ? null : type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * OKX эхо slTriggerPxType (last/index/mark) → доменный тип. Пустое эхо
     * даёт пустой тип: молчание источника — недобытый факт, а не разрешение
     * подставить умолчание. Значение вне перечня тоже пусто — сверку базы
     * запускает только распознанное эхо
     * (docs/models/mapping/Order.md §«AttachedAlgoOrder (attached protection)»).
     */
    @Named("okxTriggerPriceType")
    public AlgoOrder.TriggerPriceType triggerPriceType(String rawType) {
        if (isBlank(rawType)) {
            return null;
        }
        return Arrays.stream(AlgoOrder.TriggerPriceType.values())
                .filter(type -> type.name().equalsIgnoreCase(rawType.trim()))
                .findFirst()
                .orElse(null);
    }
}
