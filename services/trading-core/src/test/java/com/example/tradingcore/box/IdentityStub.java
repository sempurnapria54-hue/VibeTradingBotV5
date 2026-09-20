package com.example.tradingcore.box;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEException;
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

/**
 * Стаб провайдера идентичности: диспетчер OIDC, JWKS, подпись входящего
 * токена и ВЫДАЧА исходящей служебной идентичности
 * (.claude/decisions/test-contour-design-pass.md, решение 4).
 *
 * <p><b>Сторон у него ДВЕ, и это ось предмета, а не оснастки.</b> У
 * коннектора провайдер служит одной стороне — проверке входящего токена;
 * ядро ходит к трём соседям под служебной идентичностью кластера, и точка
 * выдачи того же стаба есть вход клеток {@code B12.8} и {@code B12.9}. Токен выдачи — значение, известное до прогона: иначе
 * отрицание «наружу он не выходит» нечем было бы искать.
 *
 * <p><b>Контейнера Keycloak здесь нет намеренно:</b> проверяется ПРОВЕРКА
 * токена, а не его выпуск, и минуты подъёма чужого процесса ради выпуска
 * не платятся.
 *
 * <p><b>Осей у входящего токена три, а не семь, и это следствие
 * предмета.</b> На границе ядра вызывающий — сервис, а не человек:
 * членство не резолвится, пер-операционных проверок права нет намеренно
 * (docs/rules/api-access-policy.md). Поэтому входами остаются ровно те
 * оси, которые проверяет контур ресурс-сервера, — ключ подписи, издатель
 * и срок ({@code B12.2}).
 *
 * <p><b>Ключей два, и второй существует ради одной оси.</b> В JWKS
 * отдаётся только {@code K1}; {@code K2} стаб не публикует никогда, и
 * подписанный им токен есть вход ноги «подпись чужим ключом».
 *
 * <p><b>Своя копия, а не общий носитель, и условие переезда названо.</b>
 * Критерий общего артефакта проб — ТРЕТИЙ носитель, и он записан у самого
 * артефакта ({@code services/common/test-support/pom.xml}, шапка); задача
 * переезда живёт в .claude/work/backlog.md §«Стаб провайдера идентичности
 * у ящиков живёт копией на дерево».
 */
final class IdentityStub {

    /** Идентификатор ключа, который стаб публикует в JWKS. */
    static final String PUBLISHED_KEY_ID = "K1";

    /** Идентификатор ключа, которого в JWKS нет ни одним прогоном. */
    static final String UNPUBLISHED_KEY_ID = "K2";

    /**
     * Значение токена служебной идентичности: известно до прогона, потому
     * что клетка {@code B12.8} ищет его отсутствие, а искать случайную
     * строку нечем.
     */
    static final String SERVICE_ACCESS_TOKEN = "box-service-access-token-4f1c";

    private static final String JWKS_PATH = "/jwks";

    private static final String TOKEN_PATH = "/token";

    private static final String REFUSING_TOKEN_PATH = "/token-refused";

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
        this.server.stubFor(WireMock.get(WireMock.urlEqualTo(JWKS_PATH))
                .willReturn(WireMock.okJson(new JWKSet(publishedKey.toPublicJWK()).toString())));
        stubTokenIssuance();
    }

    static IdentityStub stub() {
        return INSTANCE;
    }

    /** Адрес издателя: он же значение issuer-uri сервиса. */
    String issuer() {
        return "http://localhost:" + server.port();
    }

    /** Точка выдачи служебной идентичности. */
    String tokenUri() {
        return issuer() + TOKEN_PATH;
    }

    /** Точка, отказывающая в выдаче: вход клетки {@code B12.9}. */
    String refusingTokenUri() {
        return issuer() + REFUSING_TOKEN_PATH;
    }

    /**
     * Сколько запросов провайдер получил всего.
     *
     * <p>Им наблюдается, что подпись входящего токена проверяется
     * ЛОКАЛЬНО: за подтверждением сервис к провайдеру не ходит, и число
     * его запросов от числа вызовов поверхности не зависит
     * ({@code B12.2}).
     */
    Integer count() {
        return server.getAllServeEvents().size();
    }

    /**
     * Токен, корректный по всем осям: подписан публикуемым ключом, выдан
     * этим издателем, живой.
     */
    String serviceToken() {
        return sign(PUBLISHED_KEY_ID, issuer(), Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    /** Токен, подписанный ключом, которого в JWKS нет. */
    String foreignKeyToken() {
        return sign(UNPUBLISHED_KEY_ID, issuer(), Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    /** Токен, срок которого истёк. */
    String expiredToken() {
        return sign(PUBLISHED_KEY_ID, issuer(), Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    /** Токен чужого издателя: подпись наша, издатель — не тот. */
    String foreignIssuerToken() {
        return sign(PUBLISHED_KEY_ID, "http://localhost:1/other",
                Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    /**
     * Подписывает токен названными осями.
     *
     * <p><b>Подписывает стаб, а не постпроцессор spring-security-test:</b>
     * постпроцессор обходит проверку подписи, то есть ровно то, что
     * поверхность обязана делать.
     */
    private String sign(String keyId, String issuer, Instant expiresAt) {
        RSAKey signing = UNPUBLISHED_KEY_ID.equals(keyId) ? unpublishedKey : publishedKey;
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("service-account-vibetrading")
                .issueTime(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), claims);
        try {
            jwt.sign(new RSASSASigner((RSAPrivateKey) signing.toPrivateKey()));
        } catch (JOSEException failure) {
            throw new IllegalStateException("Токен не подписался: стаб провайдера идентичности сломан", failure);
        }
        return jwt.serialize();
    }

    private void stubTokenIssuance() {
        String issued = """
                {
                  "access_token": "%s",
                  "token_type": "Bearer",
                  "expires_in": 600
                }
                """.formatted(SERVICE_ACCESS_TOKEN);
        server.stubFor(WireMock.post(WireMock.urlEqualTo(TOKEN_PATH)).willReturn(WireMock.okJson(issued)));
        server.stubFor(WireMock.post(WireMock.urlEqualTo(REFUSING_TOKEN_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_client\"}")));
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
                  "token_endpoint": "%s%s"
                }
                """.formatted(issuer(), issuer(), JWKS_PATH, issuer(), issuer(), TOKEN_PATH);
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
}
