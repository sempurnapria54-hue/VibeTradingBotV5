package com.example.bff.box;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Журнал приложения — единственный наблюдаемый выход ветвей приёма, у
 * которых провода нет вовсе: пропущенная запись в поток не уходит, и
 * «отказ каждой виден строкой лога» (группа {@code B6} документа кейсов)
 * читается только здесь.
 *
 * <p><b>Почему приёмник, а не чтение файла:</b> файла у прогона нет —
 * вывод идёт в консоль, — и ассерт по нему был бы ассертом о настройке
 * логирования, а не о том, что пишет сервис.
 *
 * <p><b>Журнал общий у ВСЕХ поднятых контекстов прогона, и это несущее
 * для формы ассерта.</b> Слушатели каждого живого контекста читают те же
 * темы своими группами, и одну пропущенную запись в журнал пишет каждый из
 * них. Поэтому клетка утверждает о строке, ОПОЗНАЮЩЕЙ её запись (смещение,
 * класс, которого нет у соседей), а не о счёте строк: счёт мерил бы число
 * живых контекстов.
 *
 * <p><b>Привязка проверяется перед каждым чтением.</b> Подъём контекста
 * Boot переинициализирует систему логирования, снимает с корневого логгера
 * чужих приёмников и останавливает их; приёмник, повешенный однажды,
 * пережил бы только первый контекст, и отказ был бы молчаливым — журнал
 * просто пуст (ловушка TC-045).
 */
final class AppLog {

    private static final String NAME = "box-app-log";

    private static final ListAppender<ILoggingEvent> APPENDER = newAppender();

    private AppLog() {
    }

    /** Отметка текущей длины журнала: с неё читает клетка о своей записи. */
    static Integer mark() {
        attach();
        return APPENDER.list.size();
    }

    /** Всё, что сервис написал в журнал после отметки. */
    static String since(Integer mark) {
        attach();
        List<ILoggingEvent> events;
        synchronized (APPENDER.list) {
            events = List.copyOf(APPENDER.list);
        }
        StringBuilder collected = new StringBuilder();
        for (int index = mark; index < events.size(); index++) {
            ILoggingEvent event = events.get(index);
            collected.append(event.getFormattedMessage()).append('\n');
            if (nonNull(event.getThrowableProxy())) {
                collected.append(event.getThrowableProxy().getClassName()).append(": ")
                        .append(event.getThrowableProxy().getMessage()).append('\n');
            }
        }
        return collected.toString();
    }

    private static ListAppender<ILoggingEvent> newAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setName(NAME);
        appender.start();
        return appender;
    }

    /**
     * Привязывает приёмник к корневому логгеру, подняв его, если он
     * остановлен: сброс системы логирования не только отвязывает, но и
     * останавливает, и привязка без подъёма выглядела бы исполненной.
     */
    private static void attach() {
        if (isFalse(APPENDER.isStarted())) {
            APPENDER.start();
        }
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (isNull(root.getAppender(NAME))) {
            root.addAppender(APPENDER);
        }
    }
}
