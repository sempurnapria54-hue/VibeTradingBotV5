package com.example.tradingbot.domain.model.trade.candle;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.Auditable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Группа свечей одного инструмента и одного таймфрейма — единица
 * загрузки/докачки/проверки целостности свечной истории. Несёт
 * фактические границы загруженной истории, поддерживаемый count и
 * заказанный горизонт «до куда грузить». См.
 * docs/models/domain/other/CandleGroup.md,
 * docs/lifecycles/CandleGroup.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroup extends Auditable {

    /** Новый закрытый бар существует, когда прошло ≥ 2 длительностей бара после actualLast. */
    private static final long CLOSED_BAR_FACTOR = 2L;

    /** Внутренний идентификатор группы. */
    private Long id;

    /**
     * Межсервисный идентификатор группы (наружу отдаётся вместо id).
     * Генерируется системой при заведении группы из internalId
     * инструмента и таймфрейма; уникален.
     */
    private String internalId;

    /** Инструмент-владелец (Instrument.id). */
    private Long instrumentId;

    /** Канонический таймфрейм группы. */
    private TimeFrame timeframe;

    /** Таймфрейм в формате биржи (сырой, например 1H). */
    private String externalTimeframe;

    /** Статус жизненного цикла загрузки свечей. */
    private Status status;

    /**
     * Горизонт бэкфилла: нижняя граница истории, заказанная
     * потребителем (UTC мс); пусто — глубина не названа, и выкачка
     * идёт до конца истории площадки.
     *
     * <p><b>Горизонт принадлежит ГРУППЕ, а не инструменту.</b> Глубину
     * называет требование потребителя вместе с таймфреймом, и у 1m и
     * 1D одного инструмента она разная: общий горизонт на инструмент
     * качал бы годы минутных баров ради дневного требования
     * (docs/processes/candle-loading.md §«Кто заводит группу»).
     */
    private Long plannedFirstUtcMillis;

    /** Время открытия первой фактически загруженной свечи (UTC мс). */
    private Long actualFirstUtcMillis;

    /** Время открытия последней фактически загруженной свечи (UTC мс). */
    private Long actualLastUtcMillis;

    /** Поддерживаемое число свечей в группе (основа проверки целостности). */
    private Long count;

    /**
     * Ожидаемое по density-инварианту число свечей на фактических
     * границах [actualFirst, actualLast]:
     * {@code (actualLast - actualFirst) / step + 1}. Для пустой
     * группы (границы не заданы) — 0.
     */
    public Long expectedCount() {
        if (isNull(actualFirstUtcMillis) || isNull(actualLastUtcMillis)) {
            return 0L;
        }
        long step = timeframe.getDurationMillis();
        return (actualLastUtcMillis - actualFirstUtcMillis) / step + 1L;
    }

    /**
     * Ряд плотен на [actualFirst, actualLast]: поддерживаемый count
     * совпадает с ожидаемым по density-инварианту. Дотягивание нижней
     * границы до планового горизонта — забота BACKFILL, не плотности.
     */
    public Boolean isDense() {
        long expected = expectedCount();
        long actual = isNull(count) ? 0L : count;
        return actual == expected;
    }

    /** Группа готова (покрытие подтверждено, дыр нет). */
    public Boolean isActive() {
        return Objects.equals(status, Status.ACTIVE);
    }

    /**
     * Группа выведена из живого цикла загрузки: разбор ошибки или
     * логическое удаление. Такую группу циклы не ведут и требование
     * потребителя её не оживляет — иначе исчерпанные попытки докачки
     * начинались бы заново от чужой команды.
     */
    public Boolean isTerminal() {
        return Objects.equals(status, Status.ERROR) || Objects.equals(status, Status.DELETED);
    }

    /**
     * Появился ли новый закрытый бар к моменту {@code nowMillis}: после
     * открытия последней загруженной свечи прошло ≥ {@code CLOSED_BAR_FACTOR}
     * длительностей бара. Пустая группа (нет actualLast) — нет.
     */
    public Boolean hasNewClosedBar(Long nowMillis) {
        if (isNull(actualLastUtcMillis)) {
            return false;
        }
        long step = timeframe.getDurationMillis();
        return nowMillis - actualLastUtcMillis >= CLOSED_BAR_FACTOR * step;
    }

    /** Статус жизненного цикла загрузки свечей группы. */
    public enum Status {

        /** Группа заведена, загрузка не начиналась. */
        CREATED,

        /** Выкачка истории «в глубину» до планового горизонта. */
        BACKFILL,

        /** Докачка хвоста (свежие закрытые бары). */
        SYNC,

        /** Проверка целостности ряда по count. */
        CHECK,

        /** Докачка дыр (поиск и заполнение пропусков). */
        REPAIR,

        /** Ряд плотен и поддерживается — группа активна. */
        ACTIVE,

        /** Ошибка загрузки/целостности. */
        ERROR,

        /** Группа удалена (логически). */
        DELETED
    }
}
