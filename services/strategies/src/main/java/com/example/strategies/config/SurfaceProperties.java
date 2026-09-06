package com.example.strategies.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки внешней поверхности владельца определений.
 *
 * <p><b>Величина живёт в конфигурации, а не литералом в контроллере.</b>
 * Она калибруется наблюдением — числом определений у тенанта, — и
 * зашитая в код потребовала бы сборки на всякую перекалибровку.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "surface")
public class SurfaceProperties {

    /**
     * Окно перечня определений тенанта.
     *
     * <p><b>Окно обязательно, а не желательно:</b> число определений
     * сверху ничем не ограничено, и безлимитное чтение кладёт базу на
     * растущей таблице (.claude/rules/codestyle.md §«Выборка данных»).
     *
     * <p><b>Названное ограничение: постраничного обхода у перечня нет.</b>
     * Тенант, у которого определений больше окна, старых через эту точку
     * не увидит; порядок — от новых к старым, поэтому отрезается наименее
     * интересный хвост. Условие возврата и владелец —
     * `.claude/work/backlog.md` §«Постраничный обход перечня определений».
     */
    private Integer strategyListWindow = 200;
}
