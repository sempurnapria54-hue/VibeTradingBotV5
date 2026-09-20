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

    private Bodies() {
    }

    /** Базовая сборка запроса регистрации счёта. */
    static Registration registration(String tenantInternalId) {
        return new Registration(tenantInternalId);
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
}
