package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.domain.service.ExchangeAccountKeyPath;
import org.junit.jupiter.api.Test;

/**
 * Путь ключей счёта в Vault: окружение — ПЕРВЫЙ сегмент.
 *
 * <p>Тест охраняет не форму строки, а границу: политика Vault адресует
 * префикс, и запрет «`dev` не читает секреты `prod`» записывается одной
 * строкой только тогда, когда окружение стои́т первым
 * (docs/architecture/platform.md §Безопасность).
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
