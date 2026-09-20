package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.mapping.TimeFrameMapper;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Доменный таймфрейм → бар площадки — группа `U31` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/TimeFrame.md;
 * docs/integrations/okx/rules/timeframe-constants.md).
 *
 * <p><b>Базовая сборка:</b> {@code TimeFrameMapper} (порождённая
 * реализация); на вход — значение доменного перечня, на выходе — строка
 * бара площадки.
 *
 * <p><b>Соответствие строгое, и регистр — часть значения:</b> ни
 * нижний, ни верхний регистр не нормализуются, а дневной бар выбран
 * UTC-выровненным ради сквозного правила времени.
 *
 * <p>Кейс {@code U31.9} (секундный бар) в код не пошёл: дом объявляет
 * ветку бросающей и строку незаведённой, а ветка отдаёт строку —
 * `.claude/work/backlog.md` §«Ветка секундного бара маппера таймфрейма:
 * дом объявляет бросок, код отдаёт строку».
 */
class TimeFrameToBarTest {

    private final TimeFrameMapper mapper = Mappers.timeFrame();

    @ParameterizedTest
    @CsvSource({
            "ONE_MINUTE,1m",
            "THREE_MINUTES,3m",
            "FIVE_MINUTES,5m",
            "FIFTEEN_MINUTES,15m"})
    @DisplayName("U31.1-U31.4 — минутные бары")
    void u31_1_to_4_theMinuteBars(TimeFrame timeFrame, String expected) {
        assertThat(mapper.domainToOkx(timeFrame)).isEqualTo(expected);
    }

    /** Регистр — часть значения: у площадки это разные строки. */
    @ParameterizedTest
    @CsvSource({"ONE_HOUR,1H", "TWO_HOURS,2H", "FOUR_HOURS,4H"})
    @DisplayName("U31.5-U31.7 — часовые бары пишутся заглавной")
    void u31_5_to_7_theHourBarsAreUpperCase(TimeFrame timeFrame, String expected) {
        assertThat(mapper.domainToOkx(timeFrame)).isEqualTo(expected);
    }

    /** Дневной бар без пометки открывается со смещением, и ряд поехал бы. */
    @Test
    @DisplayName("U31.8 — дневной бар UTC-выровнен")
    void u31_8_theDayBarIsUtcAligned() {
        assertThat(mapper.domainToOkx(TimeFrame.ONE_DAY)).isEqualTo("1Dutc").isNotEqualTo("1D");
    }

    /** Охрана написана явно. */
    @Test
    @DisplayName("U31.10 — пустота вместо таймфрейма даёт пустоту")
    void u31_10_emptinessInIsEmptinessOut() {
        assertThat(mapper.domainToOkx(null)).isNull();
    }

    /** Потребителя у обратного направления не заведено. */
    @Test
    @DisplayName("U31.11 — обратного направления перехода нет вовсе")
    void u31_11_thereIsNoReverseTransition() {
        assertThat(Arrays.stream(TimeFrameMapper.class.getDeclaredMethods()).map(Method::getName).toList())
                .containsExactly("domainToOkx");
        assertThat(Arrays.stream(TimeFrameMapper.class.getDeclaredMethods())
                .noneMatch(method -> TimeFrame.class.equals(method.getReturnType()))).isTrue();
    }
}
