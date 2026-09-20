package com.example.auth.box;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Журнал приложения за прогон — четвёртый канал, которым ключи счёта
 * могли бы выйти наружу ({@code B2.11}).
 *
 * <p><b>Почему приёмник, а не чтение файла:</b> файла у прогона нет —
 * вывод идёт в консоль, — и ассерт по нему был бы ассертом о настройке
 * логирования, а не о том, что пишет сервис. Приёмник вешается на
 * корневой логгер один раз на JVM и копит записи ВСЕХ кейсов: отрицание
 * «ключей в журнале нет» шире одного кейса по построению, и это ему не
 * мешает — оно абсолютное по предмету, а не по состоянию субстрата.
 */
final class AppLog {

    private static final ListAppender<ILoggingEvent> APPENDER = attach();

    private AppLog() {
    }

    /** Всё, что сервис написал в журнал за прогон. */
    static String text() {
        List<ILoggingEvent> events = List.copyOf(APPENDER.list);
        StringBuilder collected = new StringBuilder();
        events.forEach(event -> collected.append(event.getFormattedMessage()).append('\n'));
        return collected.toString();
    }

    private static ListAppender<ILoggingEvent> attach() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setName("box-app-log");
        appender.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(appender);
        return appender;
    }
}
