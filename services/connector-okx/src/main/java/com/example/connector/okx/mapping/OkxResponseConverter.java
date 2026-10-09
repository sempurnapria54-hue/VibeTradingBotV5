package com.example.connector.okx.mapping;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.connector.okx.exception.ExternalInvariantViolationException;
import com.example.connector.okx.exception.ExternalStatusException;
import com.example.connector.okx.util.OkxConstants;
import com.example.connector.okx.util.OkxParse;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.AccountMode;
import com.example.tradingbot.domain.model.core.balance.PositionMode;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Конвертеры сырых OKX-строк для мапперов снапшотов (подбор MapStruct по
 * типам / qualifiedByName): empty→null, epoch-ms→время UTC, abs/знак pos
 * позиции. Общий для OrderMapper/PositionMapper/AlgoOrderMapper.
 */
@Slf4j
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

    /**
     * Числовое поле записи закрытия НЕСОБЫТИЙНОЙ природы (слагаемые
     * тождества: pnl, fee, fundingFee, liqPenalty) → BigDecimal; пустое
     * значение — НОЛЬ, а не пустота: величина существует всегда, а «не
     * было события» означает нулевую величину. Конвенция применяется ДО
     * проверки обязательности контракта записи — иначе валидация границы
     * реджектила бы каждую сделку без фондирования
     * (docs/models/integrations/okx/PositionsHistoryOkxResponse.md).
     */
    @Named("okxNonEventDecimal")
    public BigDecimal nonEventDecimal(String value) {
        BigDecimal parsed = okxDecimal(value);
        return isNull(parsed) ? BigDecimal.ZERO : parsed;
    }

    /**
     * Накопленное финансирование записи закрытия → доменная ИЗДЕРЖКА:
     * знак снимается. В тождестве источника слагаемое знаковое («сколько
     * прибавилось к результату»), домен же хранит уплаченное
     * фондирование положительным. Нормализация делается ЗДЕСЬ и только
     * здесь — иначе каждый потребитель нормализовал бы сам, и знак
     * разошёлся бы молча (docs/models/mapping/PositionCloseResult.md).
     */
    @Named("okxFundingCost")
    public BigDecimal fundingCost(String value) {
        return nonEventDecimal(value).negate();
    }

    /**
     * Направление закрытой позиции (long/short) → доменное значение.
     * Резолв идёт в слое интеграции, симметрично живой ноге; пустое либо
     * незнакомое значение — нарушение биржевого инварианта, а не
     * пустота: без направления материализация эпизода отказала бы ТИХО
     * (docs/models/mapping/PositionCloseResult.md).
     */
    @Named("okxCloseDirection")
    public Position.Direction closeDirection(String rawDirection) {
        if (isBlank(rawDirection)) {
            throw new ExternalInvariantViolationException(
                    "positions-history: направление закрытой позиции пусто — эпизод не материализуем");
        }
        return Arrays.stream(Position.Direction.values())
                .filter(value -> value.name().equalsIgnoreCase(rawDirection.trim()))
                .findFirst()
                .orElseThrow(() -> new ExternalInvariantViolationException(
                        "positions-history: направление вне перечня: " + rawDirection));
    }

    /**
     * Режим маржи записи позиции ({@code mgnMode}: isolated / cross) →
     * доменный режим маржи (docs/models/mapping/Position.md).
     *
     * <p><b>Пустоты здесь нет, и это не строгость, а форма контракта:</b>
     * площадка отдаёт режим у каждой записи, и запись без него либо с
     * незнакомым словом — значение вне формы контракта, то есть отказ
     * чтения. Пустота на его месте выдала бы чужую запись за нашу либо
     * нашу за чужую: режим — единственное, чем площадка их различает.
     */
    @Named("okxMarginModeToDomain")
    public Instrument.MarginMode marginModeToDomain(String mgnMode) {
        if (OkxConstants.TD_MODE_ISOLATED.equals(mgnMode)) {
            return Instrument.MarginMode.ISOLATED;
        }
        if (OkxConstants.MGN_MODE_CROSS.equals(mgnMode)) {
            return Instrument.MarginMode.CROSS;
        }
        throw new ExternalInvariantViolationException("position: режим маржи записи вне словаря контракта: "
                + mgnMode);
    }

    /**
     * Доменный режим маржи → слово площадки для тела закрытия позиции.
     *
     * <p><b>Пусто — режим контура (adapter-константа isolated):</b> так
     * закрывается всякая наша позиция. Непустой режим приносит только
     * снятие риска вне графа сделок, закрывая запись иного режима её
     * собственным режимом (docs/models/mapping/Position.md §«OKX
     * close-position request body»;
     * docs/integrations/okx/rules/adapter-constants.md).
     */
    @Named("okxMarginMode")
    public String marginMode(Instrument.MarginMode marginMode) {
        if (isNull(marginMode)) {
            return OkxConstants.TD_MODE_ISOLATED;
        }
        return switch (marginMode) {
            case ISOLATED -> OkxConstants.TD_MODE_ISOLATED;
            case CROSS -> OkxConstants.MGN_MODE_CROSS;
        };
    }

    /** BigDecimal → OKX строка суммы (plain, без экспоненты); null→null. */
    public String okxAmount(BigDecimal value) {
        return isNull(value) ? null : value.toPlainString();
    }

    /**
     * Откат трейлинга в процентах → доля площадки ({@code callbackRatio}):
     * {@code 0.8} процента уезжают {@code "0.008"}. Без перевода откат
     * уехал бы в сто раз шире объявленного, и трейлинг не защищал бы
     * ничего (docs/models/mapping/AlgoOrder.md §«OKX request mapping —
     * дополнения»).
     */
    @Named("okxRatioFromPercents")
    public String ratioFromPercents(BigDecimal percents) {
        return isNull(percents)
                ? null
                : percents.movePointLeft(OkxConstants.PERCENTS_TO_RATIO_POINT_SHIFT).toPlainString();
    }

    /** OKX sCode → принят ли запрос (успех). */
    @Named("okxAckSuccess")
    public Boolean ackSuccess(String code) {
        return Objects.equals(OkxConstants.SUCCESS_CODE, code);
    }

    /**
     * Доменная сторона заявки → словарь площадки (buy/sell).
     *
     * <p>Здесь и проходит граница: доменный перечень внутрь площадки не
     * уезжает, литерал площадки в домен не попадает
     * (.claude/rules/codestyle.md §«Слои»).
     */
    @Named("okxOrderSide")
    public String orderSide(Order.Side side) {
        return isNull(side) ? null : side.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Словарь площадки → доменная сторона заявки.
     *
     * <p><b>Отсутствие и неразрешимость — разные вещи.</b> Поля нет в
     * ответе — пусто, обновление стороны не происходит. Поле есть, а
     * значение неизвестно — контролируемое исключение, как у резолверов
     * внешнего статуса: молча записанное {@code null} в обязательное поле
     * денежной тропы означало бы заявку без стороны, а это состояние, из
     * которого не выводится ни направление, ни экспозиция
     * (docs/rules/controlled-exchange-exceptions.md).
     */
    @Named("okxOrderSideToDomain")
    public Order.Side orderSideToDomain(String raw) {
        if (isNull(raw)) {
            return null;
        }
        if (OkxConstants.SIDE_BUY.equals(raw)) {
            return Order.Side.BUY;
        }
        if (OkxConstants.SIDE_SELL.equals(raw)) {
            return Order.Side.SELL;
        }
        throw new ExternalStatusException(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS, raw);
    }

    /** Доменное направление algo → OKX side (buy/sell). */
    @Named("okxAlgoSide")
    public String algoSide(AlgoOrder.Direction direction) {
        return isNull(direction) ? null : direction.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Эхо стороны условной заявки (buy/sell) → доменное направление: перевод
     * словаря площадки делает граница, сверяет эхо добыча в ядре
     * (docs/models/mapping/AlgoOrder.md §«Сверка эха»).
     *
     * <p><b>Молчание и неразрешимость — разные вещи.</b> Поле пусто — пусто,
     * и сверка по стороне не запускается. Значение вне словаря — нарушение
     * формы контракта: пустота на его месте выдала бы ответ, нарушивший
     * контракт, за молчание и погасила бы сверку
     * (docs/rules/controlled-exchange-exceptions.md).
     */
    @Named("okxAlgoSideToDomain")
    public AlgoOrder.Direction algoSideToDomain(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        if (OkxConstants.SIDE_BUY.equals(raw)) {
            return AlgoOrder.Direction.BUY;
        }
        if (OkxConstants.SIDE_SELL.equals(raw)) {
            return AlgoOrder.Direction.SELL;
        }
        throw new ExternalInvariantViolationException("algo order: сторона вне словаря контракта: " + raw);
    }

    /** Внутренний тип trigger-цены → OKX тип (last/index/mark). */
    @Named("okxTriggerType")
    public String triggerType(AlgoOrder.TriggerPriceType type) {
        return isNull(type) ? null : type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * OKX эхо базы триггера (slTriggerPxType/tpTriggerPxType:
     * last/index/mark) → доменный тип — у обеих форм защиты, встроенной и
     * отдельной условной заявки. Пустое эхо даёт пустой тип: молчание
     * источника — недобытый факт, а не разрешение подставить умолчание.
     * Значение вне перечня тоже пусто — сверку базы запускает только
     * распознанное эхо
     * (docs/models/mapping/Order.md §«AttachedAlgoOrder (attached protection)»;
     * docs/models/mapping/AlgoOrder.md).
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

    /**
     * Режим счёта площадки ({@code acctLv}) → доменный режим счёта
     * (docs/integrations/okx/contracts/account-config.md).
     *
     * <p><b>Значение вне словаря даёт пустоту, а не угаданный режим</b>
     * (docs/models/mapping/Balance.md): пустой режим в свежем снимке
     * преконтроль читает как режим вне контура, то есть ошибка выходит в
     * запрещающую сторону. Отказом чтения оно не является — снимок средств
     * без режима остаётся годным для прочих проверок, а неизвестность режима
     * запирает действия ровно тем кодом, который её и называет.
     */
    @Named("okxAccountMode")
    public AccountMode accountMode(String acctLv) {
        if (isBlank(acctLv)) {
            return null;
        }
        return switch (acctLv.trim()) {
            case OkxConstants.ACCOUNT_LEVEL_SPOT -> AccountMode.SPOT;
            case OkxConstants.ACCOUNT_LEVEL_FUTURES -> AccountMode.FUTURES;
            case OkxConstants.ACCOUNT_LEVEL_MULTI_CURRENCY_MARGIN -> AccountMode.MULTI_CURRENCY_MARGIN;
            case OkxConstants.ACCOUNT_LEVEL_PORTFOLIO_MARGIN -> AccountMode.PORTFOLIO_MARGIN;
            default -> {
                logUnknownMode("acctLv", acctLv);
                yield null;
            }
        };
    }

    /**
     * Режим позиций площадки ({@code posMode}) → доменный режим позиций;
     * значение вне словаря — пустота, довод — {@link #accountMode}.
     */
    @Named("okxPositionMode")
    public PositionMode positionMode(String posMode) {
        if (isBlank(posMode)) {
            return null;
        }
        return switch (posMode.trim()) {
            case OkxConstants.POS_MODE_NET -> PositionMode.NET;
            case OkxConstants.POS_MODE_LONG_SHORT -> PositionMode.LONG_SHORT;
            default -> {
                logUnknownMode("posMode", posMode);
                yield null;
            }
        };
    }

    /** Значение режима вне словаря оставляет след в логе: пустота одна, а причин у неё две. */
    private void logUnknownMode(String field, String raw) {
        log.warn("OKX account config value outside the dictionary [account-config] {}={}", field, raw);
    }
}
