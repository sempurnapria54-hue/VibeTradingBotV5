package com.example.auth.box;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Тела запросов поверхности: базовая сборка плюс сдвиг одной оси.
 *
 * <p><b>Базовая сборка объявлена один раз, и это несущее свойство.</b>
 * Негатив «обязательный вход не предъявлен» покрыт ПО ЕДИНИЦЕ — по клетке
 * на каждое обязательное поле (`.claude/tests/cases/auth.md` §«Чем
 * достаются выходы»), — и без единой базовой сборки каждая такая клетка
 * несла бы свои семь полей: расхождение сборок сделало бы красный прогон
 * непрочитываемым.
 *
 * <p>Собирается текстом, а не объектом: тело кейса {@code B5.6}
 * неразбираемо по построению, и типизованная форма его не выражает.
 */
final class Bodies {

    /** Узнаваемые значения ключей: ими наблюдается их невыход наружу. */
    static final String API_KEY_MARKER = "api-KEY-MARKER";

    /** Узнаваемое значение секрета ключа. */
    static final String SECRET_MARKER = "secret-MARKER";

    /** Узнаваемое значение passphrase ключа. */
    static final String PASSPHRASE_MARKER = "pass-MARKER";

    /**
     * Узнаваемые значения НОВЫХ ключей смены: отличны от ключей регистрации,
     * иначе «новые легли» было бы неотличимо от «старые остались».
     */
    static final String ROTATED_API_KEY_MARKER = "api-KEY-ROTATED";

    /** Узнаваемое значение нового секрета ключа. */
    static final String ROTATED_SECRET_MARKER = "secret-ROTATED";

    /** Узнаваемое значение нового passphrase ключа. */
    static final String ROTATED_PASSPHRASE_MARKER = "pass-ROTATED";

    private Bodies() {
    }

    /** Базовая сборка запроса регистрации счёта. */
    static Registration registration(String tenantInternalId) {
        return new Registration(tenantInternalId);
    }

    /** Базовая сборка запроса смены ключей счёта. */
    static KeyRotation keyRotation() {
        return new KeyRotation();
    }

    /** Запрос регистрации счёта: семь полей, каждое обязательно. */
    static final class Registration {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private Registration(String tenantInternalId) {
            fields.put("tenantInternalId", tenantInternalId);
            fields.put("exchangeCode", "OKX");
            fields.put("label", "main");
            fields.put("contour", "DEMO");
            fields.put("apiKey", API_KEY_MARKER);
            fields.put("secret", SECRET_MARKER);
            fields.put("passphrase", PASSPHRASE_MARKER);
        }

        /** Сдвигает одну ось запроса. */
        Registration with(String field, String value) {
            fields.put(field, value);
            return this;
        }

        /** Тело запроса. */
        String body() {
            return json(fields);
        }
    }

    /**
     * Запрос смены ключей: три поля, каждое обязательно. Контура в базовой
     * сборке нет — операция его не принимает, и лишнее поле кейс добавляет
     * сдвигом оси сам.
     */
    static final class KeyRotation {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private KeyRotation() {
            fields.put("apiKey", ROTATED_API_KEY_MARKER);
            fields.put("secret", ROTATED_SECRET_MARKER);
            fields.put("passphrase", ROTATED_PASSPHRASE_MARKER);
        }

        /** Сдвигает одну ось запроса. */
        KeyRotation with(String field, String value) {
            fields.put(field, value);
            return this;
        }

        /** Тело запроса. */
        String body() {
            return json(fields);
        }
    }

    private static String json(Map<String, String> fields) {
        StringBuilder text = new StringBuilder("{");
        fields.forEach((name, value) -> {
            if (text.length() > 1) {
                text.append(',');
            }
            text.append('"').append(name).append("\":");
            if (Objects.isNull(value)) {
                text.append("null");
            } else {
                text.append('"').append(value).append('"');
            }
        });
        return text.append('}').toString();
    }
}
