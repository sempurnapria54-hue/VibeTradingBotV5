package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Выключатель проактивной детекции — клетка {@code B7.10}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> положение оси
 * {@code anomaly-job.enabled} есть её предмет. Расписание у прогона
 * глушится ВЫРАЖЕНИЕМ такта, а не выключателем, — иначе этой клетке нечего
 * было бы мерить ({@link TradingCoreSubstrate} §шапка).
 *
 * <p><b>Клетка предъявляет НАЗВАННОЕ ограничение предела слепоты, а не
 * дефект.</b> Счёт двигают только СОСТОЯВШИЕСЯ проходы: ненаблюдённый
 * увеличивает, наблюдённый обнуляет, а тик, не исполнившийся вовсе,
 * оставляет счёт нулевым — и ступень не поднимается никогда. Молчание
 * читается как «наблюдение идёт, аномалий нет»
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»;
 * .claude/work/backlog.md §«Слепота считается состоявшимися проходами, а
 * тропа входа её не спрашивает»).
 *
 * <p><b>Срез при этом поставлен НЕПОЛНЫМ, и это несущее.</b> Выключенный
 * тик, которому и считать было бы нечего, зелен при любом поведении
 * выключателя: клетка мерит разницу между «слепоты не было» и «слепота
 * была, но её никто не считал».
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}.
 */
class DisabledDetectionBoxTest extends TradingCoreBox {

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-disabled-detection";

    /** Отсутствие ступени: рабочее состояние радиуса. */
    private static final String NO_RUNG = "ACTIVE";

    /** Сколько интервалов прошло без исполнения тика. */
    private static final Integer INTERVALS = 3;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.ANOMALY_ENABLED_KEY, "false"));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B7.10 — счёт слепоты двигают только состоявшиеся проходы")
    void onlyTheExecutedPassesMoveTheBlindnessCount() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        // Срез неполон: будь тик исполнен, каждый интервал стои́л бы
        // единицы счёта, и на третьем поднялась бы мягкая ступень.
        connector.answers(positionsPath(ACCOUNT), 503, "{\"message\": \"box stub silence\"}");
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());
        assertThat(blindPasses()).isZero();

        ticks(Tick.ANOMALY_DETECTION, INTERVALS);

        // Счёт нулевой, ступени нет, журнал пуст: не-исполнение тика от
        // «наблюдение идёт, аномалий нет» не отличается ничем.
        assertThat(blindPasses()).isZero();
        assertThat(accountRung()).isEqualTo(NO_RUNG);
        assertThat(rows.count("anomaly_reports")).isZero();
        // К площадке выключенный тик не ходит: выключатель стои́т до
        // выборки счетов (.claude/rules/codestyle.md §Джобы).
        assertThat(connector.count()).isZero();
    }

    /** Строка биржевого счёта, как её видит база. */
    private Map<String, Object> accountRow() {
        return rows.row("exchange_accounts", "internal_id", ACCOUNT);
    }

    /** Ступень счёта, как её видит база. */
    private String accountRung() {
        return String.valueOf(accountRow().get("safety_rung"));
    }

    /** Счёт подряд идущих ненаблюдённых проходов на строке счёта. */
    private Integer blindPasses() {
        return ((Number) accountRow().get("blind_pass_count")).intValue();
    }

    /** Корень путей счёта у коннектора. */
    private String accountPath(String accountInternalId) {
        return "/api/v1/accounts/" + accountInternalId;
    }

    /** Живые позиции счёта целиком: первый срез проактивной детекции. */
    private String positionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions";
    }

    /** Живые заявки счёта целиком: второй срез. */
    private String pendingOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/pending";
    }

    /** Живые отдельные условные заявки счёта целиком: третий срез. */
    private String pendingAlgoOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/algo-orders/pending";
    }
}
