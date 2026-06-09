package com.example.tradingbot.domain.service.market;

import static java.util.Objects.isNull;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Runtime-проверка свежести рыночных данных. Состояние в БД не хранит и
 * Strategy.Status не меняет: свежесть вычисляется на чтение
 * {@code expiredAt = referencePoint + askingSetting.expirationDuration},
 * свежо ⟺ {@code now < expiredAt}. referencePoint — windowEndAt
 * (структура) / candleTimestamp (фаза, индикатор); confirmedAt — гейт без
 * look-ahead, не точка отсчёта. На общей строке шаримого результата
 * единого expiredAt нет — свежесть оценивается под каждую запрашивающую
 * настройку. Агрегирующие checkForEntry/checkForStep (по Strategy/
 * DealContext) приходят с кластером сделки. См.
 * docs/components/MarketDataExpirationChecker.md,
 * docs/rules/market-data-freshness.md.
 */
@Service
public class MarketDataExpirationChecker {

    /** Свежо ли значение с точкой отсчёта referencePoint под срок expirationDuration (на момент now UTC). */
    public Boolean isFresh(OffsetDateTime referencePoint, Duration expirationDuration) {
        if (isNull(referencePoint) || isNull(expirationDuration)) {
            return false;
        }
        OffsetDateTime expiredAt = referencePoint.plus(expirationDuration);
        return OffsetDateTime.now(ZoneOffset.UTC).isBefore(expiredAt);
    }
}
