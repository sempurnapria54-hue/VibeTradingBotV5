package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.Json;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Токен человека тропой браузера: код авторизации клиента {@code web} с PKCE
 * под учётной записью держателя (.claude/decisions/stand-human-presenter.md;
 * дом — docs/rules/api-access-policy.md).
 *
 * <p><b>Ходы те же, что у браузера, и грант пароля не включается:</b> запрос
 * авторизации с {@code code_challenge} → разбор действия формы входа
 * провайдера → отправка имени и пароля с куками сессии входа → код из
 * заголовка переадресации без перехода по ней → обмен кода на токен с
 * верификатором. Образец формы — {@code tools/stand/holder-api.py}.
 *
 * <p><b>Цена названа решением:</b> форма входа — HTML чужого продукта, и
 * смена темы провайдера ломает прогон, а не пользователя. Отказ на любом ходе
 * — отказ прогона с названным ходом.
 *
 * <p><b>Токен живёт минуты, а прогон — дольше.</b> {@link #current()} берёт
 * новый токен той же тропой, когда до истечения прежнего осталось меньше
 * запаса; каждый новый — новой сессией входа (свежие куки), чтобы тропа
 * проходила форму, а не единый вход.
 */
final class HolderToken {

    /** Клиент браузера у провайдера окружения (deploy/base/services/identity-realm.yaml). */
    static final String BROWSER_CLIENT = "web";

    private static final Duration RENEW_MARGIN = Duration.ofSeconds(60);

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    private static final Pattern FORM_ACTION = Pattern.compile("<form[^>]+action=\"([^\"]+)\"");

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Integer VERIFIER_BYTES = 48;

    private String token;

    private Instant expiresAt;

    /** Токен держателя, годный не меньше запаса обновления. */
    synchronized String current() {
        if (isNull(token) || Instant.now().plus(RENEW_MARGIN).isAfter(expiresAt)) {
            token = acquire();
            expiresAt = expiryOf(token);
        }
        return token;
    }

    /**
     * Новый токен той же тропой — без кэша.
     *
     * @return токен доступа, выданный провайдером окружения
     */
    static String acquire() {
        HttpClient browser = HttpClient.newBuilder()
                .sslContext(Stand.ingressContext())
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CALL_TIMEOUT)
                .build();
        String verifier = verifier();
        String state = Long.toHexString(RANDOM.nextLong());
        String redirect = Stand.origin() + "/";
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", BROWSER_CLIENT);
        query.put("response_type", "code");
        query.put("scope", "openid");
        query.put("redirect_uri", redirect);
        query.put("code_challenge", challengeOf(verifier));
        query.put("code_challenge_method", "S256");
        query.put("state", state);
        HttpResponse<String> page = send(browser, HttpRequest.newBuilder(
                URI.create(Stand.issuer() + "/protocol/openid-connect/auth?" + form(query))).GET(), "запрос авторизации");
        String location = page.statusCode() == 200
                ? submitLogin(browser, page.body())
                : page.headers().firstValue("Location").orElse("");
        Map<String, String> answered = queryOf(location);
        if (isFalse(Objects.equals(state, answered.get("state"))) || isNull(answered.get("code"))) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: переадресация входа не несёт кода либо state чужой — "
                    + "ответ провайдера " + page.statusCode() + ", Location без кода");
        }
        Map<String, String> exchange = new LinkedHashMap<>();
        exchange.put("grant_type", "authorization_code");
        exchange.put("client_id", BROWSER_CLIENT);
        exchange.put("code", answered.get("code"));
        exchange.put("redirect_uri", redirect);
        exchange.put("code_verifier", verifier);
        HttpResponse<String> issued = send(browser, HttpRequest.newBuilder(
                        URI.create(Stand.issuer() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(exchange))), "обмен кода на токен");
        String accessToken = Json.tree(issued.body()).path("access_token").asString("");
        if (issued.statusCode() != 200 || accessToken.isEmpty()) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: обмен кода на токен отвергнут — " + issued.statusCode());
        }
        return accessToken;
    }

    /**
     * Утверждения токена без проверки подписи — для чтения субъекта, издателя
     * и момента истечения.
     */
    static JsonNode claimsOf(String jwt) {
        String[] parts = jwt.split("\\.");
        return Json.tree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    /** Момент истечения токена — утверждение {@code exp}. */
    static Instant expiryOf(String jwt) {
        return Instant.ofEpochSecond(claimsOf(jwt).path("exp").asLong());
    }

    private static String submitLogin(HttpClient browser, String page) {
        Matcher action = FORM_ACTION.matcher(page);
        if (isFalse(action.find())) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: на странице входа провайдера нет формы — "
                    + "сменилась тема провайдера? (.claude/decisions/stand-human-presenter.md §Цена)");
        }
        JsonNode credentials = Stand.holderCredentials();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("username", credentials.path("username").asString());
        fields.put("password", credentials.path("password").asString());
        HttpResponse<String> answer = send(browser, HttpRequest.newBuilder(URI.create(unescape(action.group(1))))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields))), "отправка формы входа");
        if (answer.statusCode() != 302 && answer.statusCode() != 303) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: вход не вернул переадресации — " + answer.statusCode()
                    + " (неверные учётные данные держателя?)");
        }
        return answer.headers().firstValue("Location").orElse("");
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request, String move) {
        try {
            return client.send(request.timeout(CALL_TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: ход «" + move + "» не дошёл до провайдера", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: ход «" + move + "» прерван", failure);
        }
    }

    private static String verifier() {
        byte[] bytes = new byte[VERIFIER_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 недоступен в этой JVM", failure);
        }
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static Map<String, String> queryOf(String location) {
        Map<String, String> values = new LinkedHashMap<>();
        int start = location.indexOf('?');
        if (start < 0) {
            return values;
        }
        for (String pair : location.substring(start + 1).split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                values.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    /** Действие формы приходит HTML-экранированным: {@code &amp;} и числовые сущности. */
    private static String unescape(String html) {
        Matcher numeric = NUMERIC_ENTITY.matcher(html.replace("&amp;", "&").replace("&quot;", "\""));
        StringBuilder plain = new StringBuilder();
        while (numeric.find()) {
            int code = Integer.parseInt(numeric.group(2), numeric.group(1).isEmpty() ? 10 : 16);
            numeric.appendReplacement(plain, Matcher.quoteReplacement(Character.toString(code)));
        }
        numeric.appendTail(plain);
        return plain.toString();
    }

    @Override
    public String toString() {
        return "HolderToken[" + (nonNull(expiresAt) ? "до " + expiresAt : "не взят") + "]";
    }
}
