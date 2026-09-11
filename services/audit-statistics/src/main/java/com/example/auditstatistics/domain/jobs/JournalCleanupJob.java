package com.example.auditstatistics.domain.jobs;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.auditstatistics.config.EnvironmentProperties;
import com.example.auditstatistics.config.JournalCleanupProperties;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.JournalRetentionProfile;
import com.example.auditstatistics.domain.service.JournalCleanupService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Чистит журнал аудита в непроизводственном окружении: удаляет строки,
 * принятые раньше назначенной глубины, и тем же проходом гасит момент
 * разрыва у пар, чей разрыв эта чистка вынесла за нижнюю границу полноты
 * (docs/components/JournalCleanupJob.md).
 *
 * <p><b>Почему исполнитель вообще нужен.</b> Первый операнд нижней границы
 * полноты объявлен несущим ИМЕННО ПОТОМУ, что непроизводственное окружение
 * журнал чистит: безличное «чистится по профилю» оставляло бы величину без
 * писателя, а ветвь — без момента
 * (docs/models/domain/other/AuditRecord.md §«Позиция чтения и с какого
 * момента журнал полон»).
 *
 * <p><b>Операндов у прохода два, и носители у них разные:</b>
 * применимость — ось окружения {@code journalRetentionProfile},
 * доезжающая до сервиса ключом конфигурации манифеста; глубина в сутках —
 * величина конфигурации сервиса. В {@code prod} проход не «отключён
 * администратором», а НЕ ИМЕЕТ ПРЕДМЕТА по значению оси.
 *
 * <p><b>Ручного фасада нет</b> — по доводу сервиса: поверхность
 * {@code audit-statistics} объявлена только читающей, и ручной триггер
 * завёл бы входящую точку записи, которой инвентарь ей не даёт.
 * Пропущенный проход при этом невыполненной работы не оставляет: следующий
 * удаляет всё, что старше глубины, включая пропущенное.
 *
 * <p><b>А защита от перекрытия ЕСТЬ, и это не исключение из соседского
 * довода, а его граница</b> (.claude/rules/codestyle.md §Джобы). Охрану
 * снимает ПАРА «такт {@code fixedDelay} плюс отсутствие второго
 * запускающего», и у соседнего тика она сходится целиком. Здесь такт —
 * CRON: он бьёт независимо от того, кончился ли предыдущий проход, а
 * проход длителен по построению.
 *
 * <p><b>Границы:</b> строк состояния приёма он не трогает, кроме момента
 * разрыва — состав пар, признак подписки и момент обновления ведёт
 * {@code ReceptionStateJob}; агрегатов не трогает вовсе (строка выводима
 * из журнала повторным пересчётом), хотя косвенно и сужает область
 * пересчёта, двигая вперёд самую раннюю уцелевшую строку.
 *
 * <p><b>Строк отказа доступа он тоже не трогает, и это названо.</b>
 * {@code access_denials} лежит в той же базе {@code audit}, но глубины у
 * неё нет: журнальная ей не принадлежит — она выведена из другого вопроса,
 * — а своя назначается вместе с реакцией на серию отказов, которой нужна
 * та же наблюдённая частота (docs/components/JournalCleanupJob.md
 * §Границы, docs/models/domain/other/AccessDenial.md §Персистентность).
 * <b>Цена со-расположения названа там же:</b> рост, инициируемый
 * посторонним, идёт в том ряда, чья гарантия — «потеря недопустима».
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JournalCleanupJob {

    private static final String JOB_NAME = "journalCleanupJob";

    /**
     * Размер порции удаления — локальная техническая константа, а не
     * величина конфигурации: исход прохода от неё не зависит, и владельца
     * с якорем калибровки у неё нет
     * (docs/components/JournalCleanupJob.md §«Чем ограничен проход:
     * применимость из оси, глубина из конфигурации»).
     */
    private static final Integer BATCH_SIZE = 1_000;

    private final JournalCleanupProperties properties;
    private final EnvironmentProperties environmentProperties;
    private final ReceptionProperties receptionProperties;
    private final JobExecutionGuard executionGuard;
    private final JournalCleanupService journalCleanupService;

    /** Такт чистки; CRON — величина конфигурации, не хардкод. */
    @Scheduled(cron = "${jobs.journal-cleanup.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    /**
     * Проход: сперва удаление порциями, затем снятие момента разрыва.
     *
     * <p><b>Порядок несущий.</b> Момент разрыва гаснет по НОВОЙ нижней
     * границе — той, которую подвинуло удаление; снятие, сделанное до
     * удаления, мерило бы границу, которой этот проход ещё не создал, и
     * оставляло бы разрыв ложным ещё на один такт.
     */
    private void run() {
        if (isFalse(isApplicable())) {
            return;
        }
        deleteRecordedBefore(OffsetDateTime.now(ZoneOffset.UTC).minusDays(properties.getDepthDays()));
        journalCleanupService.clearGapsOutsideLowerBound(receptionProperties.getGroupId());
    }

    /**
     * Есть ли у чистки предмет в этом окружении.
     *
     * <p><b>Пустое значение оси означает «ось не доехала», а не «не
     * чистить»</b> (docs/rules/absent-value-semantics.md), и исход у обоих
     * состояний ОДИН — прохода не будет, — но различаются они следом:
     * недоехавшая ось есть дефект развёртывания, и молчаливое умолчание в
     * любую сторону скрыло бы его. Направление выбранного исхода
     * консервативно: удаление необратимо, и чистить по значению, которого
     * никто не назначал, значило бы терять журнал по недоразумению
     * (docs/components/JournalCleanupJob.md §«Ось не доехала — проход не
     * идёт, и это не то же самое, что `UNBOUNDED`»).
     */
    private Boolean isApplicable() {
        JournalRetentionProfile profile = environmentProperties.getJournalRetentionProfile();
        if (isNull(profile)) {
            log.warn("Journal retention profile is not configured: the cleanup makes no pass at all");
            return Boolean.FALSE;
        }
        return profile.isCleanupApplicable();
    }

    /**
     * Удаление ограниченными порциями, пока старее глубины ничего не
     * остаётся.
     *
     * <p><b>Признак конца — неполная порция.</b> Полная означает, что
     * подходящих строк было не меньше её размера, то есть остаток
     * возможен; неполная — что отбор исчерпан. Считать «пока удалено
     * больше нуля» было бы тем же по исходу и на одну порцию дороже.
     */
    private void deleteRecordedBefore(OffsetDateTime threshold) {
        Integer deleted;
        do {
            deleted = journalCleanupService.deleteBatchRecordedBefore(threshold, BATCH_SIZE);
        } while (Objects.equals(deleted, BATCH_SIZE));
    }
}
