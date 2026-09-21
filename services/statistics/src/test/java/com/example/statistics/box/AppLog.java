package com.example.statistics.box;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Журнал приложения — единственный наблюдаемый выход тех ветвей, у которых
 * нет ни поверхности, ни следа в базе.
 *
 * <p><b>У ЭТОГО предмета читатель один, и предмет у него — УРОВЕНЬ, а не
 * текст.</b> Событие класса, статистикой не несомого, доезжает и факта не
 * порождает: строк не появляется ни в одной таблице, смещение двигается — то
 * есть исход неотличим от исхода класса, который сервис не узнал. Дом говорит,
 * что различать их незачем, а вот жалобы быть не должно: отбор идёт у
 * потребителя штатно, и запись уровня предупреждения превратила бы штатную
 * ветвь в аномалию для наблюдателя окружения
 * (docs/models/domain/other/StatisticsFact.md §«Признак несомого класса»).
 * Поэтому наружу здесь отдаётся не текст, а перечень записей УРОВНЯ
 * предупреждения и выше.
 *
 * <p><b>Почему приёмник, а не чтение файла:</b> файла у прогона нет — вывод
 * идёт в консоль, — и ассерт по нему был бы ассертом о настройке логирования,
 * а не о том, что пишет сервис.
 *
 * <p><b>Приёмник копит записи ВСЕХ кейсов.</b> Клетка, утверждающая о своей
 * записи либо о её отсутствии, снимает отметку {@link #mark()} до своего хода
 * и читает {@link #alarmsSince}: накопленное соседками иначе давало бы ложное
 * попадание там, где клетка утверждает молчание.
 *
 * <p><b>Привязка проверяется перед каждым чтением, и это не
 * перестраховка.</b> Подъём контекста Boot ПЕРЕИНИЦИАЛИЗИРУЕТ систему
 * логирования и снимает с корневого логгера всех чужих приёмников: у прогона
 * контекстов столько, сколько у ящика положений осей конфигурации, и
 * приёмник, повешенный однажды, пережил бы только первый. Отказ при этом был
 * бы МОЛЧАЛИВЫМ — журнал просто пуст, а клетка о молчании зелена по ложной
 * причине.
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

    /**
     * Записи уровня предупреждения и выше, сделанные после отметки.
     *
     * <p>Текст сохранён вместе с уровнем: сообщение падения обязано называть,
     * ЧТО именно пожаловалось, иначе красная клетка не отличает своей жалобы
     * от чужой.
     *
     * @param mark отметка, снятая до хода клетки
     */
    static List<String> alarmsSince(Integer mark) {
        attach();
        List<ILoggingEvent> events = List.copyOf(APPENDER.list);
        List<String> collected = new ArrayList<>();
        for (int index = mark; index < events.size(); index++) {
            ILoggingEvent event = events.get(index);
            if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
                collected.add(event.getLevel() + " " + event.getFormattedMessage());
            }
        }
        return collected;
    }

    /**
     * Привязывает приёмник к корневому логгеру, подняв его, если он
     * остановлен.
     *
     * <p><b>Половины здесь ДВЕ, и вторая несущая.</b> Сброс системы
     * логирования не только отвязывает чужих приёмников, но и ОСТАНАВЛИВАЕТ
     * их; остановленный приёмник молча не принимает записей, и привязка без
     * подъёма выглядела бы исполненной, а журнал оставался бы пустым.
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
