package com.example.connector.okx.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот позиционного тира внутри
 * {@link InstrumentExternalRulesExternalSnapshot}: сырые строки площадки,
 * уже провалидированные как непустые (разбор в число — при материализации
 * правил). Сырой OKX DTO за adapter не выходит. См.
 * docs/models/mapping/InstrumentExternalRules.md.
 */
@Value
@Builder
public class PositionTierExternalSnapshot {

    /** Нижняя граница размера позиции тира в контрактах (OKX minSz). */
    String externalMinSize;

    /** Верхняя граница размера позиции тира в контрактах (OKX maxSz). */
    String externalMaxSize;

    /** Ставка поддерживающей маржи тира (OKX mmr). */
    String externalMaintenanceMarginRate;
}
