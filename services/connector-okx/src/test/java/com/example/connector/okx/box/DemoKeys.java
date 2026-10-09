package com.example.connector.okx.box;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.util.ExchangeAccountSecretFields;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.json.JsonParserFactory;

/**
 * Ключи demo-счёта держателя — вход мишени {@code -D}
 * (.claude/tests/cases/connector-okx.md §«Две мишени и суффикс метки»).
 *
 * <p><b>Ключей два, и роли у них разные.</b> Торговый ({@code read_only,trade})
 * кладётся в хранилище субстрата по адресу счёта — им подписывает запросы
 * ПРЕДМЕТ, и им же ставится принудительная зачистка. Ключ только чтения —
 * канал проверки конца: авторитет «вернулся ли счёт» читает площадку, а не
 * мнение проверяемой системы о ней
 * (.claude/tests/case-material/connector-okx.md §«2. Инвариант восстановления
 * состояния stateful-кейса»).
 *
 * <p><b>Файлы — держателя, и в репозиторий не попадают.</b> Путь берётся из
 * его каталога стенда (.claude/skills/local-stand.md §«Тестовые данные
 * стенда»); переопределяется системными свойствами {@link #TRADE_FILE_PROPERTY}
 * и {@link #READ_FILE_PROPERTY}.
 *
 * <p><b>Нет файла — отказ прогона, а не пропуск.</b> Пропуск читался бы
 * зелёным, то есть ровно «мерить было нечем», выданным за «дефектов нет»
 * (.claude/rules/measurement-commands.md).
 *
 * <p><b>Значения не выходят ни в одно сообщение.</b> Текстовое представление
 * записи их не несёт, а отказ разбора файла теряет причину намеренно: её
 * текст пишет разборщик, и он вправе процитировать содержимое.
 *
 * @param apiKey     API-ключ
 * @param secret     секрет ключа
 * @param passphrase passphrase ключа
 */
record DemoKeys(String apiKey, String secret, String passphrase) {

    /** Системное свойство: путь файла торгового ключа. */
    static final String TRADE_FILE_PROPERTY = "okx.demo.trade-keys";

    /** Системное свойство: путь файла ключа только чтения. */
    static final String READ_FILE_PROPERTY = "okx.demo.read-keys";

    private static final Path STAND = Path.of(System.getProperty("user.home"), "vibetrading-stand");

    private static final String TRADE_FILE = "okx-demo-trade-dev.json";

    private static final String READ_FILE = "okx-demo-read-dev.json";

    /** Торговый ключ demo-счёта. */
    static DemoKeys trade() {
        return load(Path.of(System.getProperty(TRADE_FILE_PROPERTY, STAND.resolve(TRADE_FILE).toString())));
    }

    /** Ключ того же счёта только на чтение. */
    static DemoKeys readOnly() {
        return load(Path.of(System.getProperty(READ_FILE_PROPERTY, STAND.resolve(READ_FILE).toString())));
    }

    @Override
    public String toString() {
        return "DemoKeys[значения скрыты]";
    }

    private static DemoKeys load(Path file) {
        if (Files.notExists(file)) {
            throw new IllegalStateException("Мишень -D: файла ключей demo нет — " + file
                    + ". Прогон без ключей держателя не проводится (.claude/skills/local-stand.md"
                    + " §«Тестовые данные стенда»)");
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException failure) {
            throw new IllegalStateException("Мишень -D: файл ключей demo не прочитан — " + file, failure);
        }
        Map<String, Object> fields;
        try {
            fields = JsonParserFactory.getJsonParser().parseMap(text);
        } catch (RuntimeException notJson) {
            // Причина отброшена намеренно: её сообщение вправе процитировать ключи.
            throw new IllegalStateException("Мишень -D: файл ключей demo не разобран как объект JSON — " + file);
        }
        return new DemoKeys(
                required(fields, ExchangeAccountSecretFields.API_KEY, file),
                required(fields, ExchangeAccountSecretFields.SECRET, file),
                required(fields, ExchangeAccountSecretFields.PASSPHRASE, file));
    }

    private static String required(Map<String, Object> fields, String name, Path file) {
        Object value = fields.get(name);
        if (isNull(value) || isBlank(value.toString())) {
            throw new IllegalStateException("Мишень -D: в файле ключей demo нет поля «" + name + "» — " + file);
        }
        return value.toString();
    }
}
