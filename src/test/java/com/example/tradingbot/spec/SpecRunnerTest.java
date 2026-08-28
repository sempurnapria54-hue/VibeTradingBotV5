package com.example.tradingbot.spec;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Прогон исполнимых спецификаций против их примеров.
 *
 * <p>Это проверка корпуса: правка формулы, предиката или перечня доказывается
 * прогоном примеров, а не вычиткой доков.
 */
class SpecRunnerTest {

    private static final Path SPEC_DIRECTORY = Path.of("docs", "spec");

    @TestFactory
    List<DynamicTest> specificationsMatchTheirExamples() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Path file : Spec.specFiles(SPEC_DIRECTORY)) {
            Spec spec = Spec.load(file);
            tests.add(DynamicTest.dynamicTest(spec.subject(), () -> {
                List<String> failures = spec.run();
                assertTrue(failures.isEmpty(), String.join("\n", failures));
            }));
        }
        assertTrue(!tests.isEmpty(), "Спецификаций не найдено в " + SPEC_DIRECTORY);
        return tests;
    }
}
