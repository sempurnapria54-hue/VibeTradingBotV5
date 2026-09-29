package com.example.marketdata.exception;

/**
 * Отказ чтения площадки через коннектор: транспорт, отказ доступа либо
 * ответ, который не разобрался. Незаданный адрес коннектора — тот же класс:
 * соседа не достать ни по какому адресу (docs/architecture/services/market-data.md
 * §«Какие вызовы делает и какие принимает»).
 *
 * <p>Отдельный тип, а не {@code RuntimeException}: на проходе сбора
 * отказ по одному инструменту не роняет проход, а отказ доступа или
 * лимита проход прекращает (docs/processes/snapshot-collection.md
 * §«Отказ на проходе») — различать их вызывающему нужно по типу, а не по
 * тексту сообщения.
 */
public class ExchangeReadException extends RuntimeException {

    public ExchangeReadException(String message) {
        super(message);
    }

    public ExchangeReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
