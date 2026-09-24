package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Ступень и торговые настройки счёта на инструменте, какими их держит ядро
 * (docs/models/domain/core/Instrument.md §«Ступень и настройки счёта на
 * инструменте — своя таблица ядра»).
 *
 * <p>Идентичности счёта и инструмента резолвит вызывающий, а не маппер:
 * числовой ключ границу сервиса не пересекает
 * (.claude/rules/codestyle.md §«Идентичность наружу»).
 */
@Getter
@Setter
public class AccountInstrumentStateApiResponse {

    @Schema(description = "Идентичность биржевого счёта")
    private String exchangeAccountInternalId;

    @Schema(description = "Идентичность инструмента")
    private String instrumentInternalId;

    @Schema(description = "Рабочее плечо счёта на инструменте; пусто — не назначено")
    private Integer leverage;

    @Schema(description = "Режим маржи счёта на инструменте")
    private String marginMode;

    @Schema(description = "Ступень лестницы инструмента, стоящая на этом счёте")
    private String safetyRung;
}
