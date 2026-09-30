package com.example.marketdata.util;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Константы сервиса рыночных данных: величины, осмысленные больше чем в
 * одном классе. Один класс-дом с вложенными по теме
 * (.claude/rules/codestyle.md §Константы).
 */
@UtilityClass
public class Constants {

    /** Точность денежных и ценовых величин в схеме. */
    @UtilityClass
    public class Price {

        /** Точность (всего значащих цифр) для денежных/ценовых величин. */
        public static final int PRECISION = 36;

        /** Масштаб (знаков после запятой) для денежных/ценовых величин. */
        public static final int SCALE = 18;
    }

    /** Разделители составных внутренних идентификаторов. */
    @UtilityClass
    public class InternalId {

        /** Разделитель сегментов составного internalId (группа свечей, идентичность вычисления). */
        public static final String SEPARATOR = ":";
    }

    /** Отборы каталога инструментов по онбординг-статусу. */
    @UtilityClass
    public class InstrumentCatalog {

        /**
         * Статусы, на которых ведётся навес правил: их обходит обновление
         * правил тика синка каталога, и ими же ограничен действующий листинг
         * поверхности каталога (docs/lifecycles/Instrument.md §«Листинг
         * наружу — статусы, на которых ведётся навес правил»).
         *
         * <p><b>Носитель один на обоих читателей намеренно.</b> Листинг
         * наружу не шире охвата обновления правил: строка вне этого охвата
         * пришла бы в проекцию торгового ядра с правилами, которых никто не
         * обновлял. Две записи одного перечня разошлись бы первой правкой.
         *
         * <p>Популяция прохода невосполнимых срезов — другая (весь листинг,
         * кроме снятого с торгов) и этим перечнем не выражается.
         */
        public static final Set<Instrument.Status> RULES_MAINTAINED_STATUSES = Set.of(
                Instrument.Status.SYNC,
                Instrument.Status.CANDLES_LOADING,
                Instrument.Status.ACTIVE);
    }
}
