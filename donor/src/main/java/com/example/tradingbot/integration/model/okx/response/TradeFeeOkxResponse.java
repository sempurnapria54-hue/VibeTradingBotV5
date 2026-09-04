package com.example.tradingbot.integration.model.okx.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Нативный DTO OKX ставок комиссий счёта (GET /api/v5/account/trade-fee).
 * Несёт только snapshot-релевантное подмножество; все значения приходят
 * строками. Не выходит за IntegrationService/adapter
 * (docs/models/integrations/okx/TradeFeeOkxResponse.md).
 *
 * <p><b>Ставка берётся только из {@code feeGroup[]}.</b> Плоские ставки
 * верхнего уровня офдок для SWAP/FUTURES помечает deprecated, и прогон
 * контура это подтвердил: у SWAP они приходят **пустыми строками**, а
 * значения лежат в группе (наблюдение `AG12.1`,
 * `.claude/tests/source-api/okx/observations/AG12_1.md`). Поэтому плоской
 * шестёрки в DTO нет вовсе.
 */
@Getter
@Setter
public class TradeFeeOkxResponse {

    /** Тип инструмента эха запроса (instType) — половина ключа группы. */
    private String instType;

    /** Комиссионный уровень счёта (level), например Lv1 — датчик оси тира. */
    private String level;

    /** Время данных источника (ts, UTC мс). */
    private String ts;

    /** Группы ставок — канонический источник ставки. */
    private List<FeeGroupOkxResponse> feeGroup;

    /**
     * Группа ставок счёта: вторая половина ключа резолва и сами ставки.
     * Знак источника — минус означает комиссию, плюс ребейт; снимается он
     * при маппинге, а не здесь (docs/models/mapping/TradeFeeRate.md).
     */
    @Getter
    @Setter
    public static class FeeGroupOkxResponse {

        /** Идентификатор комиссионной группы (groupId) — ось группы. */
        private String groupId;

        /** Ставка taker-комиссии группы (taker, знак источника). */
        private String taker;

        /** Ставка maker-комиссии группы (maker, знак источника). */
        private String maker;
    }
}
