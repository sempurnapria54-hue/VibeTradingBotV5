package com.example.marketdata.domain.model;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Привязка авторского имени операнда к идентичности вычисления и к сроку
 * свежести, под который значение годно ЭТОМУ читателю.
 *
 * <p><b>Обе половины принадлежат читателю, а не хранилищу.</b> Имя
 * операнда — из клауз потребителя; толерантность — его собственная
 * (docs/rules/market-data-freshness.md). market-data резолвит по
 * идентичности и гейтит по сроку, но ни того, ни другого не выдумывает.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeatureBinding {

    /** Авторское имя операнда, которым его называет клауза потребителя. */
    private String key;

    /** Идентичность вычисления, из которой берётся значение. */
    private Long configId;

    /** Срок свежести читателя: значение старше него в контекст не попадает. */
    private Duration tolerance;
}
