package com.example.strategies.integration.internal.api.model;

/**
 * Ответ ядра о разрешимости ссылок определения — форма ИСТОЧНИКА.
 *
 * <p>Три признака, а не один: отказ адресует тот конъюнкт, который ложен
 * (docs/concept.md П3).
 *
 * @param accountFound           счёт есть в проекции реестра
 * @param accountBelongsToTenant счёт принадлежит названному тенанту
 * @param instrumentFound        инструмент есть в проекции каталога
 */
public record PairCheckCoreResponse(Boolean accountFound, Boolean accountBelongsToTenant,
                                    Boolean instrumentFound) {
}
