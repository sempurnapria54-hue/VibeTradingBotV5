package com.example.audit.box;

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
 * Стаб провайдера идентичности: диспетчер OIDC, JWKS и подпись входящего
 * токена (.claude/decisions/test-contour-design-pass.md, решение 4).
 *
 * <p><b>Сторона у него ОДНА, и это свойство предмета.</b> Исходящей
 * идентичности у журнала нет вовсе — соседей он не зовёт ни одного
 * (.claude/tests/cases/audit.md §«Чем достаются выходы»), — поэтому точки
 * выдачи служебного токена стаб не держит. Заведи он её, кейс группы
 * {@code B11} об отсутствии исходящих вызовов проверял бы стаб, которого
 * сервис не спрашивает ни при каком входе.
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

    /** Имя принципала, которым ходит поверхность ящика. */
    static final String PRINCIPAL = "service-account-vibetrading";

    /** Идентификатор ключа, который стаб публикует в JWKS. */
    private static final String PUBLISHED_KEY_ID = "K1";

    private static final String JWKS_PATH = "/jwks";

    private static final IdentityStub INSTANCE = new IdentityStub();

    private final WireMockServer server;
    private final RSAKey publishedKey;

    private IdentityStub() {
        this.publishedKey = keyOf(PUBLISHED_KEY_ID);
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        this.server.start();
        stubDiscovery();
        this.server.stubFor(WireMock.get(WireMock.urlEqualTo(JWKS_PATH))
                .willReturn(WireMock.okJson(new JWKSet(publishedKey.toPublicJWK()).toString())));
    }

    static IdentityStub stub() {
        return INSTANCE;
    }

    /** Адрес издателя: он же значение issuer-uri сервиса. */
    String issuer() {
        return "http://localhost:" + server.port();
    }

    /**
     * Токен, корректный по всем осям: подписан публикуемым ключом, выдан
     * этим издателем, живой.
     */
    String serviceToken() {
        return sign(issuer(), Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    /**
     * Подписывает токен названными осями.
     *
     * <p><b>Подписывает стаб, а не постпроцессор spring-security-test:</b>
     * постпроцессор обходит проверку подписи, то есть ровно то, что
     * поверхность обязана делать.
     *
     * <p><b>Ключ один, и негодных осей токена стаб пока не выпускает.</b>
     * Их вход — группа {@code B9}, которой в дереве ещё нет; заведённые
     * заранее, они были бы формами без единого потребителя
     * (.claude/rules/codestyle.md §«Неиспользуемый код»).
     *
     * @param issuer    издатель, которым токен себя называет
     * @param expiresAt момент истечения токена
     */
    private String sign(String issuer, Instant expiresAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(PRINCIPAL)
                .issueTime(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(PUBLISHED_KEY_ID).build(), claims);
        try {
            jwt.sign(new RSASSASigner((RSAPrivateKey) publishedKey.toPrivateKey()));
        } catch (JOSEException failure) {
            throw new IllegalStateException("Токен не подписался: стаб провайдера идентичности сломан",
                    failure);
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
}
