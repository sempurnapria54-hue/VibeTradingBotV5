package com.example.platform.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Охрана сборки декодера — клетки `U4.12`, `U4.13` документа
 * `.claude/tests/cases/platform-shared-logic.md`.
 *
 * <p><b>Декодер собран здесь лямбдой, а не подменён:</b> он и есть вход —
 * поведение исходного декодера на отказе, — а наблюдается класс того, что
 * охрана выпускает наружу.
 */
class JwtDecoderAssemblyGuardTest {

    private static final String TOKEN = "token-value";

    private final JwtDecoderAssemblyGuard guard = new JwtDecoderAssemblyGuard();

    @Test
    @DisplayName("U4.12 — отказ ленивой сборки становится отказом проверки токена, а не отвержением")
    void u4_12_anAssemblyFailureBecomesATokenCheckFailure() {
        JwtDecoderInitializationException assembly =
                new JwtDecoderInitializationException("Failed to lazily resolve the supplied JwtDecoder instance",
                        new IllegalStateException("discovery unreachable"));
        JwtDecoder decoder = guarded(token -> {
            throw assembly;
        });

        assertThatThrownBy(() -> decoder.decode(TOKEN))
                .isExactlyInstanceOf(JwtException.class)
                .hasCause(assembly);
    }

    @Test
    @DisplayName("U4.13 — отвержение токена и чужие бины охрана не трогает")
    void u4_13_otherFailuresAndBeansPassUntouched() {
        BadJwtException rejection = new BadJwtException("signature mismatch");
        JwtDecoder decoder = guarded(token -> {
            throw rejection;
        });
        Object foreign = new Object();

        assertThatThrownBy(() -> decoder.decode(TOKEN)).isSameAs(rejection);
        assertThat(guard.postProcessAfterInitialization(foreign, "foreign")).isSameAs(foreign);
    }

    private JwtDecoder guarded(JwtDecoder decoder) {
        return (JwtDecoder) guard.postProcessAfterInitialization(decoder, "jwtDecoder");
    }
}
