package com.example.bff.box;

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
import java.util.List;

/**
 * Стаб провайдера идентичности: диспетчер OIDC, JWKS и подпись входящего
 * токена (.claude/decisions/test-contour-design-pass.md, решение 4).
 *
 * <p><b>Сторона у него ОДНА, и это отличие от соседних ящиков.</b>
 * Исходящей служебной идентичности у периметра нет вовсе: к владельцам он
 * ходит под токеном ПОЛЬЗОВАТЕЛЯ (docs/architecture/contracts.md
 * §«Контекст тенанта в вызове»). Точка выдачи у стаба тем не менее
 * заведена — и заведена РАДИ ОТРИЦАНИЯ: клетка {@code B1.2} утверждает,
 * что периметр её не зовёт ни разу, а не поднять её значило бы
 * утверждать об отсутствии того, чего не с чем сверить.
 *
 * <p><b>Субъект подаётся кейсом, а не зашит в стабе.</b> Ключ кэша
 * членств есть субъект токена (docs/architecture/contracts.md §«Состояния
 * периметр не держит»), и клетки группы {@code B1} ходят двумя разными
 * субъектами и двумя разными токенами ОДНОГО субъекта — без подачи
 * субъекта такой вход не выразим.
 *
 * <p><b>Контейнера Keycloak здесь нет намеренно:</b> проверяется ПРОВЕРКА
 * токена, а не его выпуск, и минуты подъёма чужого процесса ради выпуска
 * не платятся.
 *
 * <p><b>Своя копия, а не общий носитель, и условие переезда названо.</b>
 * Критерий общего артефакта проб — ТРЕТИЙ носитель, и он записан у самого
 * артефакта ({@code services/common/test-support/pom.xml}, шапка); задача
 * переезда живёт в .claude/work/backlog.md §«Стаб провайдера идентичности
 * у ящиков живёт копией на дерево».
 */
final class IdentityStub {

    /** Субъект, которым ходит большинство клеток. */
    static final String SUBJECT = "S1";

    /** Второй субъект: им наблюдается, что ключ кэша — субъект. */
    static final String SECOND_SUBJECT = "S2";

    /** Идентификатор ключа, который стаб публикует в JWKS. */
    private static final String PUBLISHED_KEY_ID = "K1";

    /**
     * Идентификатор ключа, которого в JWKS нет: им подписан токен
     * негодной оси подписи.
     */
    private static final String UNPUBLISHED_KEY_ID = "K2";

    private static final String JWKS_PATH = "/jwks";

    /** Документ диспетчера провайдера: по нему сервис находит набор ключей. */
    private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";

    private static final String TOKEN_PATH = "/token";

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

    /**
     * Точки, за которыми сервис вправе ходить к провайдеру: документ
     * диспетчера и набор ключей — добыча ключей, а не проверка токена.
     */
    static List<String> keyRetrievalPaths() {
        return List.of(DISCOVERY_PATH, JWKS_PATH);
    }

    /** Точка выдачи служебной идентичности: заведена ради отрицания. */
    static String tokenPath() {
        return TOKEN_PATH;
    }

    /**
     * Токен штатного субъекта, корректный по всем осям: подписан
     * публикуемым ключом, выдан этим издателем, живой.
     */
    String token() {
        return tokenFor(SUBJECT);
    }

    /** Такой же токен названного субъекта. */
    String tokenFor(String subject) {
        return sign(PUBLISHED_KEY_ID, issuer(), subject, Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    /**
     * ВТОРОЙ токен того же субъекта — не тот же самый.
     *
     * <p>Им клетка о ключе кэша предъявляет, что записью распоряжается
     * субъект, а не значение токена: одинаковый токен доказал бы лишь
     * совпадение строк.
     */
    String anotherTokenFor(String subject) {
        return sign(PUBLISHED_KEY_ID, issuer(), subject, Instant.now().plus(11, ChronoUnit.MINUTES));
    }

    /** Токен, подписанный ключом, которого в JWKS нет. */
    String foreignKeyToken() {
        return sign(UNPUBLISHED_KEY_ID, issuer(), SUBJECT, Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    /** Токен, срок которого истёк. */
    String expiredToken() {
        return sign(PUBLISHED_KEY_ID, issuer(), SUBJECT, Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    /** Токен чужого издателя: подпись наша, издатель — не тот. */
    String foreignIssuerToken() {
        return sign(PUBLISHED_KEY_ID, "http://localhost:1/other", SUBJECT,
                Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    /**
     * Пути всех обращений, полученных стабом.
     *
     * <p>Ими наблюдается, что за подтверждением токена сервис ходил
     * только за КЛЮЧАМИ: подпись проверяется локально, и точки
     * подтверждения у провайдера сервис не зовёт вовсе.
     */
    List<String> paths() {
        return server.getAllServeEvents().stream()
                .map(event -> event.getRequest().getUrl().split("\\?")[0])
                .toList();
    }

    /**
     * Подписывает токен названными осями.
     *
     * <p><b>Подписывает стаб, а не постпроцессор spring-security-test:</b>
     * постпроцессор обходит проверку подписи, то есть ровно то, что
     * поверхность обязана делать.
     *
     * @param keyId     идентификатор ключа подписи
     * @param issuer    издатель, которым токен себя называет
     * @param subject   субъект, за которого токен отвечает
     * @param expiresAt момент истечения токена
     */
    private String sign(String keyId, String issuer, String subject, Instant expiresAt) {
        RSAKey signing = UNPUBLISHED_KEY_ID.equals(keyId) ? unpublishedKey : publishedKey;
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .issueTime(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), claims);
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
                  "access_token": "box-service-access-token-7f21",
                  "token_type": "Bearer",
                  "expires_in": 600
                }
                """;
        server.stubFor(WireMock.post(WireMock.urlEqualTo(TOKEN_PATH)).willReturn(WireMock.okJson(issued)));
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
        server.stubFor(WireMock.get(WireMock.urlEqualTo(DISCOVERY_PATH))
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
