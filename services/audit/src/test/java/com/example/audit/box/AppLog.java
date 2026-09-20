package com.example.audit.box;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Журнал приложения — единственный наблюдаемый выход тех ветвей, у
 * которых нет ни поверхности, ни следа в базе.
 *
 * <p><b>У ЭТОГО предмета таких ветвей две, и обе о чистке.</b>
 * Недоехавшая ось окружения и назначенный неограниченный профиль дают
 * ОДИН исход — прохода не будет, — и дом прямо говорит, что различает их
 * не исход, а след: пустая ось поднимает предупреждение, а {@code
 * UNBOUNDED} проходит молча
 * (docs/components/JournalCleanupJob.md §«Ось не доехала — проход не
 * идёт, и это не то же самое, что `UNBOUNDED`»). Ни журнал, ни строки
 * состояния в обоих состояниях не меняются ничем, и без записи журнала
 * дефект развёртывания был бы неотличим от назначенного значения — то
 * есть ровно тем, против чего разведение и заведено.
 *
 * <p><b>Второй читатель — пропуск перекрывающего такта.</b> Удаление
 * идемпотентно по исходу, поэтому «второй проход пропущен» от «второй
 * проход прошёл следом» база не отличает ни одной колонкой; отличает их
 * запись охраны.
 *
 * <p><b>Почему приёмник, а не чтение файла:</b> файла у прогона нет —
 * вывод идёт в консоль, — и ассерт по нему был бы ассертом о настройке
 * логирования, а не о том, что пишет сервис.
 *
 * <p><b>Приёмник копит записи ВСЕХ кейсов.</b> Клетка, утверждающая о
 * СВОЕЙ записи либо о её отсутствии, снимает отметку {@link #mark()} до
 * своего хода и читает {@link #since}: накопленное соседками иначе
 * давало бы ложное попадание там, где клетка утверждает молчание.
 *
 * <p><b>Привязка проверяется перед каждым чтением, и это не
 * перестраховка.</b> Подъём контекста Boot ПЕРЕИНИЦИАЛИЗИРУЕТ систему
 * логирования и снимает с корневого логгера всех чужих приёмников: у
 * прогона контекстов столько, сколько у ящика положений осей
 * конфигурации, и приёмник, повешенный однажды, пережил бы только
 * первый. Отказ при этом был бы МОЛЧАЛИВЫМ — журнал просто пуст, а
 * клетка о молчании зелена по ложной причине.
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
        List<ILoggingEvent> events = List.copyOf(APPENDER.list);
        StringBuilder collected = new StringBuilder();
        for (int index = mark; index < events.size(); index++) {
            collected.append(events.get(index).getFormattedMessage()).append('\n');
        }
        return collected.toString();
    }

    /**
     * Привязывает приёмник к корневому логгеру, подняв его, если он
     * остановлен.
     *
     * <p><b>Половины здесь ДВЕ, и вторая несущая.</b> Сброс системы
     * логирования не только отвязывает чужих приёмников, но и
     * ОСТАНАВЛИВАЕТ их; остановленный приёмник молча не принимает
     * записей, и привязка без подъёма выглядела бы исполненной, а журнал
     * оставался бы пустым.
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

    private static ListAppender<ILoggingEvent> newAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setName(NAME);
        appender.start();
        return appender;
    }
}
