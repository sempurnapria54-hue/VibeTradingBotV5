package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * Привязка авторского имени операнда к идентичности вычисления и к сроку
 * свежести читателя.
 *
 * <p>Обе величины принадлежат читателю: имя — из его клауз, срок — его
 * собственная толерантность (docs/rules/market-data-freshness.md).
 */
@Getter
@Setter
public class FeatureBindingApiRequest {

    @NotBlank
    @Schema(description = "Авторское имя операнда, которым его называет клауза")
    private String key;

    @NotBlank
    @Schema(description = "Идентичность вычисления, из которой берётся значение")
    private String configInternalId;

    @NotNull
    @Schema(description = "Срок свежести читателя (ISO-8601): значение старше в контекст не попадает")
    private Duration tolerance;
}
