package com.example.tradingbot.domain.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Путь ключей счёта в Vault: окружение — ПЕРВЫЙ сегмент.
 *
 * <p>Тест охраняет не форму строки, а границу: политика Vault адресует
 * префикс, и запрет «`dev` не читает секреты `prod`» записывается одной
 * строкой только тогда, когда окружение стои́т первым
 * (docs/architecture/platform.md §Безопасность).
 *
 * <p><b>Проба живёт у формы, а не у потребителя.</b> Прежде она стоя́ла в
 * тестовом дереве {@code auth} — у первого потребителя пути, — и там
 * снималась бы вместе с чужим деревом, хотя правится форма здесь
 * (.claude/tests/cases/domain-model-predicates.md §«Существующий набор
 * предмета»).
 */
class ExchangeAccountKeyPathTest {

    @Test
    void environmentIsTheFirstSegment() {
        String path = ExchangeAccountKeyPath.of("prod", "acc-1");

        assertThat(path).isEqualTo("prod/exchange-accounts/acc-1");
        assertThat(path).startsWith("prod/");
    }

    @Test
    void differentEnvironmentsNeverSharePrefix() {
        String dev = ExchangeAccountKeyPath.of("dev", "acc-1");
        String prod = ExchangeAccountKeyPath.of("prod", "acc-1");

        assertThat(dev).isNotEqualTo(prod);
        assertThat(dev.startsWith("prod/")).isFalse();
        assertThat(prod.startsWith("dev/")).isFalse();
    }
}
