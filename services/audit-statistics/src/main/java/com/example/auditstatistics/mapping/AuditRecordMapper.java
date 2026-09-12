package com.example.auditstatistics.mapping;

import com.example.auditstatistics.api.model.AuditRecordApiResponse;
import com.example.auditstatistics.api.model.JournalCompletenessApiResponse;
import com.example.auditstatistics.api.model.JournalPageApiResponse;
import com.example.auditstatistics.api.model.JournalReadApiQuery;
import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.domain.model.JournalCompleteness;
import com.example.auditstatistics.domain.model.JournalPage;
import com.example.auditstatistics.domain.model.JournalQuery;
import com.example.auditstatistics.integration.internal.event.model.AuditEventMessage;
import com.example.auditstatistics.persistence.model.journal.AuditRecordEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Переходы журнальной выборки между слоями: api → domain → persistence и
 * обратно (.claude/rules/codestyle.md §Маппинг).
 *
 * <p><b>На тропе записи маппер держит ровно один переход — message →
 * domain.</b> Саму вставку он не обслуживает и не будет: она нативная и
 * поглощает конфликт по ключу дедупа
 * (docs/components/AuditEventListener.md §«Форма вставки — поглощающая
 * конфликт по ключу»), переносить между слоями там нечего. Прежде этого
 * перехода не было вовсе: доменную строку собирал чтец конверта прямо из
 * записи брокера, то есть слой сообщения у сервиса отсутствовал
 * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»).
 *
 * <p><b>Цепочка не срезается, хотя формы и близки.</b> Слой persistence
 * знает ключ базы, слой api его не видит вовсе — наружу идёт идентичность
 * события (.claude/rules/codestyle.md §«Идентичность наружу»); доменная
 * форма стои́т между ними, и прямой переход persistence → api сделал бы её
 * необязательной.
 *
 * <p><b>Непокрытые цели гасятся политикой маппера, а не списком
 * {@code ignore}</b>: перечисление совпадающих полей по одному было бы
 * вторым носителем того же решения.
 */
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AuditRecordMapper {

    /**
     * Вопрос читателя → доменная форма вопроса.
     *
     * <p><b>Тенант приезжает ОТДЕЛЬНЫМ операндом, а не полем формы
     * запроса:</b> его ставит периметр заголовком контекста, и в строке
     * запроса его нет вовсе (docs/architecture/contracts.md §«Контекст
     * тенанта в вызове»).
     */
    @Mapping(target = "tenantId", source = "tenantId")
    JournalQuery apiToDomain(JournalReadApiQuery query, String tenantId);

    /**
     * Прочитанное сообщение → доменная строка журнала.
     *
     * <p><b>Момент приёма ставится здесь</b> — его писателем объявлен код
     * приёма, а не конверт и не база: на проводе его не было
     * (docs/models/domain/other/AuditRecord.md).
     *
     * <p><b>Непокрытая цель здесь ОШИБКА, а не умолчание.</b> Политика
     * маппера гасит непокрытое под выборки чтения, где часть полей
     * законно пуста; у строки журнала пустое поле есть потерянный факт, и
     * замечен он был бы только чтением через месяцы. Поэтому у одного
     * этого перехода политика ужесточена.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    @Mapping(target = "recordedAt",
            expression = "java(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))")
    AuditRecord messageToDomain(AuditEventMessage message);

    /**
     * Строка базы → доменная форма.
     *
     * <p>Ключ базы в доменную форму не переносится: он деталь хранения, а
     * идентичность строки — идентичность события.
     */
    AuditRecord persistenceToDomain(AuditRecordEntity entity);

    /**
     * Строка журнала → форма ответа поверхности.
     *
     * <p>Момент приёма наружу идёт вместе с моментом происшествия: оси
     * времени разведены конвертом, и клейм полноты высказывается на одной
     * из них, а строки упорядочены по другой
     * (docs/models/domain/other/AuditRecord.md §«Позиция чтения и с какого
     * момента журнал полон»).
     */
    AuditRecordApiResponse domainToApi(AuditRecord record);

    /**
     * Объявленная полнота → форма ответа поверхности.
     *
     * <p><b>Метод объявлен, а не оставлен генератору, потому что у формы два
     * потребителя:</b> обе выборки чтения обязаны отдавать полноту с каждой
     * выдачей чисел, и соседний маппер берёт этот перенос через
     * {@code uses}, а не описывает свой
     * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»;
     * .claude/rules/policy-home.md).
     */
    JournalCompletenessApiResponse domainToApi(JournalCompleteness completeness);

    /**
     * Страница вместе с позицией продолжения и объявленной полнотой.
     *
     * <p><b>Форма ответа собирается ОДНИМ переходом, а не по частям в
     * контроллере:</b> ручного маппинга у проекта нет
     * (.claude/rules/codestyle.md §Маппинг), и собранная руками оболочка
     * была бы им — включая пустоты, которые обязаны уезжать пустотами
     * (позиции продолжения нет, границы полноты нет).
     */
    JournalPageApiResponse domainToApi(JournalPage page);
}
