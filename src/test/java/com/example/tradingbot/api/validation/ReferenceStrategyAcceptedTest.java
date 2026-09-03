package com.example.tradingbot.api.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.api.model.request.CreateStrategyApiRequest;
import com.example.tradingbot.api.model.strategy.StrategyDetailApiModel;
import com.example.tradingbot.api.model.strategy.StrategyPositionActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyStepApiModel;
import com.example.tradingbot.api.model.strategy.StrategyTrancheApiModel;
import com.example.tradingbot.config.RiskAppetiteProperties;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Связывает эталон репозитория с api-моделью и охраной создания: эталон
 * обязан РАЗБИРАТЬСЯ целиком и ПРОХОДИТЬ создание.
 *
 * <p>Несущее: прежде разбор падал до валидации — эталон несёт действие
 * {@code actionKind: POSITION}, а перечень подтипов знал только
 * {@code ORDER} и {@code ALGO_ORDER}. Пока подтипа не было, эталон нельзя
 * было ни создать через API, ни прогнать сквозь create-валидацию целиком;
 * тесты читали его артефакт деревом, то есть мерили не ту сторону.
 *
 * <p>Второе несущее — <b>два уровня объявления</b>: поведение входа
 * объявляют транши, на детали живёт только узкая агрегатная поверхность.
 * Ключи их map читаются РАЗНЫМИ перечнями статусов, и подмена одного
 * другим на эталоне видна сразу.
 */
class ReferenceStrategyAcceptedTest {

    private static final String REFERENCE_STRATEGY = "strategy-examples/trend-following-ema.json";

    @Test
    @DisplayName("Эталон разбирается api-моделью целиком, включая действие выхода над позицией")
    void referenceDeserializesWithPositionAction() throws IOException {
        CreateStrategyApiRequest request = assertDoesNotThrow(this::readReference);

        StrategyDetailApiModel bull = request.getDetails().getFirst();
        List<StrategyStepApiModel> exitSteps = bull.getStepsByStatus().get(Deal.Status.ACTIVE.name());
        assertNotNull(exitSteps, "агрегатная поверхность объявлена ключом статуса СДЕЛКИ");
        assertEquals(StrategyStepType.EXIT.name(), exitSteps.getFirst().getStepType());
        assertTrue(exitSteps.getFirst().getActions().getFirst() instanceof StrategyPositionActionApiModel,
                "выход объявлен действием над позицией");
    }

    @Test
    @DisplayName("Поведение входа эталон объявляет ТРАНШЕМ, а не деталью")
    void referenceDeclaresEntryOnTranche() throws IOException {
        StrategyDetailApiModel bull = readReference().getDetails().getFirst();

        List<StrategyTrancheApiModel> tranches = bull.getTranches();
        assertEquals(1, tranches.size());
        assertEquals(1, tranches.getFirst().getLevelCount());
        assertNotNull(tranches.getFirst().getStepsByStatus().get(DealTranche.Status.PRECHECK.name()));
        assertNotNull(tranches.getFirst().getStepsByStatus().get(DealTranche.Status.MANAGING.name()));
    }

    @Test
    @DisplayName("Эталон проходит охрану создания под числами профиля test")
    void referencePassesCreateValidation() throws IOException {
        CreateStrategyApiRequest request = readReference();

        assertDoesNotThrow(() -> new StrategyCreateRequestValidator(testProfileAppetite()).validateCreate(request));
    }

    @Test
    @DisplayName("Шаг не из узкой поверхности на детали отвергается адресно")
    void nonExitStepOnDealLevelIsRejected() throws IOException {
        CreateStrategyApiRequest request = readReference();
        StrategyDetailApiModel bull = request.getDetails().getFirst();
        bull.getStepsByStatus().get(Deal.Status.ACTIVE.name()).getFirst()
                .setStepType(StrategyStepType.MAIN_PROTECTION.name());

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_DEAL_LEVEL_STEP_OUT_OF_SCOPE"), reason);
    }

    @Test
    @DisplayName("Торгуемая деталь без объявлений траншей отвергается: входа у неё нет")
    void tradableDetailWithoutTranchesIsRejected() throws IOException {
        CreateStrategyApiRequest request = readReference();
        request.getDetails().getFirst().setTranches(List.of());

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_TRANCHE_NOT_DECLARED"), reason);
    }

    @Test
    @DisplayName("Смещение уровня без сетки отвергается: у нешаблонного объявления смещать нечего")
    void levelStepWithoutGridIsRejected() throws IOException {
        CreateStrategyApiRequest request = readReference();
        request.getDetails().getFirst().getTranches().getFirst().setLevelStep(new BigDecimal("10"));

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_TRANCHE_LEVEL_STEP_UNEXPECTED"), reason);
    }

    @Test
    @DisplayName("Сетка без смещения уровня отвергается: уровни совпали бы ценой")
    void gridWithoutLevelStepIsRejected() throws IOException {
        CreateStrategyApiRequest request = readReference();
        request.getDetails().getFirst().getTranches().getFirst().setLevelCount(3);

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_TRANCHE_LEVEL_STEP_MISSING"), reason);
    }

    @Test
    @DisplayName("Необъявленный признак переоткрытия отвергается: умолчания у него нет")
    void undeclaredReopenFlagIsRejected() throws IOException {
        CreateStrategyApiRequest request = readReference();
        request.getDetails().getFirst().getTranches().getFirst().setPositionReopenAllowed(null);

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_TRANCHE_REOPEN_NOT_DECLARED"), reason);
    }

    @Test
    @DisplayName("Пустой levelCount отвергается: единица мажорировала бы его в разрешающую сторону")
    void undeclaredLevelCountIsRejected() throws IOException {
        CreateStrategyApiRequest request = readReference();
        request.getDetails().getFirst().getTranches().getFirst().setLevelCount(null);

        String reason = rejectReason(request);

        assertTrue(reason.contains("STRATEGY_TRANCHE_LEVEL_COUNT_NOT_DECLARED"), reason);
        assertFalse(reason.contains("STRATEGY_TRANCHE_LEVEL_STEP"), "адресует отказ тот конъюнкт, который ложен");
    }

    private String rejectReason(CreateStrategyApiRequest request) {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> new StrategyCreateRequestValidator(testProfileAppetite()).validateCreate(request));
        return failure.getReason();
    }

    /** Числа профиля test: равенство объявлениям эталона — самая тесная планка, которая его ещё пропускает. */
    private RiskAppetiteProperties testProfileAppetite() {
        RiskAppetiteProperties properties = new RiskAppetiteProperties();
        properties.setGlobalSimultaneousRiskPerDealPercent(BigDecimal.ONE);
        properties.setGlobalCatastrophicRiskPerDealMultiplier(new BigDecimal("100"));
        properties.setGlobalConsecutiveLossLimit(3);
        return properties;
    }

    private CreateStrategyApiRequest readReference() throws IOException {
        return new ObjectMapper().readValue(new ClassPathResource(REFERENCE_STRATEGY).getInputStream(),
                CreateStrategyApiRequest.class);
    }
}
