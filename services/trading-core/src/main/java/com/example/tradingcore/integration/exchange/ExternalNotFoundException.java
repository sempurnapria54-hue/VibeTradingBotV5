package com.example.tradingcore.integration.exchange;

/**
 * После ПОЛНОГО цикла добычи сущность у источника не найдена, и объяснить
 * её финал безопасно нечем.
 *
 * <p><b>Бросок ядра, а не границы, и это несущее свойство.</b> Пустой
 * ответ одного эндпоинта основанием не является
 * ({@code docs/rules/controlled-exchange-exceptions.md} §Правило); знает
 * об исчерпании цикла только тот, кто цикл и ведёт, — исполнитель добычи.
 * Класса с таким именем на границе коннектора поэтому нет вовсе
 * ({@code docs/components/IntegrationService.md} §«Классы отказа на
 * границе — дом здесь»).
 *
 * <p>Причина закрытия сущности у этой категории одна —
 * {@code MISSING_AFTER_REFRESH}; отдельным полем она не едет, потому что
 * выбора здесь нет.
 */
public class ExternalNotFoundException extends ControlledExchangeException {

    public ExternalNotFoundException(String message) {
        super(message);
    }
}
