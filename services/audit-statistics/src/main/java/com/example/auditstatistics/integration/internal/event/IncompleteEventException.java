package com.example.auditstatistics.integration.internal.event;

/**
 * Вход неполон: обязательное значение не предъявлено сообщением
 * (docs/models/domain/other/AuditRecord.md §«Единственная ветвь, на которой
 * строки не будет, — неполный вход»).
 *
 * <p><b>Класс отказа один, потому что ветвь одна.</b> Сюда же попадает
 * значение, предъявленное в форме, которую колонка не несёт: момент не
 * разбирается как момент, версия — как число, содержимое — как документ.
 * Такое значение <b>не</b> подменяется пустотой: пустота у этих колонок
 * объявлена значением («значения не было»), и подмена записала бы в журнал
 * ложный факт (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Отказ обязан быть брошен, а не залогирован.</b> На нём держится
 * остановка приёма: смещение не фиксируется, группа остаётся на
 * отравленном сообщении, и дыра наблюдаема. Поглощённое исключение
 * продвинуло бы группу и потеряло бы строку молча.
 */
public class IncompleteEventException extends RuntimeException {

    public IncompleteEventException(String message) {
        super(message);
    }

    public IncompleteEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
