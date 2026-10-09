package com.example.connector.okx.api.model;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Адресация закрываемой позиции: инструмент, валюта расчёта и режим маржи
 * записи.
 *
 * <p><b>Два операнда из трёх необязательны, и пустота у них значит
 * разное.</b> Пустая валюта расчёта в запрос площадке не уходит: её не
 * знает снятие риска по позиции на инструменте вне контура ядра. Пустой
 * режим маржи закрывает запись режима контура — тропа всякой нашей
 * позиции; непустой приносит только снятие риска вне графа сделок,
 * закрывая запись иного режима её собственным режимом
 * ({@code docs/components/IntegrationService.md},
 * {@code docs/models/mapping/Position.md} §«Close-position request»).
 */
@Getter
@Setter
public class PositionClosureApiQuery {

    @NotBlank
    @Schema(description = "Идентификатор инструмента на площадке: без него площадка позицию не адресует")
    private String externalInstrumentId;

    @Schema(description = "Валюта расчёта инструмента; пусто — поле в запрос площадке не уходит")
    private String settleCurrency;

    @Schema(description = "Режим маржи закрываемой записи; пусто — режим контура (изолированная маржа)")
    private Instrument.MarginMode marginMode;
}
