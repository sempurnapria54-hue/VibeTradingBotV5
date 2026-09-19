package com.example.tradingcore.unit.integration;

import com.example.testsupport.PeerCallContract;
import com.example.tradingcore.exception.PeerReadException;
import com.example.tradingcore.integration.internal.api.PeerCall;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Копия разбора отказа соседа по ярусу в дереве {@code trading-core}
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
        return Path.of("src", "main", "java", "com", "example", "tradingcore",
                "integration", "internal", "api", "PeerCall.java");
    }
}
