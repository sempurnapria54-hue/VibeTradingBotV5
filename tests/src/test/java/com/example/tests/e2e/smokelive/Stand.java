package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.Json;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import tools.jackson.databind.JsonNode;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Что дым берёт снаружи окружения: имя хоста, доверие к сертификату, учётную
 * запись держателя и ключ только чтения demo-счёта
 * (.claude/tests/cases/smoke-live.md §«Чем достаются выходы»;
 * .claude/skills/local-stand.md §«Вход снаружи окружения»).
 *
 * <p><b>Каждое отсутствие — отказ прогона, а не пропуск.</b> Файла нет, имя
 * не резолвится, поле пусто — бросается {@link IllegalStateException} с
 * адресом того, что недостаёт: молчаливый пропуск читался бы зелёным
 * (.claude/tests/cases/smoke-live.md §«Новая ось формы — МИШЕНЬ РАЗВЁРНУТА, А
 * ПРОГОН СНАРУЖИ НЕЁ»: невыполненное предусловие есть отказ прогона).
 *
 * <p><b>Имя хоста резолвится средой, а не файлом имён JVM.</b> Файл
 * {@code -Djdk.net.hosts.file} подменяет резолвер JVM ЦЕЛИКОМ: имя площадки,
 * которое читает канал чтения ({@link OkxReadChannel}), в нём не значится, и
 * канал отказал бы на резолве. Поэтому прогон опирается на строку
 * {@code hosts} среды (на маке её кладёт
 * {@code ~/vibetrading-stand/macos-host-setup.sh}) и проверяет резолв первым
 * ходом.
 *
 * <p>Величины переопределяются свойствами JVM — прогон против другого
 * окружения меняет их, а не код: {@code smoke.ingress.host},
 * {@code smoke.stand.directory}, {@code smoke.okx.base-url}.
 */
final class Stand {

    /** Имя хоста окружения по умолчанию — ось {@code ingressHost} окружения {@code dev}. */
    private static final String DEFAULT_HOST = "dev.vibetrading.invalid";

    /** Каталог файлов стенда вне репозитория. */
    private static final String DEFAULT_DIRECTORY = System.getProperty("user.home") + "/vibetrading-stand";

    /** Адрес площадки — тот же, что у коннектора окружения (deploy/base/services/connector-okx.yaml). */
    private static final String DEFAULT_OKX = "https://www.okx.com";

    private static final String CERTIFICATE = "ingress-dev.crt";

    private static final String HOLDER = "identity-holder-dev.json";

    private static final String READ_KEY = "okx-demo-read-dev.json";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private Stand() {
    }

    /** Имя хоста окружения. */
    static String host() {
        return System.getProperty("smoke.ingress.host", DEFAULT_HOST);
    }

    /** Корень входа снаружи: {@code https://<ingressHost>}. */
    static String origin() {
        return "https://" + host();
    }

    /** Издатель токенов окружения — имя хоста и реалм (.claude/decisions/token-issuer-environment-host.md). */
    static String issuer() {
        return origin() + "/realms/vibetrading";
    }

    /** Адрес площадки, на который канал чтения шлёт подписанные запросы. */
    static String okxBaseUrl() {
        return System.getProperty("smoke.okx.base-url", DEFAULT_OKX);
    }

    /**
     * Проверяет, что имя хоста резолвится у прогона.
     *
     * @throws IllegalStateException имя не резолвится — отказ прогона
     */
    static void requireHostResolves() {
        try {
            InetAddress.getByName(host());
        } catch (UnknownHostException failure) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: имя хоста окружения " + host()
                    + " не резолвится — строка hosts среды не заведена"
                    + " (.claude/skills/local-stand.md §«Вход снаружи окружения»)", failure);
        }
    }

    /**
     * HTTP-клиент входа снаружи: доверяет ровно сертификату окружения, по
     * переадресациям не ходит — код авторизации забирается из заголовка, а
     * не переходом.
     */
    static HttpClient ingressClient() {
        return HttpClient.newBuilder()
                .sslContext(ingressContext())
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Имя и пароль учётной записи держателя — поля {@code username}, {@code password}. */
    static JsonNode holderCredentials() {
        return requiredFile(HOLDER, "username", "password");
    }

    /** Ключ только чтения demo-счёта — поля {@code apiKey}, {@code secret}, {@code passphrase}. */
    static JsonNode readKey() {
        return requiredFile(READ_KEY, "apiKey", "secret", "passphrase");
    }

    /** Контекст TLS, доверяющий ровно сертификату окружения. */
    static SSLContext ingressContext() {
        Path certificate = file(CERTIFICATE);
        try (InputStream input = Files.newInputStream(certificate)) {
            Certificate trusted = CertificateFactory.getInstance("X.509").generateCertificate(input);
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            store.setCertificateEntry("ingress", trusted);
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException failure) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: сертификат окружения не прочитался — " + certificate,
                    failure);
        }
    }

    private static JsonNode requiredFile(String name, String... fields) {
        Path path = file(name);
        JsonNode content;
        try {
            content = Json.tree(Files.readString(path));
        } catch (IOException failure) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: файл стенда не прочитался — " + path, failure);
        }
        for (String field : fields) {
            if (isBlank(content.path(field).asString(""))) {
                throw new IllegalStateException("ОТКАЗ ПРОГОНА: в файле " + path + " пусто поле " + field);
            }
        }
        return content;
    }

    private static Path file(String name) {
        Path path = Path.of(System.getProperty("smoke.stand.directory", DEFAULT_DIRECTORY), name);
        if (isFalse(Files.isRegularFile(path))) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: файла стенда нет — " + path
                    + " (.claude/skills/local-stand.md §«Вход снаружи окружения»)");
        }
        return path;
    }
}
