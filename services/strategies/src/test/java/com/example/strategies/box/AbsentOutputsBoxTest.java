package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B10} документа кейсов: отсутствие выходов
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Клейм отсутствия читается по ОБЪЯВЛЕННОЙ поверхности и по
 * состоянию брокера, а не по тому, что кейс не догадался позвать.</b>
 * Группа потребителя есть состояние НА БРОКЕРЕ, и сервис, подписавшийся
 * хоть на одну тему, её заводит; схему печатает сама база; исходящие
 * адреса считают стабы.
 *
 * <p><b>Названное ограничение наблюдателя:</b> адресов, на которые
 * владелец определений вправе ходить, ровно два — сосед по ярусу и точки
 * провайдера идентичности; всё, что ушло бы мимо них, стабами не
 * ловится. Что исходящих адресов у процесса ровно столько, читается
 * конфигурацией, а не прогоном.
 */
class AbsentOutputsBoxTest extends SharedStrategiesBox {

    /** Тема, в которую публикует ядро: её записи владелец определений не читает. */
    private static final String CORE_TOPIC = "trading-core.facts";

    /** Имя сервиса: им называлась бы его группа потребителя, будь она у него. */
    private static final String SERVICE_NAME = "strategies";

    /** Стаб второго соседа яруса: он поднят ради отрицания и обязан остаться пустым. */
    private final PeerStub marketData = PeerStub.marketData();

    /**
     * Поля, которые снимок несёт СВЕРХ ответа поверхности, — метаданные
     * персистентности ВЛАДЕЛЬЦА: технический ключ строки и колонки
     * аудита каждого узла (находки F-5 и F-7 того же документа).
     *
     * <p><b>Биржевых моментов аудита в перечне нет намеренно:</b> у
     * определения они пусты, а пустое поле снимка перепись не считает
     * вовсе — объявленное здесь, оно было бы мёртвой маской. Появись
     * они, клетка их и предъявит.
     */
    private static final Set<String> OWNER_METADATA = Set.of("id", "createdAt", "createdBy",
            "modifiedAt", "modifiedBy");

    /**
     * Предикаты rich-модели, уехавшие на провод полями: их в форме
     * сообщения не объявлено ни одного, а сериализатор вывел их из
     * методов (находка F-8 того же документа).
     *
     * <p><b>Пятый предикат добыт этой клеткой:</b> {@code entryDeclaration}
     * — метод {@code StrategyTranche#isEntryDeclaration()}, и находка
     * F-8 называла четыре из пяти. Перечень здесь закрытый ровно затем,
     * чтобы шестой предъявился падением, а не растворился в исключении.
     */
    private static final Set<String> RICH_PREDICATES = Set.of("active", "deleted", "entryStep",
            "protective", "entryDeclaration");

    /**
     * Поля домена, которых ответ поверхности не несёт, и это не изъян:
     * радиус вызова приезжает заголовком контекста, а снимок едет
     * читателю, у которого заголовка нет.
     */
    private static final Set<String> SURFACE_OMITS = Set.of("tenantId");

    @Test
    @DisplayName("B10.1 — Сервис ничего не потребляет")
    void b10_1_theServiceConsumesNothing() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        Long written = rows.countsByTable().values().stream().reduce(0L, Long::sum);
        peer.forgetRequests();

        Wire.put(CORE_TOPIC, TENANT, "{\"strategyInternalId\": \"" + internalId + "\"}");
        Wire.put(StrategiesSubstrate.FACTS_TOPIC, TENANT,
                "{\"strategyInternalId\": \"" + internalId + "\"}");
        relayPass();

        assertThat(Wire.consumerGroups())
                .as("группы потребителя с именем сервиса у брокера нет: клиента-потребителя у него нет")
                .noneMatch(group -> group.contains(SERVICE_NAME));
        assertThat(rows.countsByTable().values().stream().reduce(0L, Long::sum))
                .as("на положенные записи сервис не отреагировал ни строкой")
                .isEqualTo(written);
        assertThat(peer.count())
                .as("и ни одним исходящим вызовом")
                .isZero();
        assertThat(statusOf(internalId, TENANT))
                .as("собственную тему он тоже не читает: статус свой он двигает только поверхностью")
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("B10.2 — Соседей, кроме ядра, сервис не зовёт")
    void b10_2_theServiceCallsNoNeighbourButTheCore() {
        marketData.reset();
        peerResolvesEverything();
        String internalId = given(TENANT);

        get(STRATEGIES, TENANT);
        get(STRATEGIES + "/" + internalId, TENANT);
        assertThat(moveTo(internalId, TENANT, "ACTIVE").status()).isEqualTo(200);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);
        assertThat(moveTo(internalId, TENANT, "DELETED").status()).isEqualTo(200);
        relayPass();

        assertThat(distinct(peer.paths()))
                .as("к ядру ушли ровно два пути: проверка пары и числа риск-аппетита")
                .containsExactlyInAnyOrder(PEER_PAIR_CHECKS, PEER_RISK_APPETITES + "/" + TENANT);
        assertThat(marketData.count())
                .as("стаб владельца рыночных данных не получил ни одного: история приезжает фазой 4")
                .isZero();
    }

    @Test
    @DisplayName("B10.3 — Сервис не ходит в чужие базы")
    void b10_3_theServiceNeverReachesIntoForeignDatabases() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);

        assertThat(get(STRATEGIES, TENANT).status())
                .as("все тропы проходят при единственной доступной базе")
                .isEqualTo(200);
        assertThat(get(STRATEGIES + "/" + internalId, TENANT).status()).isEqualTo(200);
        assertThat(relayPass().status()).isEqualTo(202);

        assertThat(rows.tableNames())
                .as("проекций чужих реестров у себя сервис не держит")
                .doesNotContain("tenant_risk_appetites", "exchange_accounts", "instruments",
                        "tenants", "memberships");
        assertThat(rows.columnNames(STRATEGIES_TABLE))
                .as("и чисел риск-аппетита у себя не хранит: копия ошибалась бы в разрешающую сторону")
                .noneMatch(column -> column.contains("risk") || column.contains("appetite"));
    }

    /**
     * Ожидание «большее нескольких тактов расписания» здесь заменено
     * прогоном ЕДИНСТВЕННОГО прохода сервиса, и замена названа.
     *
     * <p>Расписание в прогоне глушится выражением такта, до которого он
     * не доживает ({@link StrategiesSubstrate}) — иначе выключатель реле
     * перестал бы быть входом своей клетки. Ждать «несколько тактов»
     * поэтому нечего: их не будет по построению. Утверждение при этом не
     * ослабевает — у сервиса одна джоба, и это реле; прогнав её и не
     * увидев ни одной команды, клетка утверждает ровно то же.
     */
    @Test
    @DisplayName("B10.4 — Торговых решений сервис не принимает")
    void b10_4_theServiceMakesNoTradingDecisions() {
        peerResolvesEverything();
        givenActive(TENANT);
        peer.forgetRequests();

        relayPass();
        relayPass();

        assertThat(peer.count())
                .as("к ядру не уходит ни одной команды: ни заявки, ни сделки, ни отмены")
                .isZero();
        assertThat(rows.tableNames())
                .as("и ни одной строки о сделках, заявках или позициях в базе не заводится")
                .doesNotContain("deals", "deal_tranches", "orders", "algo_orders", "positions");
        assertThat(post(STRATEGIES + "/jobs/entry-scanner", TENANT, "").status())
                .as("иных проходов у сервиса нет: джоба одна, и это реле")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("B10.5 — Грамматики условий и расчётного слоя у сервиса нет")
    void b10_5_theServiceHasNeitherConditionGrammarNorCalculationLayer() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);

        Set<String> snapshot = names(nested(contentOf(event(ACTIVATED)), "definition"));
        Set<String> given = names(get(STRATEGIES + "/" + internalId, TENANT).asObject());

        Set<String> declared = new LinkedHashSet<>(OWNER_METADATA);
        declared.addAll(RICH_PREDICATES);
        declared.addAll(SURFACE_OMITS);
        assertThat(snapshot)
                .as("объявленное исключение обязано встречаться: мёртвая маска прячет ровно то, "
                        + "ради чего заведена")
                .containsAll(declared);

        Set<String> beyond = new LinkedHashSet<>(snapshot);
        beyond.removeAll(given);
        beyond.removeAll(declared);
        assertThat(beyond)
                .as("в содержимом едет дерево как ДАННЫЕ: ни цены, ни размера, ни выбранного действия")
                .isEmpty();
        assertThat(snapshot)
                .as("грамматика условий едет объявлением, а не своим разбором")
                .contains("condition");
    }

    /** Различные пути обращений, в порядке первого появления. */
    private List<String> distinct(List<String> paths) {
        return new LinkedHashSet<>(paths).stream().toList();
    }

    /** Вложенный объект содержимого по имени поля. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> content, String field) {
        return (Map<String, Object>) content.get(field);
    }

    /** Имена всех полей документа на всей его глубине. */
    private static Set<String> names(Object node) {
        Map<String, Object> collected = new LinkedHashMap<>();
        collect(node, collected);
        return collected.keySet();
    }

    private static void collect(Object node, Map<String, Object> collected) {
        if (node instanceof Map<?, ?> object) {
            object.forEach((name, value) -> {
                if (Objects.nonNull(value)) {
                    collected.put(String.valueOf(name), Boolean.TRUE);
                }
                collect(value, collected);
            });
            return;
        }
        if (node instanceof List<?> items) {
            items.forEach(item -> collect(item, collected));
        }
    }
}
