package com.example.tradingcore;

import com.example.platform.client.ServiceClientConfig;
import com.example.platform.jobs.JobExecutionGuard;
import com.example.platform.security.ActorProvider;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.SizeCalculator;
import com.example.strategy.engine.calc.StrategyActionCalculator;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingcore.config.EnvironmentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа сервиса {@code trading-core}.
 *
 * <p><b>Расписание и асинхронный запуск включены с первого дня:</b> ядро
 * живёт проходами оркестратора, наблюдения фактов и реле outbox, а
 * внерасписанный запуск джобы идёт через асинхронный фасад
 * ({@code .claude/rules/codestyle.md} §Джобы).
 *
 * <p><b>Взятое из общего артефакта периметра названо ИМЕНОВАННО</b>, а не
 * взято сканированием его пакета: перечень взятого читается в одном
 * месте, а то, чего сервису не нужно, не приезжает к нему вместе с
 * пакетом. Точек входа отказа доступа здесь нет и не будет, пока
 * писателя следа у ядра не появится (docs/rules/api-access-policy.md
 * §«След отказа пишет тот, у кого есть база»,
 * .claude/work/backlog.md §«Таблица отказов доступа у сервисов со своей
 * базой»).
 *
 * <p><b>Движок стратегий берётся тем же именованным ходом, и без него
 * контекст не поднимался вовсе.</b> Интерпретатор условий и расчётный
 * слой лежат в общем артефакте {@code strategy-engine}, то есть вне
 * пакета сервиса, и сканирование умолчания их не видит; ядро —
 * единственный их потребитель, и до подъёма чёрного ящика этого никто не
 * замечал: ни один прогон дерева контекста сервиса не поднимал. Четыре
 * класса названы поимённо, а не подтянуты
 * сканированием чужого пакета: перечень взятого читается в одном месте.
 *
 * <p><b>Область отображаемых классов названа явно.</b> Базовый тип
 * audit-полей лежит в общем артефакте, то есть вне пакета сервиса, и
 * умолчание сканирования его не видит. Свой пакет перечисляется рядом:
 * {@code @EntityScan} умолчание ЗАМЕЩАЕТ, а не дополняет.
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(EnvironmentProperties.class)
@Import({ActorProvider.class, JobExecutionGuard.class, ServiceClientConfig.class,
        StrategyConditionEvaluator.class, StrategyActionCalculator.class,
        PriceCalculator.class, SizeCalculator.class})
@EntityScan({"com.example.tradingcore", "com.example.tradingbot.persistence.model"})
public class TradingCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingCoreApplication.class, args);
    }
}
