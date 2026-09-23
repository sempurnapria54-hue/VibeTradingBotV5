package com.example.tests.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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
import java.util.UUID;

import static java.util.Objects.nonNull;

/**
 * Стаб провайдера идентичности тропы: диспетчер OIDC, JWKS и выдача
 * токена службам (.claude/decisions/test-contour-design-pass.md, решение 4).
 *
 * <p><b>Выданный службе токен — настоящий подписанный JWT, а не метка.</b>
 * У ящика исходящий токен уходит в стаб соседа и не проверяется никем; на
 * тропе он приходит на поверхность ДРУГОЙ СТОРОНЫ, и её контур доступа
 * проверяет подпись локально. Метка вместо токена отвергалась бы там, и
 * стык не прошёл бы ни разу.
 *
 * <p><b>Выданный службе токен отличим от токена теста:</b> кейс, требующий
 * «вызов пришёл с токеном {@code client_credentials}, выданным стабом»,
 * сверяет заголовок с {@link #issuedServiceToken()} посимвольно.
 */
public final class IdentityStub {

    /** Клиент браузера у провайдера: по нему владелец членств опознаёт тропу человека (claim {@code azp}). */
    public static final String BROWSER_CLIENT_ID = "vibetrading-web";

    /** Клиент служб у провайдера: его несёт токен, выданный на {@code client_credentials}. */
    public static final String SERVICE_CLIENT_ID = "platform-services";

    private static final String KEY_ID = "E2E-K1";

    private static final String JWKS_PATH = "/jwks";

    private static final String TOKEN_PATH = "/token";

    private static final String PRINCIPAL = "service-account-vibetrading";

    private final WireMockServer server;
    private final RSAKey key;
    private final String issuedServiceToken;

    public IdentityStub() {
        this.key = keyOf(KEY_ID);
        this.server = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .http2PlainDisabled(true));
        this.server.start();
        this.issuedServiceToken = sign("issued-" + UUID.randomUUID(), PRINCIPAL, SERVICE_CLIENT_ID, null);
        stubDiscovery();
        server.stubFor(WireMock.get(WireMock.urlEqualTo(JWKS_PATH))
                .willReturn(WireMock.okJson(new JWKSet(key.toPublicJWK()).toString())));
        server.stubFor(WireMock.post(WireMock.urlEqualTo(TOKEN_PATH)).willReturn(WireMock.okJson("""
                {"access_token": "%s", "token_type": "Bearer", "expires_in": 3600}
                """.formatted(issuedServiceToken))));
    }

    /** Издатель, которым называют себя токены и который стороны читают ключом {@code issuer-uri}. */
    public String issuer() {
        return "http://localhost:" + server.port();
    }

    /** Точка выдачи токена службам — ключ {@code token-uri} сторон, зовущих соседей. */
    public String tokenUri() {
        return issuer() + TOKEN_PATH;
    }

    /** Токен, который стаб выдаёт службам на {@code client_credentials}. */
    public String issuedServiceToken() {
        return issuedServiceToken;
    }

    /** Токен теста: подписан тем же ключом, но другой строкой, чем выданный службам. */
    public String testToken() {
        return sign("test-" + UUID.randomUUID(), PRINCIPAL, null, null);
    }

    /**
     * Токен человека — такой, какой браузер получает у провайдера.
     *
     * @param subject субъект предъявителя
     * @param name    имя предъявителя (claim {@code preferred_username})
     * @return подписанный токен с {@code azp} браузерного клиента
     */
    public String browserToken(String subject, String name) {
        return sign("browser-" + UUID.randomUUID(), subject, BROWSER_CLIENT_ID, name);
    }

    /** Обращения к провайдеру в порядке прихода. */
    public List<LoggedRequest> requests() {
        return server.getAllServeEvents().reversed().stream()
                .map(ServeEvent::getRequest)
                .toList();
    }

    /** Забывает журнал обращений. */
    public void forgetRequests() {
        server.resetRequests();
    }

    /** Останавливает стаб. */
    public void stop() {
        server.stop();
    }

    private String sign(String tokenId, String subject, String authorizedParty, String name) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer())
                .subject(subject)
                .jwtID(tokenId)
                .issueTime(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(Instant.now().plus(2, ChronoUnit.HOURS)));
        if (nonNull(authorizedParty)) {
            claims.claim("azp", authorizedParty);
        }
        if (nonNull(name)) {
            claims.claim("preferred_username", name);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                claims.build());
        try {
            jwt.sign(new RSASSASigner((RSAPrivateKey) key.toPrivateKey()));
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
