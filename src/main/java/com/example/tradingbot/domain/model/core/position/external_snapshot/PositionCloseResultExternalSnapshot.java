package com.example.tradingbot.domain.model.core.position.external_snapshot;

import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот ПОЛОЖЕНИЯ ЗАКРЫТИЯ эпизода —
 * единственное, что выходит за границу интеграции из истории позиций
 * источника. Две тропы применения: ОБНОВЛЕНИЕ (строку закрыла нога 1) и
 * МАТЕРИАЛИЗАЦИЯ (позиция впервые увидена уже закрытой). Знак
 * финансирования нормализуется ЗДЕСЬ и только здесь — домен хранит его
 * издержкой; у комиссии и штрафа знак остаётся сырым, они уходят
 * правыми операндами пар сверки.
 * См. docs/models/mapping/PositionCloseResult.md.
 */
@Value
@Builder
public class PositionCloseResultExternalSnapshot {

    /** Готовый net, посчитанный биржей. */
    BigDecimal externalRealizedPnl;

    /** Валюта net'а — проверяемый признак, не источник валюты результата сделки. */
    String externalResultCurrency;

    /** Средняя цена фактического выхода. */
    BigDecimal externalCloseAveragePrice;

    /** Сырой тип последнего закрытия источника; резолв исхода — доменный. */
    String externalCloseType;

    /** Результат до издержек — правый операнд первой пары сверки. */
    BigDecimal externalRealizedPnlGross;

    /** Знаковая комиссионная компонента, СЫРОЙ знак — вторая пара. */
    BigDecimal externalFee;

    /** Накопленное финансирование, знак НОРМАЛИЗОВАН (издержка положительна) — третья пара. */
    BigDecimal externalFundingCost;

    /** Штраф ликвидации, СЫРОЙ знак — четвёртая пара. */
    BigDecimal externalLiquidationPenalty;

    /** Биржевой идентификатор позиции — половина оси адресации записи. */
    String externalPosId;

    /** Сырой идентификатор инструмента — операнд структурной проверки принадлежности. */
    String externalInstrumentId;

    /** Направление закрытой позиции — доменное значение, резолвленное в слое интеграции. */
    Position.Direction direction;

    /** Время создания записи — вторая половина оси адресации. */
    OffsetDateTime externalCreatedAt;

    /** Время обновления записи — операнд порога доказанного покрытия. */
    OffsetDateTime externalModifiedAt;
}
