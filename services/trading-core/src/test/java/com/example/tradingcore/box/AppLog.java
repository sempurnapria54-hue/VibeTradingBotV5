package com.example.tradingcore.box;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Журнал приложения — единственный наблюдаемый выход тех ветвей, у
 * которых поверхности нет вовсе: перехват отказа одной сделки
 * проходом, пропуск сообщения без конверта, отказ журнального носителя,
 * пропуск перекрывающего запуска, пропуск счёта под мягкой ступенью.
 *
 * <p><b>Почему приёмник, а не чтение файла:</b> файла у прогона нет —
 * вывод идёт в консоль, — и ассерт по нему был бы ассертом о настройке
 * логирования, а не о том, что пишет сервис.
 *
 * <p><b>Приёмник копит записи ВСЕХ кейсов.</b> Отрицанию {@code B12.8}
 * («исходящий токен наружу не выходит») это не мешает — оно абсолютное по
 * предмету.
 * Клетке же, утверждающей о СВОЕЙ записи, накопленное мешало бы, поэтому
 * такая клетка снимает отметку {@link #mark()} до тика и читает
 * {@link #since}.
 *
 * <p><b>Привязка проверяется перед каждым чтением, и это не
 * перестраховка.</b> Подъём контекста Boot ПЕРЕИНИЦИАЛИЗИРУЕТ систему
 * логирования и снимает с корневого логгера всех чужих приёмников: у
 * прогона контекстов столько, сколько у ящика положений осей
 * конфигурации, и приёмник, повешенный однажды, пережил бы только первое.
 * Отказ при этом был бы МОЛЧАЛИВЫМ — журнал просто пуст, а ожидание следа
 * тика истекает по таймауту.
 */
final class AppLog {

    private static final String NAME = "box-app-log";

    private static final ListAppender<ILoggingEvent> APPENDER = newAppender();

    private AppLog() {
    }

    /** Всё, что сервис написал в журнал за прогон. */
    static String text() {
        return textFrom(0);
    }

    /** Отметка текущей длины журнала: с неё читает клетка о своей записи. */
    static Integer mark() {
        attach();
        return APPENDER.list.size();
    }

    /** Всё, что сервис написал в журнал после отметки. */
    static String since(Integer mark) {
        attach();
        return textFrom(mark);
    }

    private static String textFrom(Integer from) {
        List<ILoggingEvent> events = List.copyOf(APPENDER.list);
        StringBuilder collected = new StringBuilder();
        for (int index = from; index < events.size(); index++) {
            ILoggingEvent event = events.get(index);
            collected.append(event.getFormattedMessage()).append('\n');
            appendCauses(collected, event.getThrowableProxy());
        }
        return collected.toString();
    }

    /**
     * Дописывает цепочку причин отказа целиком.
     *
     * <p><b>Первопричина несущая, и одной верхней записи мало.</b> Отказ,
     * поднятый внутри слушателя брокера либо обработчика поверхности,
     * приходит в журнал ОБЁРНУТЫМ — своё сообщение обёртки говорит лишь о
     * том, что метод бросил; ассерт по нему не отличил бы «содержимое не
     * разобралось» от любого другого отказа того же слушателя.
     */
    private static void appendCauses(StringBuilder collected, IThrowableProxy failure) {
        IThrowableProxy current = failure;
        while (current != null) {
            collected.append(current.getClassName()).append(": ")
                    .append(current.getMessage()).append('\n');
            current = current.getCause();
        }
    }

    private static ListAppender<ILoggingEvent> newAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setName(NAME);
        appender.start();
        return appender;
    }

    /**
     * Привязывает приёмник к корневому логгеру, подняв его, если он
     * остановлен.
     *
     * <p><b>Половины здесь ДВЕ, и вторая несущая.</b> Сброс системы
     * логирования не только отвязывает чужие приёмники, но и
     * ОСТАНАВЛИВАЕТ их; остановленный приёмник молча не принимает
     * записей, и привязка без подъёма выглядела бы исполненной, а журнал
     * оставался бы пустым.
     */
    private static void attach() {
        if (!APPENDER.isStarted()) {
            APPENDER.start();
        }
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(NAME) == null) {
            root.addAppender(APPENDER);
        }
    }
}
