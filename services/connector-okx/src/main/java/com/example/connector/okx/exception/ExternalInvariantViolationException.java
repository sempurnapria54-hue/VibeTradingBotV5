package com.example.connector.okx.exception;

/**
 * Бросает граница коннектора, если ответ площадки получен, но нарушает
 * инвариант контракта. Поводы по факту кода:
 *
 * <ul>
 *   <li><b>недостача обязательного поля</b> — записи закрытия позиции,
 *       тира позиции, группы комиссий, снимка средств (включая строку
 *       расчётной валюты), серверного времени;</li>
 *   <li><b>запись чужого предмета</b> — чужого инструмента в истории
 *       позиций, чужой семьи в тирах;</li>
 *   <li><b>значение вне формы контракта</b> — число, время, перечень, длина
 *       позиционной строки (сеть разбора {@code OkxExchangeGateway}),
 *       неразбираемое либо отрицательное поле снимка средств, направление
 *       закрытой позиции, сторона и признак «только уменьшать» условной
 *       заявки.</li>
 * </ul>
 *
 * <p><b>Расхождения эха заявки с нашей строкой здесь нет:</b> ожидаемое —
 * наша строка, которой коннектор при чтении не видит, поэтому эхо он только
 * переводит в словарь домена, а сверяет его и бросает отказ того же класса
 * добыча в ядре (docs/models/mapping/AlgoOrder.md §«Сверка эха»).
 *
 * <p><b>Это факт о ЧТЕНИИ, а не о сущности:</b> судьба сущности таким
 * ответом не наблюдена, и её статус остаётся последним применённым фактом —
 * причины закрытия у класса нет. Реакция — за классом отказа: Deal → ERROR,
 * биржевая ступень 2 ({@code ExchangeAccount.safetyRung = TRADE_BLOCKED},
 * код причины {@code EXCHANGE_CONTROLLED_FAILURE}). См.
 * docs/rules/controlled-exchange-exceptions.md.
 */
public class ExternalInvariantViolationException extends ControlledExchangeException {

    public ExternalInvariantViolationException(String message) {
        super(message);
    }

    public ExternalInvariantViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
