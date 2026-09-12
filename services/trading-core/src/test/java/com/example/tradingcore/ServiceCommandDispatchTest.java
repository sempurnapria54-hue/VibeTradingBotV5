package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.example.tradingcore.config.ServiceCommandRetryProperties;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryBackoffType;
import com.example.tradingcore.domain.command.RetryBudgetExhaustedException;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandRetryPolicy;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.CommandExecutor;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.integration.internal.api.exchange.CredentialsRejectedException;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeIntegrationException;
import com.example.tradingcore.integration.internal.api.exchange.ExternalStatusException;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Диспетчер команд: учёт отказа на анкере и контракт броска.
 *
 * <p><b>Предмет — развязка классов, а не факт вызова исполнителя.</b>
 * Контракт объявляет, что контролируемое исключение, отказ ключей и
 * исчерпание бюджета уходят наружу ТРЕМЯ РАЗНЫМИ классами, потому что
 * реакция на них разная (docs/components/ServiceCommandExecutor.md
 * §«Контракт броска»). Свернись любой из них в результат — выделенный
 * обработчик прохода класса не увидит, и безусловная биржевая ступень 2
 * не поднимется; проверяется ровно это.
 */
class ServiceCommandDispatchTest {

    private static final Long DEAL = 7L;
    private static final Long ROW = 42L;

    private final CommandExecutor executor = mock(CommandExecutor.class);
    private final DealActionStateDataService dataService = mock(DealActionStateDataService.class);

    private ServiceCommandExecutor dispatcher;

    /**
     * Реестр диспетчера собирается КОНСТРУКТОРОМ по объявленному типу
     * исполнителя, поэтому тип объявляется до сборки: маршрут — по типу
     * команды, а не по порядку в списке.
     */
    @BeforeEach
    void setUp() {
        when(executor.supportedType()).thenReturn(ServiceCommandType.SUBMIT_ORDER_COMMAND);
        dispatcher = new ServiceCommandExecutor(List.of(executor),
                new RetryPolicyService(properties(2)), dataService);
    }

    /**
     * Контролируемое исключение уходит наружу СВОИМ классом, а строка
     * закрывается отказом до броска.
     *
     * <p>Общий ловец вернул бы его результатом — и реакция «недоверенная
     * интеграция» не поднялась бы вовсе; порядок «сперва отказ строки,
     * затем бросок» нужен, чтобы реакция не встала над строкой, не
     * отражающей отказ.
     */
    @Test
    void controlledFailureLeavesUntouchedAndClosesTheRow() {
        DealActionState row = strategyRow();
        givenRow(row);
        when(executor.execute(any(), any(), any()))
                .thenThrow(new ExternalStatusException(ExternalStatusReason.ORDER_FAILED, "unknown status"));

        assertThatThrownBy(() -> dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row)))
                .isInstanceOf(ExternalStatusException.class);

        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.FAILED);
        assertThat(row.getLastError().type()).isEqualTo(RuntimeErrorCode.VALIDATION_ERROR);
        verify(dataService).save(row);
    }

    /**
     * Отвергнутые ключи закрывают строку БЕЗ повтора и без эскалации
     * «бюджет кончился»: повторяемость снята природой отказа, а не
     * исчерпанным бюджетом.
     */
    @Test
    void rejectedCredentialsCloseTheRowWithoutRetryAndWithoutEscalation() {
        DealActionState row = strategyRow();
        givenRow(row);
        when(executor.execute(any(), any(), any()))
                .thenThrow(new CredentialsRejectedException("key revoked"));

        assertThatThrownBy(() -> dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row)))
                .isInstanceOf(CredentialsRejectedException.class)
                .isNotInstanceOf(RetryBudgetExhaustedException.class);

        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.FAILED);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getNextRetryAt()).isNull();
    }

    /** Повторяемый отказ в пределах бюджета ставит строку в ожидание повтора с назначенным моментом. */
    @Test
    void retryableFailureWithinBudgetSchedulesTheNextAttempt() {
        DealActionState row = strategyRow();
        givenRow(row);
        when(executor.execute(any(), any(), any()))
                .thenThrow(new ExchangeIntegrationException("gateway timeout"));

        ServiceCommandExecutionResult result =
                dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row));

        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR);
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.RETRY_PENDING);
        assertThat(row.getNextRetryAt()).isNotNull();
    }

    /**
     * Исчерпанный бюджет — самостоятельный класс броска, и он несёт род
     * строки: на СТРАТЕГИЙНОЙ «мы не смогли дозвониться» не основание
     * рвать принятый риск.
     */
    @Test
    void exhaustedBudgetEscalatesWithTheRowKind() {
        DealActionState row = strategyRow();
        row.setAttemptCount(2);
        givenRow(row);
        when(executor.execute(any(), any(), any()))
                .thenThrow(new ExchangeIntegrationException("gateway timeout"));

        assertThatThrownBy(() -> dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row)))
                .isInstanceOf(RetryBudgetExhaustedException.class)
                .satisfies(failure -> assertThat(
                        ((RetryBudgetExhaustedException) failure).getStrategyLevel()).isTrue());

        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.FAILED);
    }

    /**
     * Неожиданный баг приложения не повторяется: код неповторяем, и
     * эскалации «бюджет кончился» он тоже не порождает — она про
     * недозвон, а не про наш дефект.
     */
    @Test
    void applicationDefectIsNotRetriedAndDoesNotEscalate() {
        DealActionState row = strategyRow();
        givenRow(row);
        when(executor.execute(any(), any(), any())).thenThrow(new IllegalStateException("npe-ish"));

        ServiceCommandExecutionResult result =
                dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row));

        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.INTERNAL_ERROR);
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.FAILED);
    }

    /**
     * Отказ, ВОЗВРАЩЁННЫЙ исполнителем, проходит тот же учёт, что и
     * брошенный: иначе анкер завис бы живым, и сделка пересылала бы
     * команду каждый тик.
     */
    @Test
    void returnedFailureGoesThroughTheSameAccounting() {
        DealActionState row = strategyRow();
        givenRow(row);
        when(executor.execute(any(), any(), any())).thenReturn(
                ServiceCommandExecutionResult.failure(RuntimeErrorCode.EXCHANGE_ERROR, "rejected"));

        dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row));

        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.RETRY_PENDING);
        assertThat(row.getAttemptCount()).isEqualTo(1);
    }

    /**
     * Незавершённое звено — третий исход, а не отказ: классификации у
     * него нет, но повтор по бюджету строки он получает. Без этого
     * писатель, которому не хватило полноты графа, вставал бы навсегда:
     * бессрочное ожидание запрещено.
     */
    @Test
    void unfinishedLinkRetriesWithinTheBudgetWithoutAClassification() {
        DealActionState row = strategyRow();
        givenRow(row);
        when(executor.execute(any(), any(), any()))
                .thenReturn(ServiceCommandExecutionResult.notCompleted("deal graph incomplete"));

        ServiceCommandExecutionResult result =
                dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row));

        assertThat(result.getErrorCode()).isNull();
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.RETRY_PENDING);
        assertThat(row.getNextRetryAt()).isNotNull();
    }

    /**
     * Исчерпанный бюджет незавершённого звена уводит строку в отказ и
     * <b>эскалации не порождает</b>: «мы не смогли дозвониться» — про
     * площадку, а здесь площадка ни при чём, и радиусная реакция со
     * снятием живого риска поднималась бы на неполном графе.
     */
    @Test
    void exhaustedBudgetOfAnUnfinishedLinkFailsWithoutEscalation() {
        DealActionState row = strategyRow();
        row.setAttemptCount(2);
        givenRow(row);
        when(executor.execute(any(), any(), any()))
                .thenReturn(ServiceCommandExecutionResult.notCompleted("deal graph incomplete"));

        assertThatCode(() -> dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row)))
                .doesNotThrowAnyException();

        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.FAILED);
    }

    /**
     * У дочистки анкера нет вовсе — значит нет и бюджета отказов: учёт
     * молча пропускается, а не падает на пустой строке.
     */
    @Test
    void anchorlessCommandHasNoFailureBudget() {
        when(executor.execute(any(), any(), any()))
                .thenThrow(new ExchangeIntegrationException("gateway timeout"));

        assertThatCode(() -> dispatcher.execute(ServiceCommand.builder()
                .type(ServiceCommandType.SUBMIT_ORDER_COMMAND)
                .dealId(DEAL)
                .build(), context()))
                .doesNotThrowAnyException();

        verify(dataService, never()).save(any());
    }

    /**
     * Анкер берётся из контекста прохода, а не перечитывается из базы:
     * строка, заведённая этим же проходом, обязана быть видна команде,
     * а контекст собран до неё.
     */
    @Test
    void anchorComesFromThePassContextWithoutARead() {
        DealActionState row = strategyRow();
        when(executor.execute(any(), any(), any())).thenReturn(ServiceCommandExecutionResult.ok());

        dispatcher.execute(command(ServiceCommandType.SUBMIT_ORDER_COMMAND), context(row));

        verify(dataService, never()).findById(any());
    }

    private void givenRow(DealActionState row) {
        when(dataService.findById(ROW)).thenReturn(Optional.of(row));
    }

    private static ServiceCommand command(ServiceCommandType type) {
        return ServiceCommand.builder().type(type).dealId(DEAL).dealActionStateId(ROW).build();
    }

    private static DealContext context(DealActionState... rows) {
        return DealContext.builder().actionStates(List.of(rows)).build();
    }

    private static DealActionState strategyRow() {
        DealActionState row = new DealActionState();
        row.setId(ROW);
        row.setDealId(DEAL);
        row.setActionKind(ActionKind.STRATEGY);
        row.setStrategyActionId(11L);
        row.setStatus(DealActionStateStatus.SUBMITTED);
        return row;
    }

    private static ServiceCommandRetryProperties properties(Integer maxAttempts) {
        ServiceCommandRetryPolicy policy = new ServiceCommandRetryPolicy();
        policy.setMaxAttempts(maxAttempts);
        policy.setInitialDelay(Duration.ofSeconds(5));
        policy.setMaxDelay(Duration.ofMinutes(2));
        policy.setBackoff(RetryBackoffType.EXPONENTIAL);
        ServiceCommandRetryProperties properties = new ServiceCommandRetryProperties();
        properties.setDefaultPolicy(policy);
        return properties;
    }
}
