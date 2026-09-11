package com.example.auditstatistics.domain.model;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

/**
 * Запрошенный отбор строк агрегатов — доменная форма вопроса читателя
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Ограничения отбора живут у владельца агрегатов, а не здесь</b>
 * (.claude/rules/policy-home.md): этот класс их <b>выражает</b>
 * предикатами, а состав не пересказывает.
 *
 * <p><b>Зерно обязательно и с закрытым перечнем</b>
 * ({@link AggregateGrain}), <b>радиус тенанта обязателен и НЕ
 * СВЕРЯЕТСЯ</b> — он приезжает заголовком контекста, который ставит
 * периметр, и доверие к значению добывается не здесь
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * <p><b>Окно меряется СУТКАМИ ЗЕРНА, а не моментом.</b> Предел ширины у
 * этой выборки поэтому свой, и журнальным числом он не выражается: тот
 * меряется моментом происшествия. Оба консервативны в сторону меньшего
 * окна.
 *
 * <p><b>Курсор лежит компонентами КЛЮЧА ЗЕРНА, а не собранной парой.</b>
 * Ключ зерна уникален по построению ({@code uk_deal_aggregate_grain},
 * {@code uk_incident_aggregate_grain}), поэтому второго операнда курсору
 * не заводится. Разложенный по компонентам, он оставляет выразимым
 * состояние «курсор назван наполовину»: собранная позиция с пустой
 * половиной была бы неотличима от отсутствия курсора.
 *
 * <p><b>У двух компонентов курсора пустота есть ЗНАЧЕНИЕ, а не
 * пропуск.</b> Определение стратегии и расчётная валюта законно пусты в
 * ключе сделочного зерна, и курсор, их не назвавший, указывает ровно на
 * строку с пустым ключом. Отсюда «половина курсора» мерится по двум
 * <b>обязательным</b> компонентам — сутки зерна и биржевой счёт, — а не по
 * всем четырём.
 */
@Value
@Builder
public class AggregateQuery {

    /** Тенант-владелец строк: радиус отбора, операнд вызова. */
    String tenantId;

    /** Зерно строки; пусто — не названо либо названо вне перечня. */
    AggregateGrain grain;

    /** Левая граница окна по суткам зерна, включающая. */
    LocalDate from;

    /** Правая граница окна по суткам зерна, включающая. */
    LocalDate to;

    /** Сутки зерна последней прочитанной строки — обязательный компонент позиции. */
    LocalDate cursorBucketDate;

    /** Биржевой счёт последней прочитанной строки — обязательный компонент позиции. */
    String cursorExchangeAccountInternalId;

    /** Определение стратегии позиции; пусто означает строку с пустым ключом. */
    String cursorStrategyInternalId;

    /** Расчётная валюта позиции; пусто означает строку с пустым ключом. */
    String cursorResultCurrency;

    /**
     * Зерно названо значением перечня.
     *
     * <p>Ветвей у отказа две — не названо и названо вне перечня, — но исход
     * у них один: читатель обязан назвать одно из двух значений
     * ({@link AggregateGrain#resolve}).
     */
    public Boolean hasGrain() {
        return nonNull(grain);
    }

    /**
     * Окно названо обеими границами.
     *
     * <p>Одна половина окном не является: запрос с открытым концом читает
     * ряд без предела ровно так же, как запрос без окна вовсе.
     */
    public Boolean hasWindow() {
        return nonNull(from) && nonNull(to);
    }

    /**
     * Границы окна стоя́т в порядке: правая не раньше левой.
     *
     * <p><b>Перевёрнутое окно — отказ, а не пустая выдача:</b> пустая
     * выдача читалась бы как «за этот период не было ни одной строки», то
     * есть отвечала бы на вопрос, которого читатель не задавал
     * (docs/concept.md, П1).
     */
    public Boolean isWindowOrdered() {
        if (isNull(from) || isNull(to)) {
            return Boolean.FALSE;
        }
        return isFalse(to.isBefore(from));
    }

    /**
     * Окно шире названного предела.
     *
     * <p><b>Ширина считается ВКЛЮЧАЮЩИМ числом суток:</b> границы окна
     * включающие, и окно {@code [сутки; те же сутки]} есть окно шириной в
     * одни сутки, а не в нулевые.
     *
     * <p>Сравнение строгое: окно, равное пределу, дозволено — предел есть
     * наибольшее допустимое число суток, а не первое запрещённое.
     */
    public Boolean isWindowWiderThan(Integer maxWindowDays) {
        if (isNull(from) || isNull(to)) {
            return Boolean.FALSE;
        }
        return DAYS.between(from, to) + 1 > maxWindowDays;
    }

    /** Позиция продолжения названа обоими обязательными компонентами. */
    public Boolean hasCursor() {
        return nonNull(cursorBucketDate) && nonNull(cursorExchangeAccountInternalId);
    }

    /**
     * Курсор назван наполовину: какой-то его компонент есть, а обоих
     * обязательных нет.
     *
     * <p><b>Половина позиции — отказ, а не чтение с начала окна.</b>
     * Молчаливое чтение с начала отдало бы читателю уже прочитанную
     * страницу под видом следующей.
     */
    public Boolean hasPartialCursor() {
        if (isTrue(hasCursor())) {
            return Boolean.FALSE;
        }
        return nonNull(cursorBucketDate)
                || nonNull(cursorExchangeAccountInternalId)
                || nonNull(cursorStrategyInternalId)
                || nonNull(cursorResultCurrency);
    }

    /**
     * Курсор несёт компоненты не выбранного зерна.
     *
     * <p><b>Лишний компонент — отказ, а не молчаливое его игнорирование.</b>
     * Определения стратегии и расчётной валюты в ключе зерна происшествий
     * нет вовсе; названные при этом зерне, они означают, что читатель
     * смешал зёрна, — и позиция становится неинтерпретируемой. Прочитанная
     * без них, она отдала бы страницу, о которой читатель не спрашивал
     * (docs/concept.md, П1).
     *
     * <p>Обратной ветви у предиката нет: у сделочного зерна чужих
     * компонентов не существует — его ключ несёт все четыре.
     */
    public Boolean hasForeignGrainCursor() {
        if (AggregateGrain.DEAL.equals(grain)) {
            return Boolean.FALSE;
        }
        return nonNull(cursorStrategyInternalId) || nonNull(cursorResultCurrency);
    }
}
