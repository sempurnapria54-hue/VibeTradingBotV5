package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

/**
 * Хранилище секретов недоступно — клетки {@code B1.7}, {@code B1.13} и
 * половина «хранилище недоступно» клетки {@code B9.3} документа
 * `.claude/tests/cases/connector-okx.md`: класс собран по конфигурации
 * контекста — мёртвому адресу хранилища, — а не по группе.
 *
 * <p><b>Контейнер у кейса СВОЙ по первому основанию: кейс лишает соседей
 * адреса.</b> Остановленный общий контейнер был бы мёртв соседям до конца
 * прогона, а поднятый заново опубликовал бы НОВЫЙ порт хоста, которого их
 * контекст не знает
 * (.claude/decisions/test-contour-design-pass.md §«Кейс, разрушающий
 * субстрат, берёт свой контейнер и свой контекст»).
 *
 * <p><b>С {@code B1.2} клетка разведена ПРИРОДОЙ отказа, а не его
 * текстом:</b> там хранилище ответило и сказало, что ключей не заводили —
 * повтор не поможет; здесь ответа нет вовсе, и повтор поможет, как только
 * хранилище вернётся. Слитые в один класс, они дали бы ядру одну реакцию
 * на две несравнимые причины.
 *
 * <p><b>Адрес снимается с ЖИВОГО контейнера, и лишь затем контейнер
 * останавливается:</b> контекст обязан получить адрес, по которому никто
 * не отвечает, — отсутствующий адрес есть другой вход.
 */
class SecretStoreUnavailableBoxTest extends ConnectorBox {

    private static final String DEAD_ADDRESS = startAndStop();

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        ConnectorSubstrate.register(registry, Map.of("spring.cloud.vault.uri", DEAD_ADDRESS));
    }

    /**
     * Отказ СОЕДИНЕНИЯ с хранилищем — класс хранилища, а не «площадка
     * недостижима»: клиент хранилища бросает тот же транспортный класс, что
     * и клиент площадки, и развести их обязан резолвер ключей.
     */
    @Test
    @DisplayName("B1.7 — хранилище недоступно — это не «ключей нет»")
    void b1_7_anUnavailableStoreIsNotAnAbsenceOfKeys() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());

        Answer answer = get(account("/positions"));

        assertThat(exchange.count()).isEqualTo(0);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isNotEqualTo("CREDENTIALS_UNAVAILABLE");
        assertThat(answer.errorCode()).isEqualTo("SECRET_STORE_UNAVAILABLE");
    }

    /**
     * Пробы развёртывания хранилища не спрашивают: обе группы — состояние
     * самого процесса ({@code application.yaml},
     * {@code management.endpoint.health}). Готовность здесь — несущее
     * следствие клетки выше: коннектор без хранилища ОБСЛУЖИВАЕТ, отвечая
     * своим классом отказа, и снятый с балансировки под заменил бы этот
     * класс отказом транспорта у вызывающего. Живость, заглядывающая в
     * хранилище, гасила бы под по кругу, пока оно запечатано
     * ({@code .claude/skills/local-stand.md}).
     *
     * <p><b>Общий {@code /actuator/health} при этом обязан лечь</b> — это
     * охрана от пустоты клетки: без неё зелёные группы не отличались бы от
     * контекста, который мёртвого хранилища попросту не видит.
     */
    @Test
    @DisplayName("B1.13 — пробы живости и готовности не зависят от хранилища секретов")
    void b1_13_theProbesDoNotDependOnTheSecretStore() {
        Answer overall = getAnonymously("/actuator/health");
        Answer liveness = getAnonymously("/actuator/health/liveness");
        Answer readiness = getAnonymously("/actuator/health/readiness");

        assertThat(overall.status()).as("хранилище мертво, и общее здоровье это видит").isEqualTo(503);
        assertThat(liveness.status()).isEqualTo(200);
        assertThat(liveness.body()).contains("UP");
        assertThat(readiness.status()).isEqualTo(200);
        assertThat(readiness.body()).contains("UP");
    }

    /**
     * Свойств процесса из хранилища коннектор не берёт, и потому его подъём от
     * хранилища не зависит: контекст поднят на адресе, по которому никто не
     * отвечает, а публичному чтению ключи не нужны вовсе. Неподъём уронил бы
     * весь класс, но ожидания «публичное чтение отвечает» не мерил бы никто —
     * эту половину клетки держит метод, а не подъём.
     */
    @Test
    @DisplayName("B9.3 — контекст поднимается при недоступном хранилище, и публичное чтение отвечает")
    void b9_3_aPublicReadAnswersWhileTheSecretStoreIsDead() {
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, Okx.ok(Okx.instrument(INSTRUMENT).text()));

        Answer answer = get(market("/instruments?externalInstrumentType=SWAP"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(1);
        assertThat(exchange.requests(OkxConstants.INSTRUMENTS_PATH)).hasSize(1);
    }

    private static String startAndStop() {
        VaultContainer<?> container = new VaultContainer<>(
                DockerImageName.parse(ConnectorSubstrate.VAULT_IMAGE))
                .withVaultToken(ConnectorSubstrate.ROOT_TOKEN);
        container.start();
        String address = container.getHttpHostAddress();
        container.stop();
        return address;
    }
}
