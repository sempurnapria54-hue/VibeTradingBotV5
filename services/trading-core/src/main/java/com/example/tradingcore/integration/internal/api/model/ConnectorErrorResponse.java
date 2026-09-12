package com.example.tradingcore.integration.internal.api.model;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Тело отказа в ответе коннектора — сырая форма соседа.
 *
 * <p><b>Своя, а не импортированная из соседа:</b> api-модель принадлежит
 * тому, кто её отдаёт, и зависимость на неё сделала бы выкатку коннектора
 * пересборкой ядра. Через границу едет форма, а не класс.
 *
 * <p><b>Значения при этом общие.</b> Форма у каждой стороны своя, а
 * перечень классов —
 * {@code com.example.tradingbot.domain.exchange.ExchangeFailureClass}: его
 * производит коннектор, потребляет ядро, и разойдись значения — ядро молча
 * выбрало бы реакцию по умолчанию.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConnectorErrorResponse {

    /** Класс отказа: значение {@code ExchangeFailureClass} либо чужое. */
    private String code;

    /** Причина внутри класса; у {@code EXTERNAL_STATUS} — код проблемного статуса. */
    private String reason;

    /** Пояснение для человека; секретов не несёт. */
    private String message;

    /** Момент отказа на стороне коннектора, UTC. */
    private OffsetDateTime occurredAt;
}
