package com.example.tradingcore.unit.safety;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * Захват журнального выхода единицы предмета.
 *
 * <p><b>У части кейсов запись журнала и есть весь наблюдаемый эффект.</b>
 * Подавленный отказ журнала, пропущенное доведение при занятом ключе
 * объекта и незакрытый отчёт счётного радиуса от неслучившегося вызова
 * значением не отличаются ничем — отличает их только собственная запись
 * (`.claude/tests/cases/trading-core-safety.md` §«Чем достаются выходы»).
 *
 * <p><b>Приёмник вешается на логгер самого предмета</b>, подменять при
 * этом нечего: логгер у класса свой, и запись производит он сам.
 */
final class SafetyLogCapture implements AutoCloseable {

    private final Logger logger;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private SafetyLogCapture(Class<?> subject) {
        logger = (Logger) LoggerFactory.getLogger(subject);
        appender.start();
        logger.addAppender(appender);
    }

    /** Приёмник, снимающий записи названного класса до закрытия. */
    static SafetyLogCapture attach(Class<?> subject) {
        return new SafetyLogCapture(subject);
    }

    /** Форматированные сообщения, снятые с момента подключения. */
    List<String> messages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
