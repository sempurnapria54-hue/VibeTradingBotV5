package com.example.tradingcore.unit.fsm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * Захват журнального выхода единиц предмета.
 *
 * <p><b>Различение «обработчик ребра не предложил» и «гейт ребро
 * отверг» по самому переходу невозможно</b> — в обоих исходах целевой
 * статус пуст, — и наблюдается оно записью журнала: гейт пишет отказ
 * поимённо, обработчик молчит
 * (`.claude/tests/cases/trading-core-fsm.md` §«Новая ось формы: выход
 * прохода — две половины, и гейт снимает одну»).
 *
 * <p><b>Приёмник вешается на логгеры самих классов</b>, подменять при
 * этом нечего: логгер у каждого свой, и запись производит он сам.
 * Классов бывает несколько — отказ ребра переоткрытия различается тем,
 * КТО промолчал, и одного логгера для такого ожидания мало.
 */
final class FsmLogCapture implements AutoCloseable {

    private final List<Logger> loggers = new ArrayList<>();

    private final List<Level> restoredLevels = new ArrayList<>();

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    /**
     * Уровень отладки ставится приёмником, а не конфигурацией прогона:
     * часть наблюдаемых записей — отладочные (обработчика по статусу нет,
     * закрытие ещё не разрешено), и при умолчании они до приёмника не
     * доходят вовсе.
     */
    private FsmLogCapture(Class<?>... subjects) {
        appender.start();
        for (Class<?> subject : subjects) {
            Logger logger = (Logger) LoggerFactory.getLogger(subject);
            restoredLevels.add(logger.getLevel());
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
            loggers.add(logger);
        }
    }

    /** Приёмник, снимающий записи названных классов до закрытия. */
    static FsmLogCapture attach(Class<?>... subjects) {
        return new FsmLogCapture(subjects);
    }

    /** Форматированные сообщения, снятые с момента подключения. */
    List<String> messages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    /** Уровни снятых записей в порядке их появления. */
    List<String> levels() {
        return appender.list.stream()
                .map(event -> event.getLevel().toString())
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        for (int index = 0; index < loggers.size(); index++) {
            Logger logger = loggers.get(index);
            logger.detachAppender(appender);
            logger.setLevel(restoredLevels.get(index));
        }
        appender.stop();
    }
}
