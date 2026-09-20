package com.example.auth.box;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

/**
 * Стаб провайдера идентичности: диспетчер OIDC, JWKS и подпись токена
 * (.claude/decisions/test-contour-design-pass.md, решение 4).
 *
 * <p><b>Контейнера Keycloak здесь нет намеренно:</b> проверяется
 * ПРОВЕРКА токена, а не его выпуск, и минуты подъёма чужого процесса
 * ради выпуска не платятся. Токен подписывает сам стаб, а
 * {@code issuer-uri} сервиса указывает на него.
 *
 * <p><b>Ключей два, и второй существует ради одной оси.</b> В JWKS
 * отдаётся только {@code K1}; {@code K2} стаб не публикует никогда, и
 * подписанный им токен есть вход кейса «подпись чужим ключом»
 * ({@code B1.7}). Разведены они идентификатором ключа ({@code kid}) —
 * тем же способом, каким их разводит производитель токена.
 *
 * <p><b>Отказ добычи ключей — тоже вход, и он обратим.</b>
 * {@link #breakKeySet()} переводит JWKS-точку в отказ, а
 * {@link #healKeySet()} возвращает её; пара нужна кейсу {@code B1.15},
 * который иначе оставил бы стаб сломанным для соседей по прогону — стаб
 * общий на JVM.
 *
 * <p><b>Счётчика обращений к стабу не мерит ни один кейс</b>, и потому
 * разностная форма отрицания сюда не приходит вовсе: все оси токена суть
 * вход, которым управляет тест (`.claude/tests/cases/auth.md` §«Чем
 * достаются выходы»).
 */
final class IdentityStub {

    /** Идентификатор ключа, который стаб публикует в JWKS. */
    static final String PUBLISHED_KEY_ID = "K1";

    /** Идентификатор ключа, которого в JWKS нет ни одним прогоном. */
    static final String UNPUBLISHED_KEY_ID = "K2";

    /** Клиент браузерной тропы: им выдан токен человека. */
    static final String BROWSER_CLIENT_ID = "vibetrading-web";

    /** Служебный клиент кластера: им выдан токен межсервисного вызова. */
    static final String SERVICE_CLIENT_ID = "vibetrading-service";

    private static final String JWKS_PATH = "/jwks";

    private static final IdentityStub INSTANCE = new IdentityStub();

    private final WireMockServer server;
    private final RSAKey publishedKey;
    private final RSAKey unpublishedKey;

    private IdentityStub() {
        this.publishedKey = keyOf(PUBLISHED_KEY_ID);
        this.unpublishedKey = keyOf(UNPUBLISHED_KEY_ID);
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        this.server.start();
        stubDiscovery();
        healKeySet();
    }

    static IdentityStub stub() {
        return INSTANCE;
    }

    /** Адрес издателя: он же значение {@code issuer-uri} сервиса. */
    String issuer() {
        return "http://localhost:" + server.port();
    }

    /**
     * Переводит диспетчер OIDC в отказ: ленивая сборка декодера не
     * удаётся. Отличается от {@link #breakKeySet()} звеном, которое
     * производит отказ, — сборка декодера против добычи ключей.
     */
    void breakDiscovery() {
        server.stubFor(WireMock.get(WireMock.urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(WireMock.aResponse().withStatus(503)));
        server.stubFor(WireMock.get(WireMock.urlEqualTo("/.well-known/oauth-authorization-server"))
                .willReturn(WireMock.aResponse().withStatus(503)));
    }

    /** Возвращает диспетчер OIDC в рабочее состояние. */
    void healDiscovery() {
        stubDiscovery();
    }

    /** Переводит JWKS-точку в отказ: ключи провайдера недостижимы. */
    void breakKeySet() {
        server.stubFor(WireMock.get(WireMock.urlEqualTo(JWKS_PATH))
                .willReturn(WireMock.aResponse().withStatus(503)));
    }

    /** Возвращает JWKS-точку в рабочее состояние. */
    void healKeySet() {
        server.stubFor(WireMock.get(WireMock.urlEqualTo(JWKS_PATH))
                .willReturn(WireMock.okJson(new JWKSet(publishedKey.toPublicJWK()).toString())));
    }

    /**
     * Токен, корректный по всем осям: подписан публикуемым ключом, выдан
     * браузерному клиенту, живой, от этого издателя.
     *
     * @param subject идентификатор пользователя у провайдера
     * @return подписанный токен
     */
    String browserToken(String subject) {
        return token(builder().subject(subject));
    }

    /** Заготовка токена со всеми осями в корректном положении. */
    TokenSpec builder() {
        return new TokenSpec(issuer());
    }

    /**
     * Подписывает токен по заготовке.
     *
     * <p><b>Подписывает стаб, а не постпроцессор spring-security-test:</b>
     * постпроцессор обходит проверку подписи, то есть ровно то, что
     * поверхность обязана делать.
     *
     * @param spec оси токена
     * @return подписанный токен
     */
    String token(TokenSpec spec) {
        RSAKey signing = Objects.equals(UNPUBLISHED_KEY_ID, spec.keyId) ? unpublishedKey : publishedKey;
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(spec.issuer)
                .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(spec.expiresAt));
        if (Objects.nonNull(spec.subject)) {
            claims.subject(spec.subject);
        }
        if (Objects.nonNull(spec.authorizedParty)) {
            claims.claim("azp", spec.authorizedParty);
        }
        if (Objects.nonNull(spec.preferredUsername)) {
            claims.claim("preferred_username", spec.preferredUsername);
        }
        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(spec.keyId);
        if (Objects.nonNull(spec.type)) {
            header.type(new JOSEObjectType(spec.type));
        }
        SignedJWT jwt = new SignedJWT(header.build(), claims.build());
        try {
            jwt.sign(new RSASSASigner((RSAPrivateKey) signing.toPrivateKey()));
        } catch (JOSEException failure) {
            throw new IllegalStateException("Токен не подписался: стаб провайдера идентичности сломан", failure);
        }
        return jwt.serialize();
    }

    private void stubDiscovery() {
        String metadata = """
                {
                  "issuer": "%s",
                  "jwks_uri": "%s%s",
                  "id_token_signing_alg_values_supported": ["RS256"],
                  "subject_types_supported": ["public"],
                  "response_types_supported": ["code"],
                  "authorization_endpoint": "%s/authorize",
                  "token_endpoint": "%s/token"
                }
                """.formatted(issuer(), issuer(), JWKS_PATH, issuer(), issuer());
        server.stubFor(WireMock.get(WireMock.urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(WireMock.okJson(metadata)));
    }

    private static RSAKey keyOf(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey(pair.getPrivate())
                    .keyID(keyId)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("RSA недоступен в этой JVM", failure);
        }
    }

    /**
     * Оси токена, каждая из которых есть ВХОД кейса: ключ подписи,
     * издатель, субъект, клиент выдачи, имя, срок и заголовок типа.
     *
     * <p>Заготовка выдаётся со всеми осями в корректном положении, и кейс
     * сдвигает ровно одну — иначе красный прогон не назвал бы, какая ось
     * его произвела.
     */
    static final class TokenSpec {

        private String keyId = PUBLISHED_KEY_ID;
        private String issuer;
        private String subject = "user-0";
        private String authorizedParty = BROWSER_CLIENT_ID;
        private String preferredUsername;
        private Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        private String type;

        private TokenSpec(String issuer) {
            this.issuer = issuer;
        }

        TokenSpec keyId(String value) {
            this.keyId = value;
            return this;
        }

        TokenSpec issuer(String value) {
            this.issuer = value;
            return this;
        }

        TokenSpec subject(String value) {
            this.subject = value;
            return this;
        }

        TokenSpec noSubject() {
            this.subject = null;
            return this;
        }

        TokenSpec authorizedParty(String value) {
            this.authorizedParty = value;
            return this;
        }

        TokenSpec noAuthorizedParty() {
            this.authorizedParty = null;
            return this;
        }

        TokenSpec preferredUsername(String value) {
            this.preferredUsername = value;
            return this;
        }

        TokenSpec expiresAt(Instant value) {
            this.expiresAt = value;
            return this;
        }

        TokenSpec type(String value) {
            this.type = value;
            return this;
        }
    }
}
