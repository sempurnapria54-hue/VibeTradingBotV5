package com.example.statistics.domain.model;

import java.time.OffsetDateTime;
import lombok.Value;

/**
 * Начала рядов фактов обоих зёрен, прочитанные один раз на проход пересчёта
 * (docs/spec/statistics-aggregates.json, {@code grainMinFactMoment};
 * docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Читается проходом, а не порцией:</b> охрана отбора, прочитанная
 * заново на каждых сутках, мерила бы проход разной границей — приём,
 * положивший факт посреди прохода, сдвинул бы её между порциями, — и
 * стоила бы двух запросов на каждые сутки окна при величине, одной на
 * весь проход.
 */
@Value
public class FactSeriesStarts {

    /** Самый ранний момент терминала среди сделочных фактов; пусто — ряд пуст. */
    OffsetDateTime dealSeriesStart;

    /** Самый ранний момент происшествия среди фактов происшествий; пусто — ряд пуст. */
    OffsetDateTime incidentSeriesStart;
}
