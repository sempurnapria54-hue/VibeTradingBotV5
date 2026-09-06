package com.example.tradingcore.domain.model;

/**
 * Исход проверки ссылок определения стратегии по проекциям ядра.
 *
 * <p><b>Три признака, а не один:</b> отказ адресует тот конъюнкт, который
 * ложен (docs/concept.md П3) — иначе автор получает «не годится» без
 * указания, что именно не разрешилось.
 *
 * <p>Запись живёт в памяти прохода и провода не пересекает: наружу её
 * переносит api-модель ответа
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * @param accountFound           счёт с такой идентичностью есть в проекции реестра
 * @param accountBelongsToTenant счёт принадлежит названному тенанту
 * @param instrumentFound        инструмент с такой идентичностью есть в проекции каталога
 */
public record PairCheck(Boolean accountFound, Boolean accountBelongsToTenant, Boolean instrumentFound) {
}
