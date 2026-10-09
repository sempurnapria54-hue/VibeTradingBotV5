package com.example.tests.e2e.smokelive;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Токены, которые периметр обязан отвергнуть, — вход кейса {@code E1.4}
 * (.claude/tests/cases/smoke-live.md §«E1.4 — Токен чужого издателя периметром
 * не принят»).
 *
 * <p><b>Издатель у всех — тот же, что у провайдера окружения</b>
 * ({@link Stand#issuer()}), и клиент — браузерный: отказ обязан приходить по
 * ПОДПИСИ, а не по несовпадению издателя или клиента, иначе кейс мерил бы не
 * локальную проверку подписи, а сверку утверждений.
 *
 * <p>Ключ подписи заводится прогоном и наружу не уходит; провайдер окружения
 * его не знает по построению.
 */
final class ForeignTokens {

    private static final String KEY_ID = "smoke-foreign-key";

    private ForeignTokens() {
    }

    /**
     * Токен своим ключом с утверждениями токена держателя: тот же субъект,
     * издатель и клиент, свежие моменты.
     *
     * @param holderToken настоящий токен держателя — образец утверждений
     * @return токен, подпись которого не сходится ни с одним ключом провайдера
     */
    static String signedByOwnKey(String holderToken) {
        JsonNode claims = HolderToken.claimsOf(holderToken);
        RSAKey key = freshKey();
        JWTClaimsSet set = new JWTClaimsSet.Builder()
                .issuer(Stand.issuer())
                .subject(claims.path("sub").asString())
                .jwtID("smoke-foreign-" + UUID.randomUUID())
                .claim("azp", HolderToken.BROWSER_CLIENT)
                .claim("preferred_username", claims.path("preferred_username").asString(""))
                .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), set);
        try {
            jwt.sign(new RSASSASigner((RSAPrivateKey) key.toPrivateKey()));
        } catch (JOSEException failure) {
            throw new IllegalStateException("Токен своим ключом не подписался", failure);
        }
        return jwt.serialize();
    }

    /**
     * Настоящий токен держателя с подменённым субъектом: заголовок и подпись
     * провайдера на месте, а подписанное содержимое другое — подпись не
     * сходится с ключом провайдера.
     *
     * @param holderToken настоящий токен держателя
     * @return токен с подписью провайдера над другим содержимым
     */
    static String tamperedProviderToken(String holderToken) {
        String[] parts = holderToken.split("\\.");
        ObjectNode claims = (ObjectNode) HolderToken.claimsOf(holderToken);
        claims.put("sub", "smoke-tampered-" + UUID.randomUUID());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + payload + "." + parts[2];
    }

    private static RSAKey freshKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey(pair.getPrivate())
                    .keyID(KEY_ID)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("RSA недоступен в этой JVM", failure);
        }
    }
}
