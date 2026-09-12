package com.example.tradingcore.integration.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ответ владельца рыночных данных на требование вычисления: идентичность,
 * которой это вычисление называют в чтениях.
 *
 * <p><b>Форма — бин, а не запись:</b> значение приезжает по проводу и
 * разбирается сериализатором, поэтому собираться оно обязано без скрытых
 * механизмов (.claude/rules/codestyle.md §«Неизменяемое значение,
 * пересекающее сериализацию»).
 *
 * <p>Один тип на оба вида требований — индикатор и структуру: читателю
 * нужна ровно идентичность, а прочие поля ответа описывают то, что он и
 * так послал.
 */
@Getter
@Setter
@NoArgsConstructor
public class ComputationConfigResponse {

    /** Межсервисный идентификатор идентичности вычисления. */
    private String internalId;
}
