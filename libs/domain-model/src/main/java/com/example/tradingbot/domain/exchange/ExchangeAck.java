package com.example.tradingbot.domain.exchange;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный ACK биржи на write-команду (place/cancel/close):
 * принят ли запрос, биржевой id и эхо stable client id, код/сообщение.
 * ACK не runtime-truth (docs/rules/ack-not-runtime-truth.md): «принят»
 * ≠ «исполнен» — статус подтверждает REFRESH_*-контур. Единственное,
 * что выходит за adapter на write-операции.
 */
@Value
@Builder
public class ExchangeAck {

    /** Запрос принят биржей (sCode успеха). */
    Boolean success;

    /** Биржевой id созданной/затронутой сущности (ordId/algoId), если вернулся. */
    String externalId;

    /** Эхо stable client id (clOrdId/algoClOrdId). */
    String internalId;

    /** Код результата биржи (OKX sCode). */
    String code;

    /** Сообщение результата биржи (OKX sMsg). */
    String message;

    /**
     * Биржевое время приёма запроса. Операнд нижней границы окна
     * линковки движений (docs/models/domain/aggregate/Deal.md,
     * billsWindowBegin): оба операнда окна биржевые, локальные часы в
     * сравнение не входят.
     */
    OffsetDateTime externalCreatedAt;
}
