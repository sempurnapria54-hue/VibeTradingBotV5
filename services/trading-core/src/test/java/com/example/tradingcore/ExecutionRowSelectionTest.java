package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryError;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.mapping.DealActionStateMapper;
import com.example.tradingcore.mapping.DealActionStateMapperImpl;
import com.example.tradingcore.mapping.RuntimeJsonConverter;
import com.example.tradingcore.persistence.model.DealStrategyActionStateEntity;
import com.example.tradingcore.persistence.model.DealSystemActionStateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Отбор строк исполнения в контексте прохода и их переход через границу
 * хранения.
 *
 * <p><b>Предмет отбора — область «эпизод».</b> Переоткрытие ведётся ТЕМ
 * ЖЕ траншем, поэтому без номера эпизода строки прошлого эпизода
 * неотличимы от строк текущего, и шаг, применённый однажды, читался бы
 * применённым и после переоткрытия
 * (docs/rules/strategy-step-once-per-episode.md). Пустой транш при этом
 * участвует в отборе как ПУСТОЙ, а не как «любой»: иначе агрегатное
 * исполнение подхватило бы строку потраншевого.
 *
 * <p><b>Предмет перехода — вид действия, кодируемый таблицей.</b> Колонки
 * рода в схеме нет, значит вид обязан ставиться константой своей таблицы
 * на обратном пути; ошибись он — системная строка читалась бы
 * стратегийной и ушла бы не в ту таблицу при сохранении.
 */
class ExecutionRowSelectionTest {

    private static final Long ACTION = 11L;
    private static final Long TRANCHE = 5L;

    private final DealActionStateMapper mapper =
            new DealActionStateMapperImpl(new RuntimeJsonConverter(new ObjectMapper()));

    /** Строка прошлого эпизода не отдаётся за строку текущего. */
    @Test
    void previousEpisodeRowIsNotTakenForTheCurrentOne() {
        DealActionState previous = strategyRow(TRANCHE, 1);
        DealActionState current = strategyRow(TRANCHE, 2);
        DealContext context = DealContext.builder().actionStates(List.of(previous, current)).build();

        assertThat(context.actionState(ACTION, tranche(TRANCHE, 2))).contains(current);
        assertThat(context.actionState(ACTION, tranche(TRANCHE, 1))).contains(previous);
    }

    /**
     * Исполнение уровня сделки отбирается пустым траншем, а не «любым»:
     * потраншевая строка того же узла на этот отбор не откликается.
     */
    @Test
    void dealLevelSelectionDoesNotPickUpATrancheRow() {
        DealActionState trancheRow = strategyRow(TRANCHE, 1);
        DealContext context = DealContext.builder().actionStates(List.of(trancheRow)).build();

        assertThat(context.actionState(ACTION, null)).isEmpty();
    }

    /**
     * Живой системной строкой считается только незавершённая: новая
     * надобность заводит новую строку, а не оживляет отказавшую.
     */
    @Test
    void onlyUnfinishedSystemRowCountsAsLive() {
        DealActionState failed = systemRow(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        failed.setStatus(DealActionStateStatus.FAILED);
        DealContext context = DealContext.builder().actionStates(List.of(failed)).build();

        assertThat(context.liveSystemActionState(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, null)).isEmpty();
        assertThat(context.systemActionStates(SystemActionType.FINALIZE_DEAL_EXIT_ACTION)).containsExactly(failed);
    }

    /**
     * Строка, заведённая этим же проходом, регистрируется в уже собранном
     * контексте: без этого два запроса одного системного действия за
     * проход завели бы две живые строки и столкнулись бы на частичном
     * ключе.
     */
    @Test
    void rowCreatedWithinThePassBecomesVisible() {
        DealContext context = DealContext.builder().build();
        DealActionState fresh = systemRow(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION);

        context.register(fresh);
        context.register(fresh);

        assertThat(context.getActionStates()).containsExactly(fresh);
        assertThat(context.liveSystemActionState(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, null))
                .contains(fresh);
    }

    /** Вид действия ставится константой своей таблицы: колонки рода в схеме нет. */
    @Test
    void actionKindComesFromTheTableNotFromAColumn() {
        DealStrategyActionStateEntity strategyEntity =
                mapper.domainToStrategyPersistence(strategyRow(TRANCHE, 1));
        DealSystemActionStateEntity systemEntity =
                mapper.domainToSystemPersistence(systemRow(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION));

        assertThat(mapper.strategyPersistenceToDomain(strategyEntity).getActionKind())
                .isEqualTo(ActionKind.STRATEGY);
        assertThat(mapper.systemPersistenceToDomain(systemEntity).getActionKind())
                .isEqualTo(ActionKind.SYSTEM);
    }

    /**
     * Последняя ошибка едет навесом целиком: тройка «код, сообщение,
     * классификация» описывает один факт, и потеря классификации на
     * обратном пути сделала бы повторяемость неопределимой после
     * рестарта.
     */
    @Test
    void lastErrorSurvivesBothHalvesOfTheOverlay() {
        DealActionState row = strategyRow(TRANCHE, 1);
        row.setLastError(new RetryError("51008", "insufficient balance", RuntimeErrorCode.EXCHANGE_ERROR));

        DealActionState restored = mapper.strategyPersistenceToDomain(
                mapper.domainToStrategyPersistence(row));

        assertThat(restored.getLastError().code()).isEqualTo("51008");
        assertThat(restored.getLastError().message()).isEqualTo("insufficient balance");
        assertThat(restored.getLastError().type()).isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR);
    }

    private static DealActionState strategyRow(Long trancheId, Integer episodeSeq) {
        DealActionState row = new DealActionState();
        row.setDealId(1L);
        row.setActionKind(ActionKind.STRATEGY);
        row.setStrategyActionId(ACTION);
        row.setDealTrancheId(trancheId);
        row.setTrancheEpisodeSeq(episodeSeq);
        row.setStatus(DealActionStateStatus.PLANNED);
        return row;
    }

    private static DealActionState systemRow(SystemActionType type) {
        DealActionState row = new DealActionState();
        row.setDealId(1L);
        row.setActionKind(ActionKind.SYSTEM);
        row.setSystemActionType(type);
        row.setStatus(DealActionStateStatus.PLANNED);
        return row;
    }

    private static DealTranche tranche(Long id, Integer episodeSeq) {
        DealTranche tranche = new DealTranche();
        tranche.setId(id);
        tranche.setEpisodeSeq(episodeSeq);
        return tranche;
    }
}
