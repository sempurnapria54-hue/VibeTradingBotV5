package com.example.strategies;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategies.domain.event.OutboxWriter;
import com.example.strategies.domain.service.ActorProvider;
import com.example.strategies.domain.service.StrategyLifecycleService;
import com.example.strategies.domain.service.StrategyStatusWriter;
import com.example.strategies.domain.service.TenantRiskAppetiteReader;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.example.strategies.integration.TradingCoreReadClient;
import com.example.strategies.integration.model.PairCheckCoreResponse;
import com.example.strategies.integration.model.RiskAppetiteCoreResponse;
import com.example.strategies.mapping.StrategyApiMapper;
import com.example.strategies.mapping.StrategyApiMapperImpl;
import com.example.strategies.persistence.model.OutboxEntity;
import com.example.strategies.persistence.model.StrategyEntity;
import com.example.strategies.persistence.service.OutboxDataService;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.strategies.util.Constants;
import com.example.tradingbot.domain.event.EventEnvelope;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Состав того, что владелец определений кладёт на провод: конверт колонками
 * строки outbox и содержимое её телом.
 *
 * <p><b>Почему охрана — прогон, а не чтение.</b> Форму содержимого знает
 * только производитель, общего носителя имён у сторон нет намеренно, и цена
 * решения названа: расхождение не ловится ничем, кроме теста у писателя
 * формы (docs/architecture/contracts.md §«Носителя имён в общей библиотеке
 * не заводится, и это решение»). Потерянное поле при этом не роняет ничего —
 * величина выходит пустой, а строка журнала остаётся правдоподобной.
 *
 * <p><b>Идентичности радиуса проверяются на ВЕРХНЕМ уровне, а не внутри
 * снимка.</b> Читатель журнала достаёт из содержимого только одноимённые
 * компоненты верхнего уровня; внутри снимка определение зовётся
 * {@code internalId} и по имени колонки не находится вовсе, а две прочие
 * лежат на глубине, куда он не ходит
 * (docs/models/domain/other/AuditRecord.md).
 *
 * <p><b>Тест собирает НАСТОЯЩИЙ путь</b> — служба жизненного цикла, писатель
 * перехода, писатель outbox и живой сериализатор, — потому что предмет
 * проверки лежит ровно на стыке: содержимое собирается в одном звене, а
 * телом строки становится в другом.
 */
class StrategyEventFormTest {

    private static final String TENANT = "tn-0001";
    private static final String ACCOUNT = "ea-0001";
    private static final String INSTRUMENT = "in-0001";
    private static final String STRATEGY = "st-0001";
    private static final String PRESENTED_PRINCIPAL = "holder";

    /**
     * Полный состав конверта (docs/architecture/contracts.md §«Конверт
     * события»). Перечень написан здесь потому, что он и есть предмет
     * проверки: копия, выведенная из самого класса, совпадала бы с ним
     * всегда.
     */
    private static final List<String> ENVELOPE_FORM =
            List.of("eventId", "eventType", "tenantId", "occurredAt", "version", "traceContext");

    private final StrategyDataService dataService = mock(StrategyDataService.class);
    private final TradingCoreReadClient coreClient = mock(TradingCoreReadClient.class);
    private final OutboxDataService outboxDataService = mock(OutboxDataService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final StrategyApiMapper mapper = new StrategyApiMapperImpl();
    private final StrategyDefinitionValidator validator = new StrategyDefinitionValidator();
    private final ActorProvider actorProvider = new ActorProvider();
    private final OutboxWriter outboxWriter = new OutboxWriter(outboxDataService, objectMapper);
    private final StrategyStatusWriter statusWriter = new StrategyStatusWriter(dataService, outboxWriter);
    private final TenantRiskAppetiteReader appetiteReader = new TenantRiskAppetiteReader(coreClient);

    private final StrategyLifecycleService service = new StrategyLifecycleService(
            dataService, validator, coreClient, mapper, appetiteReader, statusWriter, actorProvider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Активация: три идентичности радиуса и актор стоя́т компонентами
     * верхнего уровня, снимок дерева — рядом с ними.
     */
    @Test
    @DisplayName("Содержимое активации несёт радиус верхним уровнем и снимок рядом")
    void theActivationContentCarriesItsRadiusAtTheTopLevel() throws Exception {
        givenPresentedPrincipal();
        givenDefinition(definition(Strategy.Status.CREATED));
        givenReferencesResolve();
        givenRiskAppetite();

        service.applyStatus(STRATEGY, TENANT, Strategy.Status.ACTIVE);

        JsonNode content = contentOfWrittenRow();
        assertThat(content.path("strategyInternalId").textValue())
                .as("колонка радиуса заполняется по ОДНОИМЁННОМУ компоненту верхнего уровня")
                .isEqualTo(STRATEGY);
        assertThat(content.path("exchangeAccountInternalId").textValue()).isEqualTo(ACCOUNT);
        assertThat(content.path("instrumentInternalId").textValue()).isEqualTo(INSTRUMENT);
        assertThat(content.path("actor").textValue())
                .as("активацию запускает команда пользователя — актор едет содержимым")
                .isEqualTo(PRESENTED_PRINCIPAL);
        assertThat(content.path("definition").path("internalId").textValue())
                .as("снимок дерева едет целиком: ядро дочитать его не может по построению")
                .isEqualTo(STRATEGY);
    }

    /** Деактивация: идентичности и актор, дерева нет — оно у читателя лежит. */
    @Test
    @DisplayName("Содержимое деактивации несёт актора и не несёт дерева")
    void theLifecycleContentCarriesTheActorAndNoTree() throws Exception {
        givenPresentedPrincipal();
        givenDefinition(definition(Strategy.Status.ACTIVE));

        service.applyStatus(STRATEGY, TENANT, Strategy.Status.INACTIVE);

        JsonNode content = contentOfWrittenRow();
        assertThat(content.path("strategyInternalId").textValue()).isEqualTo(STRATEGY);
        assertThat(content.path("exchangeAccountInternalId").textValue()).isEqualTo(ACCOUNT);
        assertThat(content.path("instrumentInternalId").textValue()).isEqualTo(INSTRUMENT);
        assertThat(content.path("actor").textValue())
                .as("остановку определения инициирует человек — актор едет содержимым")
                .isEqualTo(PRESENTED_PRINCIPAL);
        assertThat(content.has("definition"))
                .as("второй раз слать неизменяемое дерево значило бы дублировать лежащее у читателя")
                .isFalse();
    }

    /**
     * Тот же переход собственным проходом: актор — класс контура, а не
     * пустота и не имя.
     */
    @Test
    @DisplayName("Переход без предъявленного принципала несёт класс контура")
    void aTransitionWithoutAPresentedPrincipalCarriesTheContourClass() throws Exception {
        givenDefinition(definition(Strategy.Status.ACTIVE));

        service.applyStatus(STRATEGY, TENANT, Strategy.Status.DELETED);

        assertThat(contentOfWrittenRow().path("actor").textValue())
                .as("пусто в контексте означает «внешнего инициатора нет» — это признак, а не умолчание")
                .isEqualTo(Constants.Audit.SYSTEM_PRINCIPAL);
    }

    /**
     * Строка outbox берёт класс события из конверта.
     *
     * <p>Проба здесь о ПРОИСХОЖДЕНИИ значения: собранный без
     * дискриминатора конверт оставил бы колонку пустой, а потребитель без
     * неё не разбирает содержимое ничем — тело едет одним документом.
     */
    @Test
    @DisplayName("Класс события и версия формы приезжают на строку из конверта")
    void theOutboxRowTakesItsDiscriminatorAndVersionFromTheEnvelope() {
        givenDefinition(definition(Strategy.Status.ACTIVE));

        service.applyStatus(STRATEGY, TENANT, Strategy.Status.INACTIVE);

        OutboxEntity row = writtenRow();
        assertThat(row.getEventType())
                .as("без класса события содержимое не разбирается ничем")
                .isEqualTo(StrategyEventType.STRATEGY_DEACTIVATED.name());
        assertThat(row.getVersion())
                .as("состав обеих форм изменён одним ходом — версия поднимается один раз на весь состав")
                .isEqualTo(2);
        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getTopic()).isEqualTo(Constants.Topic.FACTS);
        assertThat(row.getEventId()).isNotBlank();
    }

    /**
     * Конверт — исполнимая запись объявленной формы, и запись
     * <b>полная</b>: недостающее поле означало бы, что часть состава уехала
     * в транспорт, а общий артефакт перестал быть единственным носителем
     * формы.
     */
    @Test
    @DisplayName("Класс конверта несёт весь объявленный состав и ничего сверх")
    void theEnvelopeClassCarriesItsDeclaredCompositionWhole() {
        List<String> declared = Arrays.stream(EventEnvelope.class.getDeclaredFields())
                .filter(field -> isFalse(Modifier.isStatic(field.getModifiers())))
                .map(Field::getName)
                .toList();

        assertThat(declared)
                .as("состав конверта объявлен домом формы, и второй его редакции не заводится")
                .containsExactlyInAnyOrderElementsOf(ENVELOPE_FORM);
    }

    /** Строка, которую писатель отдал границе персистентности. */
    private OutboxEntity writtenRow() {
        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxDataService).save(captor.capture());
        return captor.getValue();
    }

    /** Тело строки разобранным документом — так его увидит потребитель. */
    private JsonNode contentOfWrittenRow() throws Exception {
        return objectMapper.readTree(writtenRow().getPayload());
    }

    private void givenPresentedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                PRESENTED_PRINCIPAL, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private void givenDefinition(Strategy definition) {
        when(dataService.findByInternalId(STRATEGY)).thenReturn(Optional.of(definition));
        when(dataService.findByInternalIdWithTree(STRATEGY)).thenReturn(Optional.of(definition));
        when(dataService.getRequiredEntityByInternalId(STRATEGY)).thenReturn(new StrategyEntity());
        when(outboxDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenReferencesResolve() {
        when(coreClient.checkPair(TENANT, ACCOUNT, INSTRUMENT))
                .thenReturn(new PairCheckCoreResponse(true, true, true));
    }

    private void givenRiskAppetite() {
        when(coreClient.getRiskAppetite(TENANT)).thenReturn(new RiskAppetiteCoreResponse(
                TENANT, new BigDecimal("2"), new BigDecimal("3")));
    }

    /**
     * Определение с одной НЕТОРГУЕМОЙ деталью: риск-полей у неё нет по
     * построению, и неравенства на ней не считаются. Предмет теста —
     * состав события, а не содержание проверок активации.
     */
    private Strategy definition(Strategy.Status status) {
        StrategyDetail detail = new StrategyDetail();
        detail.setMarketPhaseType(MarketPhase.Type.UNKNOWN);
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.NO_TRADE);
        Strategy definition = new Strategy();
        definition.setInternalId(STRATEGY);
        definition.setTenantId(TENANT);
        definition.setExchangeAccountInternalId(ACCOUNT);
        definition.setInstrumentInternalId(INSTRUMENT);
        definition.setName("baseline");
        definition.setStatus(status);
        definition.setDetails(List.of(detail));
        return definition;
    }
}
