package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

/**
 * Хранилище секретов недоступно — клетка {@code B1.7} документа
 * `.claude/tests/cases/connector-okx.md`.
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
     * Красна долгом {@code F-9}: отказ СОЕДИНЕНИЯ с хранилищем уезжает
     * вызывающему классом «площадка недостижима» — тропа хранилища и тропа
     * площадки обменялись классами. Долг — `.claude/work/backlog.md`
     * §«Классы отказа границы разъехались со своими тропами».
     */
    @Test
    @Tag("debt")
    @DisplayName("B1.7 — хранилище недоступно — это не «ключей нет»")
    void b1_7_anUnavailableStoreIsNotAnAbsenceOfKeys() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());

        Answer answer = get(account("/positions"));

        assertThat(exchange.count()).isEqualTo(0);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isNotEqualTo("CREDENTIALS_UNAVAILABLE");
        assertThat(answer.errorCode()).isEqualTo("SECRET_STORE_UNAVAILABLE");
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
