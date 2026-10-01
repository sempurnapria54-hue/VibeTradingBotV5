package com.example.connector.okx.integration.external.api.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Нативный DTO OKX позиционного тира (GET /api/v5/public/position-tiers,
 * элемент data[]). Несёт только подмножество, которое читает оценка
 * ликвидации до входа, плюс семью инструмента — ей проверяется
 * принадлежность записи запросу; все значения приходят строками. Не выходит
 * за IntegrationService/adapter
 * (docs/models/integrations/okx/PositionTierOkxResponse.md).
 */
@Getter
@Setter
public class PositionTierOkxResponse {

    /** Семья инструмента тира (instFamily) — эхо запроса. */
    private String instFamily;

    /** Номер тира (tier). */
    private String tier;

    /** Нижняя граница размера позиции тира в контрактах (minSz, decimal-строка). */
    private String minSz;

    /** Верхняя граница размера позиции тира в контрактах (maxSz, decimal-строка). */
    private String maxSz;

    /** Ставка поддерживающей маржи тира (mmr, decimal-строка). */
    private String mmr;
}
