package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Торговое состояние биржевого счёта: его ступень плюс ступени пар
 * «счёт, инструмент», которые стоят.
 *
 * <p><b>Читатель этой поверхности — тот же держатель, что вызывает
 * ручную остановку.</b> Без неё операция остановки ненаблюдаема: статус
 * объекта и есть её durable-след, а строку журнала читают уже разбором,
 * а не в момент вызова.
 */
@Getter
@Setter
public class SafetyStateApiResponse {

    @Schema(description = "Идентичность биржевого счёта")
    private String exchangeAccountInternalId;

    @Schema(description = "Ступень счёта: ACTIVE — рабочее состояние, HOLD — мягкая,"
            + " TRADE_BLOCKED — сворачивание")
    private String accountSafetyRung;

    @Schema(description = "Реестровый статус счёта у владельца реестра")
    private String accountStatus;

    @Schema(description = "Серия подряд убыточных сделок счёта")
    private Integer consecutiveLossCount;

    @Schema(description = "Подряд идущие НЕнаблюдённые проходы проактивной детекции")
    private Integer blindPassCount;

    @Schema(description = "Ступени пар «счёт, инструмент», которые стоят: ключ — идентичность инструмента,"
            + " значение — ступень его лестницы (ENTRY_BLOCKED — мягкий запрет входов, TRADE_BLOCKED —"
            + " сворачивание). Инструмента нет в перечне — его пара в рабочем состоянии; пусто — стоящих нет")
    private Map<String, String> standingInstrumentRungs;
}
