package com.example.tradingcore.unit.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingcore.domain.command.resolve.PositionStatusResolver;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Статус эпизода позиции — группа {@code U9} документа
 * `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/components/PositionStatusResolver.md; звено Z22).
 *
 * <p><b>Пустота здесь — нормальный факт закрытия, а не ненайденность.</b>
 * Терминала {@code MISSING_AFTER_REFRESH} у позиции не бывает: её финал
 * объясняется закрытием на бирже.
 *
 * <p><b>Базовая сборка:</b> {@code new PositionStatusResolver()};
 * аргумент — добытая позиция либо её отсутствие. Конфигурации у единицы
 * нет вовсе.
 */
class PositionStatusResolveTest {

    private final PositionStatusResolver resolver = new PositionStatusResolver();

    private static Position fetchedPosition(String size) {
        Position position = new Position();
        position.setId(7L);
        position.setExternalId("pos-1");
        position.setExternalSize(new BigDecimal(size));
        return position;
    }

    @Test
    @DisplayName("U9.1 — позиция добыта: статус живая, кандидат причины пуст")
    void u9_1_aFetchedPositionIsActiveWithoutACloseReason() {
        StatusResolveResult<Position.Status, Position.CloseReason> result =
                resolver.resolve(fetchedPosition("1"));

        assertThat(result.getStatus()).isEqualTo(Position.Status.ACTIVE);
        assertThat(result.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U9.2 — позиция не добыта: закрыта на бирже, и это нормальный факт")
    void u9_2_anUnfetchedPositionIsClosedByTheExchange() {
        StatusResolveResult<Position.Status, Position.CloseReason> result = resolver.resolve(null);

        assertThat(result.getStatus()).isEqualTo(Position.Status.CLOSED);
        assertThat(result.getCloseReason())
                .as("успешное «не найдено» — нормальный факт, а не отказ границы")
                .isEqualTo(Position.CloseReason.EXTERNAL_CLOSE);
    }

    @Test
    @DisplayName("U9.3 — позиция добыта с нулевым размером: статус живая")
    void u9_3_aZeroSizedFetchedPositionIsStillActive() {
        assertThat(resolver.resolve(fetchedPosition("0")).getStatus())
                .as("отсутствие живого риска — предикат доменной модели, а не исход резолва; "
                        + "налив позиции резолвер не читает (Z22)")
                .isEqualTo(Position.Status.ACTIVE);
    }

    @Test
    @DisplayName("U9.4 — переданный объект резолвером не меняется: отдаётся result-object")
    void u9_4_theArgumentIsLeftUntouched() {
        Position fetched = fetchedPosition("1");

        resolver.resolve(fetched);

        assertThat(fetched.getStatus()).as("статус на модель не кладётся — применяет исполнитель").isNull();
        assertThat(fetched.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U9.5 — множество исходов ровно два: третьего значения резолвер не производит")
    void u9_5_thereAreExactlyTwoOutcomes() {
        List<StatusResolveResult<Position.Status, Position.CloseReason>> outcomes =
                List.of(resolver.resolve(fetchedPosition("1")), resolver.resolve(null));

        assertThat(outcomes).extracting(StatusResolveResult::getStatus)
                .containsExactly(Position.Status.ACTIVE, Position.Status.CLOSED);
        assertThat(outcomes).extracting(StatusResolveResult::getCloseReason)
                .as("терминала ненайденности у позиции не бывает")
                .containsExactly(null, Position.CloseReason.EXTERNAL_CLOSE);
    }
}
