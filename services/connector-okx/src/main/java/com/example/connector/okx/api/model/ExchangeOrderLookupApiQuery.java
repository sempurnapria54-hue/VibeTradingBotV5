package com.example.connector.okx.api.model;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
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
 * <p><b>Идентификаторов два, и обязателен хотя бы один.</b> Наш
 * {@code internalId} известен сразу после решения, биржевой
 * {@code externalId} — только после приёма; читатель предъявляет тот,
 * который у него есть, а оба — законны: ядро на сверке заявки предъявляет
 * оба, и площадка тогда адресует биржевым
 * ({@code docs/integrations/okx/contracts/order.md}). Вызов без обоих
 * отвергает контейнер — ограничением {@link #getAddressed()} под
 * {@code @Valid} точки ({@code docs/components/IntegrationService.md}).
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

    /**
     * Адресована ли заявка: предъявлен хотя бы один из двух идентификаторов.
     * Пустое значение читается как отсутствие — пустым его и присылает
     * вызывающий, у которого идентификатора нет.
     *
     * <p><b>Префикс {@code get}, а не {@code is}, вынужденный:</b> Bean
     * Validation признаёт {@code is}-метод свойством только при примитивном
     * {@code boolean}, а контрактная поверхность несёт обёртку
     * ({@code .claude/rules/codestyle.md} §«Примитивы и обёртки»). Полем
     * формы метод не становится: записать его биндингу нечем, а описание
     * поверхности собирается по полям.
     */
    @AssertTrue(message = "Нужен хотя бы один идентификатор заявки: externalId либо internalId")
    public Boolean getAddressed() {
        return isNotBlank(externalId) || isNotBlank(internalId);
    }
}
