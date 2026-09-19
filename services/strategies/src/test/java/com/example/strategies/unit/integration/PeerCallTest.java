package com.example.strategies.unit.integration;

import com.example.strategies.exception.PeerReadException;
import com.example.strategies.integration.internal.api.PeerCall;
import com.example.testsupport.PeerCallContract;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Копия разбора отказа соседа по ярусу в дереве {@code strategies}
 * (`.claude/tests/cases/platform-shared-logic.md`, группы `U7`, `U8`,
 * `U12.1`, клетки `U14.3`, `U14.4`).
 *
 * <p>Ожидания живут в контракте общего артефакта и объявлены один раз:
 * сличить две копии на одном classpath нечем, поэтому каждое дерево
 * прогоняет одно и то же ожидание своей копией.
 */
class PeerCallTest extends PeerCallContract {

    @Override
    protected <T> T execute(String peer, String endpoint, Supplier<T> read) {
        return PeerCall.execute(peer, endpoint, read);
    }

    @Override
    protected Class<? extends RuntimeException> peerReadExceptionType() {
        return PeerReadException.class;
    }

    @Override
    protected Path peerCallSource() {
        return Path.of("src", "main", "java", "com", "example", "strategies",
                "integration", "internal", "api", "PeerCall.java");
    }
}
