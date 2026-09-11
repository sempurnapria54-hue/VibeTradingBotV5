package com.example.auditstatistics.domain.jobs;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.auditstatistics.config.AggregateRecomputeProperties;
import com.example.auditstatistics.domain.service.AggregateRecomputeService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        for (int daysBack = 0; daysBack < properties.getWindowDays(); daysBack++) {
            recomputeIfJournalCovers(today.minusDays(daysBack));
        }
    }

    /**
     * Сутки пересчитываются, только если журнал покрывает их целиком.
     *
     * <p><b>Проход не пишет суток, начавшихся раньше самой ранней
     * уцелевшей строки журнала.</b> Сутки, часть строк которых удалена
     * чисткой непроизводственного окружения, дали бы группу меньшего
     * состава, а запись по ключу зерна заменила бы верные числа частичными
     * — молча, потому что момент сборки строки остался бы свежим.
     *
     * <p><b>Оси времени у сравнения разные, и расхождение консервативно:</b>
     * сутки зерна отсчитываются по моменту происшествия, операнд считается
     * по моменту приёма. У всякой удалённой строки момент приёма был
     * меньше самого раннего уцелевшего, а произойти событие раньше, чем
     * было принято, не могло, — значит сутки, начавшиеся не раньше
     * операнда, ни одной строки потерять не могли.
     *
     * <p><b>Отбор охраняет от ухудшения, а не восстанавливает:</b> строка
     * отобранных суток остаётся такой, какой её собрал последний прогон по
     * тому журналу, что был; если её не собирал ни один — строки нет вовсе,
     * и читателю остаётся клейм нижней границы полноты.
     *
     * <p><b>Пустой операнд означает пустой журнал</b>, и исход у него тот
     * же, что дала бы группировка: строк не появляется ни одной. Ветвь
     * разобрана здесь, а не оставлена арифметике: сравнение с пустым
     * моментом отказом не является, оно просто не имеет смысла.
     *
     * <p><b>Операнд читается на КАЖДУЮ порцию, а не один раз на проход:</b>
     * чистка идёт своим тиком и своей транзакцией, и число, добытое до
     * начала работы, к последним суткам окна уже описывало бы прошлое.
     * Что оба хода не сериализованы между собой — названное ограничение:
     * общей транзакции у них нет по построению, а проекция от повтора не
     * портится, и следующий проход пересобирает сутки заново.
     *
     * <p><b>Ходы при этом идут ОДНОВРЕМЕННО, а не только чередуются:</b>
     * потоков у планировщика столько же, сколько джоб у сервиса
     * (.claude/rules/codestyle.md §Джобы). Удаление, зафиксированное между
     * чтением операнда и группировкой тех же суток, отбором не ловится —
     * цена названа у дома отбора (docs/rules/statistics-aggregates.md
     * §«Пересчёт — проекция, а не накопитель») и достижима лишь там, где
     * окно дотягивается до глубины хранения.
     */
    private void recomputeIfJournalCovers(LocalDate bucketDate) {
        OffsetDateTime earliestRecorded = aggregateRecomputeService.earliestJournalMoment();
        if (isNull(earliestRecorded)) {
            return;
        }
        if (bucketDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime().isBefore(earliestRecorded)) {
            log.debug("Bucket {} starts before the earliest surviving journal record: not recomputed", bucketDate);
            return;
        }
        aggregateRecomputeService.recomputeDay(bucketDate, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
