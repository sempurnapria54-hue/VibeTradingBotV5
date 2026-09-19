package com.example.tradingcore.unit.config;

import com.example.tradingcore.config.AsyncActorContextConfigurer;
import com.example.testsupport.ActorContextPropagationContract;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Копия переносчика контекста хода в дереве {@code trading-core}
 * (`.claude/tests/cases/platform-shared-logic.md`, группа `U11`, клетка
 * `U12.3`).
 *
 * <p><b>Почему проба стои́т здесь, а не у формы актора.</b> Резолвер актора
 * — общий артефакт, и его ветви проверяются у него
 * ({@code com.example.platform.security.ActorProviderTest}); переносчик же
 * — класс ЭТОГО дерева, и на одном classpath с артефактом его нет
 * (§«Решение: где живёт дерево прогона»).
 *
 * <p><b>Чего тест НЕ мерит, и это названо.</b> Он не поднимает контекст
 * приложения: {@code @SpringBootTest} у модуля нет — он потянул бы БД и
 * соседа. Мерится поведение самой обёртки исполнителя, а не то, что каркас
 * спросит наш {@code AsyncConfigurer}; расхождение там пришло бы громко —
 * неработающим ручным триггером, а не молчанием.
 */
class AsyncActorContextConfigurerTest extends ActorContextPropagationContract {

    @Override
    protected AsyncTaskExecutor propagating(AsyncTaskExecutor delegate) {
        return AsyncActorContextConfigurer.propagating(delegate);
    }

    @Override
    protected Executor asyncExecutorWith(AsyncTaskExecutor applicationExecutor) {
        return new AsyncActorContextConfigurer(providerOf(applicationExecutor)).getAsyncExecutor();
    }

    /**
     * Поставщик исполнителя приложения: каркас отдаёт конфигуратору именно
     * его, и кейс {@code U11.4} мерит, что делегатом становится он, а не
     * свой пул.
     */
    private static ObjectProvider<AsyncTaskExecutor> providerOf(AsyncTaskExecutor executor) {
        return new ObjectProvider<>() {
            @Override
            public AsyncTaskExecutor getObject() {
                return executor;
            }

            @Override
            public AsyncTaskExecutor getObject(Object... args) {
                return executor;
            }

            @Override
            public AsyncTaskExecutor getIfAvailable() {
                return executor;
            }

            @Override
            public AsyncTaskExecutor getIfUnique() {
                return executor;
            }
        };
    }
}
