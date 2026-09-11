package com.example.auditstatistics;

import com.example.testsupport.SchedulerCapacityContract;

/**
 * Проба вместимости планировщика журнала аудита: форма общая
 * ({@link SchedulerCapacityContract}), дерево и конфигурация — свои.
 *
 * <p><b>Измеритель среди джоб сервиса — тик состояния приёма</b>
 * ({@link com.example.auditstatistics.domain.jobs.ReceptionStateJob}):
 * единственный писатель и durable-момента живости, и рядов экспорта. Его
 * голодание наблюдателю неотличимо от мёртвого приёма — возраст последнего
 * принятого события едет наружу моментом и растёт одинаково в обоих
 * состояниях (docs/components/ReceptionStateJob.md §«Форма — джоба без
 * ручного фасада, и это объявлено»).
 */
class SchedulerCapacityTest extends SchedulerCapacityContract {
}
