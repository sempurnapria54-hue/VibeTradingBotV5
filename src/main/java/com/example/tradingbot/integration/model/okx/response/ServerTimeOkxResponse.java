package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ответ OKX по серверному времени (GET /public/time). Якорь
 * биржевого временного домена там, где у события нет собственной метки
 * источника: системные часы хоста с биржевым доменом не сравнимы
 * (docs/integrations/okx/contracts/server-time.md, docs/rules/time-utc.md).
 */
@Getter
@Setter
public class ServerTimeOkxResponse {

    /** Серверное время источника, epoch ms. */
    private String ts;
}
