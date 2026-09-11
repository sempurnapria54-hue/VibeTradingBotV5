package com.example.tradingcore.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Контекст хода переживает асинхронный фасад.
 *
 * <p><b>Зачем.</b> Актора хода различает ПРЕДЪЯВЛЕННЫЙ принципал, а не
 * тред записи (docs/models/domain/other/Auditable.md §«Носитель
 * дискриминатора — контекст хода, а не поле модели»). У ядра тропа человека
 * до чужого треда не гипотетическая: полную ручную остановку держатель
 * запускает через асинхронный фасад, а её ход заводит и строку отчёта, и
 * событие подъёма ступени — обе записи несут актора. Без переноса контекста
 * они получили бы класс контура вместо имени принципала, и ошибка была бы
 * <b>тихой</b>: значение правдоподобно и неверно.
 *
 * <p><b>Почему обёртка исполнителя, а не наследуемый тред-локал.</b>
 * Наследование отдаёт контекст в момент СОЗДАНИЯ треда, а треды пула
 * переиспользуются: второй ход получил бы принципала первого — то есть
 * дефект строже исходного. Обёртка снимает контекст в момент постановки
 * задачи и очищает его после выполнения.
 *
 * <p><b>Делегат — исполнитель приложения</b>, а не свой пул: собственный
 * пул стал бы вторым носителем настроек исполнения, разошедшимся с
 * объявленными в конфигурации.
 *
 * <p><b>Класс — {@code @Component}, а не {@code @Configuration}, и это не
 * вкус.</b> Каркас <b>подменяет</b> бин, реализующий {@code AsyncConfigurer},
 * своей обёрткой (она подставляет исполнитель приложения, когда делегат
 * отдаёт пустоту); подменять так класс конфигурации значило бы менять его
 * тип посреди сборки контекста. Своих {@code @Bean}-методов у класса нет —
 * подменять нечего.
 */
@Component
public class AsyncActorContextConfigurer implements AsyncConfigurer {

    private final ObjectProvider<AsyncTaskExecutor> applicationTaskExecutor;

    public AsyncActorContextConfigurer(
            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
            ObjectProvider<AsyncTaskExecutor> applicationTaskExecutor) {
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return propagating(applicationTaskExecutor.getObject());
    }

    /**
     * Исполнитель, переносящий контекст хода в порождённый тред.
     *
     * <p>Вынесен отдельным методом, потому что у него есть проба: она
     * подставляет свой делегат и смотрит, доходит ли предъявленный
     * принципал до задачи.
     */
    public static AsyncTaskExecutor propagating(AsyncTaskExecutor delegate) {
        return new DelegatingSecurityContextAsyncTaskExecutor(delegate);
    }
}
