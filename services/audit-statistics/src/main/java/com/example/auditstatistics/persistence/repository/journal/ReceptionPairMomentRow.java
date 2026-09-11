package com.example.auditstatistics.persistence.repository.journal;

import java.time.OffsetDateTime;

/**
 * Строка выборки моментов пары: тема и два durable-момента её состояния
 * приёма.
 *
 * <p><b>Проекция, а не сущность:</b> из строки читаются три колонки из
 * восьми, и тянуть её целиком ради них правило выборки запрещает
 * (.claude/rules/codestyle.md §«Выборка данных: не тянем сущность ради
 * одного поля»).
 *
 * <p><b>Выбор между двумя моментами проекция НЕ делает</b> — он живёт
 * предикатом доменной модели
 * ({@link com.example.auditstatistics.domain.model.ReceptionPairMoments}).
 * Сведённый в {@code coalesce} самого запроса, он проверялся бы только
 * живым прогоном базы, а его подмена — «брать один момент вместо двух» —
 * уносит ряд возраста у здоровой пары, которой производитель ещё ничего
 * не присылал.
 */
public interface ReceptionPairMomentRow {

    String getTopic();

    OffsetDateTime getLastAcceptedOccurredAt();

    OffsetDateTime getObservedSince();
}
