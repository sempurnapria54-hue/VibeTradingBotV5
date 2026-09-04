package com.example.tradingbot.config;

import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.security.task.DelegatingSecurityContextTaskExecutor;

/**
 * Перенос контекста хода в тред асинхронного фасада.
 *
 * <p><b>Зачем.</b> Актор записи разделяется <b>предъявленным принципалом</b>,
 * а не тредом (docs/models/domain/other/Auditable.md). Ручной триггер джобы
 * порождён человеком, но исполняется в чужом треде пула: контекст, живущий
 * только в треде вызова, до записи не доходит, и человеческая запись
 * получила бы значение контура. Ошибка при этом <b>тихая</b> — значение
 * правдоподобно и неверно, — поэтому перенос есть часть тропы, а не деталь
 * реализации.
 *
 * <p><b>Пул, а не наследование треда.</b> Наследуемый thread-local покрыл бы
 * только тред, созданный вызовом; фасады исполняются на пуле, где тред
 * переиспользуется и наследование не работает. Поэтому переносит обёртка
 * исполнителя, а не режим хранения контекста.
 *
 * <p>Планировщик сюда не входит: у запланированного тика внешнего инициатора
 * нет по построению, и пустой контекст — верный ответ, а не потеря.
 */
@Configuration
@RequiredArgsConstructor
public class AsyncSecurityContextConfig implements AsyncConfigurer {

    private final TaskExecutor applicationTaskExecutor;

    @Override
    public Executor getAsyncExecutor() {
        return new DelegatingSecurityContextTaskExecutor(applicationTaskExecutor);
    }
}
