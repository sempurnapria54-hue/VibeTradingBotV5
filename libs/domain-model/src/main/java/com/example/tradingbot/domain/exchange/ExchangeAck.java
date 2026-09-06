package com.example.tradingbot.domain.exchange;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Нормализованный ACK биржи на write-команду (place/cancel/close):
 * принят ли запрос, биржевой id и эхо stable client id, код/сообщение.
 * ACK не runtime-truth (docs/rules/ack-not-runtime-truth.md): «принят»
 * ≠ «исполнен» — статус подтверждает REFRESH_*-контур. Единственное,
 * что выходит за adapter на write-операции.
 *
 * <p><b>Форма — бин с пустым конструктором, а не {@code @Value}, и это
 * не вкусовая правка.</b> Ack пересекает границу между коннектором и
 * ядром JSON'ом (docs/architecture/contracts.md §«Два канала»), то есть у
 * него есть не только писатель, но и ЧИТАТЕЛЬ; неизменяемая форма без
 * пустого конструктора собирается сериализатором только по явной
 * инструкции, а инструкция эта у Jackson 2 и Jackson 3 разная — на
 * проводе живёт Jackson 3, и аннотация от второго была бы молча
 * проигнорирована. Все остальные модели этого провода — обычные бины,
 * ack был единственным исключением и единственным, чей читатель до сих
 * пор не существовал.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeAck {

    /** Запрос принят биржей (sCode успеха). */
    private Boolean success;

    /** Биржевой id созданной/затронутой сущности (ordId/algoId), если вернулся. */
    private String externalId;

    /** Эхо stable client id (clOrdId/algoClOrdId). */
    private String internalId;

    /** Код результата биржи (OKX sCode). */
    private String code;

    /** Сообщение результата биржи (OKX sMsg). */
    private String message;

    /**
     * Биржевое время приёма запроса. Операнд нижней границы окна
     * линковки движений (docs/models/domain/aggregate/Deal.md,
     * billsWindowBegin): оба операнда окна биржевые, локальные часы в
     * сравнение не входят.
     */
    private OffsetDateTime externalCreatedAt;
}
