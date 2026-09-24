package com.example.strategies;

import static com.example.platform.util.Constants.Audit.SYSTEM_PRINCIPAL;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.security.ActorProvider;
import com.example.strategies.domain.service.StrategyLifecycleService;
import com.example.strategies.domain.service.StrategyStatusWriter;
import com.example.strategies.domain.service.TenantRiskAppetiteReader;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.example.strategies.integration.internal.api.TradingCoreReadClient;
import com.example.strategies.integration.internal.api.model.PairCheckCoreResponse;
import com.example.strategies.integration.internal.api.model.RiskAppetiteCoreResponse;
import com.example.strategies.integration.internal.event.StrategyEventWriter;
import com.example.strategies.mapping.StrategyApiMapper;
import com.example.strategies.mapping.StrategyApiMapperImpl;
import com.example.strategies.mapping.StrategyEventMessageMapper;
import com.example.strategies.mapping.StrategyEventMessageMapperImpl;
import com.example.strategies.persistence.model.OutboxEntity;
import com.example.strategies.persistence.model.StrategyEntity;
import com.example.strategies.persistence.service.OutboxDataService;
import com.example.strategies.persistence.service.StrategyDataService;
import com.example.strategies.util.Constants;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.message.EventEnvelopeMessage;
import com.example.tradingbot.message.StrategyActivatedMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Маппер domain → message: формы событий строит граница, а не домен. */
    private static final StrategyEventMessageMapper EVENT_MESSAGES =
            new StrategyEventMessageMapperImpl();

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

    /**
     * Имя числового ключа базы у узла дерева определения
     * (docs/architecture/data-ownership.md §Идентификаторы).
     */
    private static final String OWNER_KEY = "id";

    /**
     * Значений ключа в дереве фикстуры: корень, три объявления, деталь,
     * транш, два шага, три действия.
     */
    private static final Integer KEYED_VALUES = 11;

    private final StrategyDataService dataService = mock(StrategyDataService.class);
    private final TradingCoreReadClient coreClient = mock(TradingCoreReadClient.class);
    private final OutboxDataService outboxDataService = mock(OutboxDataService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final StrategyApiMapper mapper = new StrategyApiMapperImpl();
    private final StrategyDefinitionValidator validator = new StrategyDefinitionValidator();
    private final ActorProvider actorProvider = new ActorProvider();
    private final StrategyEventWriter eventWriter =
            new StrategyEventWriter(outboxDataService, objectMapper, EVENT_MESSAGES);
    private final StrategyStatusWriter statusWriter = new StrategyStatusWriter(dataService, eventWriter);
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

    /**
     * Снимок активации едет без числовых ключей базы владельца — ни у
     * одного вида узла — и разбирается обратно формой провода.
     *
     * <p><b>Дерево собрано С ключами на каждом виде узла</b>, и первая
     * проверка это предъявляет: фикстура без ключей прошла бы и на снимке,
     * который их везёт, — ровно так дефект и держался незамеченным у
     * потребителя. Путь — писатель факта, маппер и живой сериализатор:
     * проверки активации к предмету не относятся.
     *
     * <p><b>Обратно снимок читается так, как его читает потребитель:</b>
     * маппер Boot неизвестных полей не отвергает, а доменное дерево везёт и
     * предикаты — полями без сеттера.
     */
    @Test
    @DisplayName("Снимок активации не несёт ключей базы владельца и читается формой провода")
    void theActivationSnapshotCarriesNoOwnerDatabaseKeys() throws Exception {
        Strategy definition = keyedDefinition();
        when(outboxDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        eventWriter.record(definition, StrategyEventType.STRATEGY_ACTIVATED, PRESENTED_PRINCIPAL);

        assertThat(ownerKeyPaths(objectMapper.valueToTree(definition), "definition"))
                .as("фикстура несёт ключ на каждом виде узла — иначе проба ниже прошла бы вхолостую")
                .hasSize(KEYED_VALUES);
        assertThat(ownerKeyPaths(contentOfWrittenRow().path("definition"), "definition"))
                .as("числовой ключ базы границу сервиса не пересекает ни у одного узла")
                .isEmpty();

        StrategyActivatedMessage read = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(writtenRow().getPayload(), StrategyActivatedMessage.class);
        StrategyDetail detail = read.definition().getDetails().get(0);
        List<StrategyAction> trancheActions = detail.getTranches().get(0)
                .getStepsByStatus().get(DealTranche.Status.MANAGING).get(0).getActions();
        assertThat(trancheActions)
                .as("узел опознаётся положением в дереве: состав и порядок доезжают без ключей")
                .extracting(StrategyAction::getKey)
                .containsExactly("entry", "sl");
        assertThat(detail.getStepsByStatus().get(Deal.Status.EXIT_PENDING).get(0).getActions())
                .extracting(StrategyAction::getKey)
                .containsExactly("exit-all");
        assertThat(read.definition().getInstrumentInternalId())
                .as("внешняя ссылка едет идентичностью, а не ключом")
                .isEqualTo(INSTRUMENT);
        assertThat(definition.getId())
                .as("едет копия: дерево вызывающего остаётся с ключами")
                .isEqualTo(101L);
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
                .isEqualTo(SYSTEM_PRINCIPAL);
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
        List<String> declared = Arrays.stream(EventEnvelopeMessage.class.getDeclaredFields())
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

    /** Пути полей-ключей базы, несущих значение, по всему документу. */
    private List<String> ownerKeyPaths(JsonNode node, String path) {
        List<String> found = new ArrayList<>();
        for (int index = 0; node.isArray() && index < node.size(); index++) {
            found.addAll(ownerKeyPaths(node.get(index), path + "[" + index + "]"));
        }
        node.properties().forEach(field -> {
            String fieldPath = path + "." + field.getKey();
            if (Objects.equals(OWNER_KEY, field.getKey()) && isFalse(field.getValue().isNull())) {
                found.add(fieldPath);
            }
            found.addAll(ownerKeyPaths(field.getValue(), fieldPath));
        });
        return found;
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
     * Определение с ключом базы владельца на каждом виде узла: корень, три
     * объявления, деталь, транш, шаги обоих уровней и
     * действия трёх видов. Значения ключей — как их выдала бы база
     * владельца.
     */
    private Strategy keyedDefinition() {
        StrategyOrderAction entry = new StrategyOrderAction();
        entry.setId(301L);
        entry.setKey("entry");
        StrategyAlgoOrderAction stop = new StrategyAlgoOrderAction();
        stop.setId(302L);
        stop.setKey("sl");
        StrategyPositionAction exit = new StrategyPositionAction();
        exit.setId(303L);
        exit.setKey("exit-all");

        StrategyTranche tranche = new StrategyTranche();
        tranche.setId(151L);
        tranche.setKey("main");
        tranche.setStepsByStatus(Map.of(DealTranche.Status.MANAGING,
                List.of(keyedStep(201L, StrategyStepType.ENTRY, List.of(entry, stop)))));

        StrategyDetail detail = new StrategyDetail();
        detail.setId(111L);
        detail.setMarketPhaseType(MarketPhase.Type.BULL_TREND);
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.FOLLOW_PHASE);
        detail.setTranches(List.of(tranche));
        detail.setStepsByStatus(Map.of(Deal.Status.EXIT_PENDING,
                List.of(keyedStep(202L, StrategyStepType.EXIT, List.of(exit)))));

        StrategyMarketPhaseSetting phaseSetting = new StrategyMarketPhaseSetting();
        phaseSetting.setId(121L);
        StrategyIndicatorSetting indicator = new StrategyIndicatorSetting();
        indicator.setId(131L);
        indicator.setKey("atr");
        StrategyMarketStructureSetting structure = new StrategyMarketStructureSetting();
        structure.setId(141L);
        structure.setKey("range");

        Strategy definition = definition(Strategy.Status.ACTIVE);
        definition.setId(101L);
        definition.setMarketPhaseSetting(phaseSetting);
        definition.setIndicatorSettings(List.of(indicator));
        definition.setMarketStructureSettings(List.of(structure));
        definition.setDetails(List.of(detail));
        return definition;
    }

    private StrategyStep keyedStep(Long id, StrategyStepType type, List<StrategyAction> actions) {
        StrategyStep step = new StrategyStep();
        step.setId(id);
        step.setStepType(type);
        step.setActions(actions);
        return step;
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
