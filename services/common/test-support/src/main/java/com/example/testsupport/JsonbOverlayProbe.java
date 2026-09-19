package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Общая сборка проб JSONB-навеса: маппер, на котором конвертер навеса
 * живёт в проде, и чтение состава ключей записанной строки
 * (.claude/tests/cases/jsonb-overlay-roundtrip.md §«Маппер сборки бина —
 * что это и почему не свежий»).
 *
 * <p><b>Свежий {@code new ObjectMapper()} базовой сборкой быть не может.</b>
 * Конструкторы шести конвертеров из семи берут {@code copy()} общего бина и
 * переопределяют в слепке ТОЛЬКО политику включения; прочая конфигурация
 * приезжает слепком. Умолчания свежего маппера и бина при этом
 * противоположны на четырёх осях, и первая из них — отказ на неизвестном
 * свойстве — несущая: на свежем маппере обратный ход собственной строки
 * навеса справочных правил падает на ключе {@code live}, который производит
 * предикат формы.
 *
 * <p><b>Пин, а не вызов сборщика Spring.</b> {@code Jackson2ObjectMapperBuilder}
 * помечен {@code @Deprecated(forRemoval)} в Spring 7, а deprecated API
 * запрещён (.claude/rules/codestyle.md §«Строгие правила»). Поэтому четыре
 * оси признаков пинятся явно, а модули берутся служебной загрузкой — тем же
 * механизмом, которым их регистрирует сборщик.
 *
 * <p><b>Носитель формы один, потому что копий у неё быть не должно.</b>
 * Сборка нужна семи пробам в трёх деревьях; уведённая в общий артефакт, она
 * не может разойтись правкой одного дерева
 * (.claude/rules/carrier-levels.md).
 */
public abstract class JsonbOverlayProbe {

    /** Обращения, которых у предмета нет ни одного: база, брокер, сеть, часы, лог. */
    private static final Pattern IO_MARKER = Pattern.compile(
            "DataService|Repository|RestClient|KafkaTemplate|JdbcTemplate|EntityManager"
                    + "|Instant\\.now|OffsetDateTime\\.now|LocalDateTime\\.now|currentTimeMillis"
                    + "|Slf4j|\\blog\\.");

    /**
     * Маппер сборки бина: то, что инжектится конвертеру в контексте
     * сервиса, воспроизведённое пином четырёх признаков и служебной
     * загрузкой модулей.
     */
    protected static ObjectMapper beanAssemblyMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        mapper.setConfig(mapper.getSerializationConfig()
                .without(MapperFeature.DEFAULT_VIEW_INCLUSION));
        mapper.setConfig(mapper.getDeserializationConfig()
                .without(MapperFeature.DEFAULT_VIEW_INCLUSION));
        return mapper;
    }


    /**
     * Ввода-вывода у предмета нет: ни базы, ни брокера, ни вызова наружу, ни
     * часов, ни лога (кейс `U12.1`). Читается по исходникам конвертеров —
     * прогон отсутствия обращения наблюдать не может, а отсутствие самого
     * обращения наблюдаемо.
     */
    protected static void assertNoRuntimeIo(Path... sources) throws IOException {
        assertThat(sources).as("перечень исходников пуст — мерить было бы нечего").isNotEmpty();
        for (Path source : sources) {
            assertThat(Files.isRegularFile(source))
                    .as("исходника %s нет — проба мерила бы пустоту", source)
                    .isTrue();
            String text = Files.readString(source, StandardCharsets.UTF_8);
            assertThat(IO_MARKER.matcher(text).find())
                    .as("%s: у конвертера навеса нет ввода-вывода — это признак уровня", source)
                    .isFalse();
        }
    }

    /**
     * Состав ключей верхнего уровня записанной строки — вторая точка
     * наблюдения предмета: тождество пары о ключе, которого в форме нет,
     * ничего не говорит.
     */
    protected static List<String> keysOf(String json) {
        List<String> keys = new ArrayList<>();
        readTree(json).fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    /** Разбор строки навеса для наблюдения её состава, а не её значения. */
    protected static JsonNode readTree(String json) {
        try {
            return beanAssemblyMapper().readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Строка навеса не разобралась при наблюдении состава", e);
        }
    }
}
