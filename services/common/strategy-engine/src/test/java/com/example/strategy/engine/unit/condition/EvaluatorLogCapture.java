package com.example.strategy.engine.unit.condition;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * Захват журнального выхода интерпретатора.
 *
 * <p><b>У предмета выход не только булев, и это несущее свойство.</b>
 * Третью причину лжи — «тип интерпретатором не исполняется вовсе» — по
 * значению отличить нельзя: ветвь неисполнимого типа пишет предупреждение,
 * а ветви недоступного операнда и посчитанной лжи не пишут ничего
 * (`.claude/tests/cases/strategy-engine-condition.md` §«Новая ось формы:
 * выход булев, и ложь у него одна на все причины»). Без этого следа
 * «тип не исполняется» неотличимо от «предикат честно ложен».
 *
 * <p><b>Снимается след на уровне ПРЕДМЕТА, а не сервиса:</b> приёмник
 * вешается на логгер самого интерпретатора, подменять при этом нечего —
 * логгер у класса свой, и запись производит он сам.
 */
final class EvaluatorLogCapture implements AutoCloseable {

    private final Logger logger = (Logger) LoggerFactory.getLogger(StrategyConditionEvaluator.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private EvaluatorLogCapture() {
        appender.start();
        logger.addAppender(appender);
    }

    /** Приёмник, снимающий записи интерпретатора до закрытия. */
    static EvaluatorLogCapture attach() {
        return new EvaluatorLogCapture();
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
