package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Runtime-проверка свежести рыночных данных. Состояния в БД не хранит:
 * свежесть вычисляется на чтение
 * {@code expiredAt = referencePoint + tolerance}, свежо ⟺
 * {@code now < expiredAt}. referencePoint — windowEndAt (структура) /
 * candleTimestamp (индикатор); confirmedAt — гейт без look-ahead, не
 * точка отсчёта. См. docs/components/MarketDataExpirationChecker.md,
 * docs/rules/market-data-freshness.md.
 *
 * <p><b>Толерантность приносит ЧИТАТЕЛЬ, а не строка результата.</b>
 * Строка ряда о заказчике ничего не знает и срока не несёт: одно и то же
 * значение для одной настройки свежее, для другой — уже нет
 * (docs/models/domain/other/IndicatorValue.md §«Ключевание —
 * идентичностью вычисления»). Отсюда сроком здесь является операнд
 * вызова, а не поле хранимого объекта.
 */
@Service
public class MarketDataExpirationChecker {

    /** Свежо ли значение с точкой отсчёта referencePoint под срок tolerance (на момент now UTC). */
    public Boolean isFresh(OffsetDateTime referencePoint, Duration tolerance) {
        if (isNull(referencePoint) || isNull(tolerance)) {
            return false;
        }
        OffsetDateTime expiredAt = referencePoint.plus(tolerance);
        return OffsetDateTime.now(ZoneOffset.UTC).isBefore(expiredAt);
    }
}
