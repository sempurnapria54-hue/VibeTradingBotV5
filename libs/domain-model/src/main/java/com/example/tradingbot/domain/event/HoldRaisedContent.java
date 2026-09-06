package com.example.tradingbot.domain.event;

/**
 * Содержимое события «ступень поднята»: радиус, ступень и машинный код
 * причины — ровно то, чем в данных различаются основания одной и той же
 * ступени.
 */
public record HoldRaisedContent(String exchangeAccountInternalId,
                                String instrumentInternalId,
                                String scope,
                                String rung,
                                String code) {
}
