package com.example.bff.domain;

/**
 * Билет подписки не предъявлен, испорчен либо просрочен.
 *
 * <p><b>Класс отдельный, а ответ — тот же.</b> Отказ на тропе потока
 * отвечает тем же error-DTO и тем же кодом, что отказ фильтр-цепочки:
 * форм предъявления две, а формат отказа один
 * (docs/rules/error-handling-policy.md §«Отказ доступа — тот же
 * контракт, что и прочие ошибки»).
 *
 * <p><b>Наружу не уходит, чем именно билет не годен</b> — ни срока, ни
 * подписи: вызывающий, не предъявивший годного билета, о контуре не
 * узнаёт ничего.
 */
public class TicketRejectedException extends RuntimeException {

    public TicketRejectedException(String message) {
        super(message);
    }
}
