package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B8} документа кейсов: контур доступа и форма отказа
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Числами пиньнуты ровно те исходы, чьё число фиксирует дом:</b>
 * {@code 401} точки входа контура, {@code 400}, {@code 404} и
 * {@code 405} контейнера, {@code 500} за «всё непредусмотренное», а
 * {@code 200} и {@code 201} — контракт успеха
 * (.claude/tests/cases/strategies.md §«Число ответа и класс отказа —
 * разные ожидания»).
 *
 * <p><b>Форма тела читается по ПОЛЯМ, а не по коду ответа.</b> Клейм
 * «единый error-DTO» держится ровно тогда, когда своё тело получают и те
 * отказы, которых обработчики не называют поимённо: отказ контура, отказ
 * контейнера и всё непредусмотренное. Пустое тело есть второй формат —
 * тот самый, существование которого клейм и отрицает.
 *
 * <p><b>Отрицания идут ПАРОЙ наблюдателей.</b> У отвергнутого вызова не
 * появляется ни строки в базе, ни запроса у соседа: отказ контура стои́т
 * ДО контроллера, и половина «ничего не сделано» без второго наблюдателя
 * не проверяется.
 */
class AccessContourBoxTest extends SharedStrategiesBox {

    /** Точка съёма метрик: читателя у неё нет, и наружу она не опубликована. */
    private static final String METRICS = "/actuator/prometheus";

    /** Описание объявленной поверхности. */
    private static final String API_DOCS = "/v3/api-docs";

    /** Класс отказа контура: принятого принципала нет. */
    private static final String UNAUTHENTICATED = "ACCESS_UNAUTHENTICATED";

    /** Класс отказа, произведённого контейнером. */
    private static final String NOT_ACCEPTED = "REQUEST_NOT_ACCEPTED";

    /** Класс всего непредусмотренного. */
    private static final String INTERNAL_FAILURE = "INTERNAL_FAILURE";

    /** Запись журнала о непредусмотренном отказе поверхности. */
    private static final String UNHANDLED = "Unhandled failure on the strategies surface";

    /** Таблица отказов доступа: в схеме владельца определений её нет. */
    private static final String DENIALS_TABLE = "access_denials";

    @Test
    @DisplayName("B8.1 — Умолчание закрыто: вызов без токена отвергается")
    void b8_1_theDefaultIsClosedAndACallWithoutATokenIsRefused() {
        Answer answer = getAnonymously(STRATEGIES);

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto())
                .as("тело — тот же error-DTO, что у всякой ошибки поверхности, а не пустое")
                .isTrue();
        assertThat(answer.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(answer.header("Set-Cookie"))
                .as("сессия не заводится: поверхность stateless")
                .isNull();
        assertThat(answer.header("WWW-Authenticate"))
                .as("схема предъявления названа: контур принимает bearer-токен")
                .isEqualTo("Bearer");
    }

    @Test
    @DisplayName("B8.2 — Токен, подписанный чужим ключом")
    void b8_2_aTokenSignedWithAForeignKeyIsRefused() {
        Answer answer = getWith(STRATEGIES, identity.foreignKeyToken());

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(peer.count()).as("к стабу ядра запросов нет").isZero();
        assertThat(rows.count(STRATEGIES_TABLE)).as("строк в базе не появляется").isZero();
        assertThat(identity.paths())
                .as("за подтверждением сервис ходил только к точкам ключей: подпись проверена локально")
                .allMatch(path -> path.contains("jwks") || path.contains("openid-configuration")
                        || path.contains("token"));
    }

    @Test
    @DisplayName("B8.3 — Просроченный токен и токен чужого издателя")
    void b8_3_anExpiredTokenAndATokenOfAForeignIssuerAreBothRefused() {
        Answer expired = getWith(STRATEGIES, identity.expiredToken());
        Answer foreignIssuer = getWith(STRATEGIES, identity.foreignIssuerToken());

        assertThat(expired.status()).isEqualTo(401);
        assertThat(foreignIssuer.status()).isEqualTo(401);
        assertThat(expired.carriesErrorDto()).isTrue();
        assertThat(foreignIssuer.carriesErrorDto()).isTrue();
        assertThat(peer.count()).as("исходящих вызовов ни в одном случае").isZero();
        assertThat(rows.count(STRATEGIES_TABLE)).as("и ни одной строки").isZero();
    }

    @Test
    @DisplayName("B8.4 — Проба живости открыта, и это единственное исключение")
    void b8_4_theLivenessProbeIsTheOnlyOpenPoint() {
        peerResolvesEverything();
        String internalId = given(TENANT);

        Answer answer = getAnonymously(HEALTH);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject())
                .as("тело несёт состояние процесса, а не данные сервиса")
                .containsKey("status");
        assertThat(answer.body())
                .as("ни одного определения в ответе")
                .doesNotContain(internalId);
    }

    @Test
    @DisplayName("B8.5 — Съём метрик и описание поверхности не открыты")
    void b8_5_neitherMetricsNorTheSurfaceDescriptionAreOpen() {
        Answer metrics = getAnonymously(METRICS);
        Answer docs = getAnonymously(API_DOCS);

        assertThat(metrics.status())
                .as("контур отвечает ДО диспетчеризации актуатора: неопубликованность точки наружу не видна")
                .isEqualTo(401);
        assertThat(docs.status()).isEqualTo(401);
        assertThat(metrics.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(docs.errorCode()).isEqualTo(UNAUTHENTICATED);
        assertThat(metrics.body()).as("содержимого ни один не отдаёт").doesNotContain("jvm_");
        assertThat(docs.body()).doesNotContain(STRATEGIES);
    }

    @Test
    @DisplayName("B8.6 — Неизвестный путь и неподдержанный метод отвечают тем же телом")
    void b8_6_anUnknownPathAndAnUnsupportedMethodCarryTheSameBody() {
        peerResolvesEverything();
        String internalId = given(TENANT);
        Long written = rows.count(STRATEGIES_TABLE);

        Answer wrongMethod = get(STRATEGIES + "/" + internalId + "/status", TENANT);
        Answer unknownPath = get(STRATEGIES + "/" + internalId + "/whatever", TENANT);

        assertThat(wrongMethod.status()).isEqualTo(405);
        assertThat(unknownPath.status()).isEqualTo(404);
        assertThat(wrongMethod.carriesErrorDto())
                .as("статус наследуется от контейнера, подменяется только тело")
                .isTrue();
        assertThat(unknownPath.carriesErrorDto()).isTrue();
        assertThat(wrongMethod.errorCode()).isEqualTo(NOT_ACCEPTED);
        assertThat(unknownPath.errorCode()).isEqualTo(NOT_ACCEPTED);
        assertThat(wrongMethod.errorMessage()).isNotBlank();
        assertThat(unknownPath.errorMessage()).isNotBlank();
        assertThat(rows.count(STRATEGIES_TABLE)).isEqualTo(written);
    }

    @Test
    @DisplayName("B8.7 — Неразбираемое тело отвергается тем же контрактом")
    void b8_7_anUnparseableBodyIsRefusedByTheSameContract() {
        peerResolvesEverything();
        peer.forgetRequests();

        Answer answer = post(STRATEGIES, TENANT, "не документ вовсе");

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(NOT_ACCEPTED);
        assertThat(answer.errorMessage()).as("причина отказа названа, а не опущена").isNotBlank();
        assertThat(rows.count(STRATEGIES_TABLE)).as("строки определения нет").isZero();
        assertThat(peer.count()).as("к ядру запросов нет: разбор падает раньше охраны").isZero();
    }

    /**
     * Отказ обязан быть НЕПРЕДУСМОТРЕННЫМ, и мимо названных классов его
     * ведёт увод таблицы из-под имени отображения ({@link Rows#hide}):
     * всякий названный класс отвечал бы своим кодом, и клетка о «всём
     * непредусмотренном» на нём предмета не имела бы.
     */
    @Test
    @DisplayName("B8.8 — Непредусмотренный отказ не рассказывает о внутреннем устройстве")
    void b8_8_anUnexpectedFailureTellsNothingAboutTheInternals() {
        Integer logMark = AppLog.mark();
        rows.hide(STRATEGIES_TABLE);

        Answer answer;
        try {
            answer = get(STRATEGIES, TENANT);
        } finally {
            rows.show(STRATEGIES_TABLE);
        }

        assertThat(answer.status()).isEqualTo(500);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo(INTERNAL_FAILURE);
        assertThat(answer.body())
                .as("текст исключения наружу не идёт: он рассказывает о внутреннем устройстве")
                .doesNotContain("does not exist")
                .doesNotContain("SQL")
                .doesNotContain("Exception");
        assertThat(AppLog.since(logMark))
                .as("полный текст ушёл в лог — там его читает держатель")
                .contains(UNHANDLED);
    }

    /**
     * Часть о СТРОКЕ сегодня не прогоняется: таблицы отказов доступа в
     * схеме владельца определений нет вовсе
     * (.claude/work/backlog.md §«Таблица отказов доступа у сервисов со
     * своей базой»), и её появление — исход находки, а не ожидание этой
     * клетки. Прогоняемое здесь — что отказ состоялся своим контрактом и
     * что ни один наблюдатель следа сверх него не увидел.
     */
    @Test
    @DisplayName("B8.9 — След отказа доступа")
    void b8_9_theAccessDenialLeavesItsTrace() {
        List<String> tables = rows.tableNames();

        Answer answer = getAnonymously(STRATEGIES);

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode())
                .as("актором хода идёт класс контура: предъявителя контур не удостоверил")
                .isEqualTo(UNAUTHENTICATED);
        assertThat(tables)
                .as("писателя следа у сервиса нет: таблицы отказов доступа в схеме не заведено")
                .doesNotContain(DENIALS_TABLE);
        assertThat(rows.countsByTable().values())
                .as("и ни одной строки отказ за собой не оставил ни в одной таблице")
                .allMatch(count -> count == 0L);
    }
}
