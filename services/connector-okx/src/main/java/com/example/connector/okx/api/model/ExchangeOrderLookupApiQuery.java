package com.example.connector.okx.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Адресация одной заявки при точечном чтении.
 *
 * <p><b>Одна форма служит и обычной заявке, и условной</b>: у площадки
 * обе адресуются одинаково — инструментом и одним из двух
 * идентификаторов. Заводить две одинаковые формы значило бы держать два
 * носителя одной истины ({@code .claude/rules/design-simplicity.md}).
 *
 * <p><b>Идентификаторов два, и обязателен ровно один.</b> Наш
 * {@code internalId} известен сразу после решения, биржевой
 * {@code externalId} — только после приёма; читатель предъявляет тот,
 * который у него есть.
 */
@Getter
@Setter
public class ExchangeOrderLookupApiQuery {

    @Schema(description = "Идентификатор инструмента на площадке: без него площадка заявку не адресует")
    private String externalInstrumentId;

    @Schema(description = "Идентификатор заявки на площадке; пусто — если известен только наш")
    private String externalId;

    @Schema(description = "Наш идентификатор заявки, уехавший на площадку клиентским; пусто — если известен биржевой")
    private String internalId;
}
