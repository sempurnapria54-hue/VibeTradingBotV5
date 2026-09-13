package com.example.statistics.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.platform.jobs.JobExecutionGuard;
import com.example.statistics.config.AggregateRecomputeProperties;
import com.example.statistics.domain.service.AggregateRecomputeService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Пересчитывает агрегаты статистики из журнала скользящим окном последних
 * суток (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Строка агрегата — проекция, а не накопитель:</b> она
 * пересчитывается из журнала целиком и событием не двигается. Довод тот
 * же, которым итог сделки отверг инкремент: повторная доставка события
 * проекции безразлична и задваивает накопитель.
 *
 * <p><b>Писатель у строк один, и момент у него один — этот тик.</b>
 * Внерасписанного запуска через фасад у джоб сервиса не бывает:
 * поверхность объявлена только читающей, и ручной триггер завёл бы
 * входящую точку записи, которой инвентарь ей не даёт
 * (docs/architecture/services.md). «Полный пересчёт» — значение окна, а не
 * второй исполнитель.
 *
 * <p><b>А защита от перекрытия ЕСТЬ</b> — по тому же механическому
 * признаку, что у соседней чистки (.claude/rules/codestyle.md §Джобы):
 * такт здесь CRON, а он бьёт независимо от того, кончился ли предыдущий
 * проход. Проход же длителен по построению — порций в нём столько, сколько
 * суток в окне.
 *
 * <p><b>Проход идёт ПОСУТОЧНЫМИ порциями</b> — запрос и запись на каждые
 * сутки, а не один запрос на всё окно: окно, расширенное до всего журнала,
 * дало бы диапазонный скан по таблице, которая в {@code prod} не чистится
 * и растёт без предела (.claude/rules/codestyle.md §«Выборка данных…»).
 *
 * <p><b>Обрыв посреди окна ничего не стои́т:</b> пройденные сутки остаются
 * пересчитанными, непройденные — с прежними числами, и каждая половина
 * верна на свой момент сборки. Поэтому отказ порции не глушится: он
 * останавливает проход, а следующий тик начинает окно заново.
 */
@Component
@RequiredArgsConstructor
public class AggregateRecomputeJob {

    private static final String JOB_NAME = "aggregateRecomputeJob";

    private final AggregateRecomputeProperties properties;
    private final JobExecutionGuard executionGuard;
    private final AggregateRecomputeService aggregateRecomputeService;

    /** Такт пересчёта; CRON — величина конфигурации, не хардкод. */
    @Scheduled(cron = "${jobs.aggregate-recompute.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    /**
     * Проход: сутки окна от свежих к старым, каждые — своей порцией.
     *
     * <p><b>Направление выбрано, а не унаследовано:</b> свежие сутки
     * читают чаще, и обрыв прохода оставляет непересчитанными самые
     * старые — те, чьи числа и так меняются реже всего.
     */
    private void run() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime assembledAt = OffsetDateTime.now(ZoneOffset.UTC);
        for (int daysBack = 0; daysBack < properties.getWindowDays(); daysBack++) {
            aggregateRecomputeService.recomputeDay(today.minusDays(daysBack), assembledAt);
        }
    }
}
