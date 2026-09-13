package com.example.statistics.mapping;

import com.example.statistics.api.model.AggregatePageApiResponse;
import com.example.statistics.api.model.AggregateReadApiQuery;
import com.example.statistics.api.model.ReceptionCompletenessApiResponse;
import com.example.statistics.api.model.DealAggregateApiResponse;
import com.example.statistics.api.model.IncidentAggregateApiResponse;
import com.example.statistics.domain.model.AggregateGrain;
import com.example.statistics.domain.model.AggregatePage;
import com.example.statistics.domain.model.AggregateQuery;
import com.example.statistics.domain.model.DealAggregate;
import com.example.statistics.domain.model.IncidentAggregate;
import com.example.statistics.domain.model.ReceptionCompleteness;
import com.example.statistics.persistence.model.aggregates.DealAggregateEntity;
import com.example.statistics.persistence.model.aggregates.IncidentAggregateEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Переходы агрегатной выборки между слоями: api → domain → persistence и
 * обратно (.claude/rules/codestyle.md §Маппинг).
 *
 * <p><b>Маппер второй у модуля, и предмет у него свой</b> — строки
 * агрегатов двух зёрен. Записи он не обслуживает и не будет: единственная
 * тропа записи агрегата — upsert пересчёта по ключу зерна, и переносить
 * между слоями там нечего
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Форму полноты маппер НЕ описывает, а берёт у соседа</b>
 * ({@code uses = ReceptionStateMapper.class}): величины принадлежат журналу, и
 * второй перенос той же формы разошёлся бы с первым молча
 * (.claude/rules/policy-home.md).
 *
 * <p><b>Зерно разбирается КВАЛИФИЦИРОВАННЫМ методом, а не встроенным
 * преобразованием строки в перечень.</b> Встроенное бросает на неизвестном
 * значении, то есть отвечало бы отказом связывания вместо отказа выборки —
 * с другим кодом и другим текстом. Квалифицированный отдаёт пустоту, и
 * отказ остаётся один (docs/rules/error-handling-policy.md).
 *
 * <p><b>Непокрытые цели гасятся политикой маппера, а не списком
 * {@code ignore}</b>: перечисление совпадающих полей по одному было бы
 * вторым носителем того же решения.
 */
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AggregateMapper {

    /**
     * Вопрос читателя → доменная форма вопроса.
     *
     * <p><b>Тенант приезжает ОТДЕЛЬНЫМ операндом, а не полем формы
     * запроса:</b> его ставит периметр заголовком контекста, и в строке
     * запроса его нет вовсе (docs/architecture/contracts.md §«Контекст
     * тенанта в вызове»).
     */
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "grain", source = "query.grain", qualifiedByName = "resolveGrain")
    AggregateQuery apiToDomain(AggregateReadApiQuery query, String tenantId);

    /**
     * Разбор названного зерна; пусто — не названо либо названо вне перечня.
     *
     * <p>Дом перечня — сам перечень ({@link AggregateGrain#resolve}); здесь
     * только квалификатор, которым маппер выбирает его вместо встроенного
     * преобразования.
     */
    @Named("resolveGrain")
    default AggregateGrain resolveGrain(String grain) {
        return AggregateGrain.resolve(grain);
    }

    /**
     * Строка базы сделочного зерна → доменная форма.
     *
     * <p>Ключ базы в доменную форму не переносится: он деталь хранения, а
     * идентичность строки — её ключ зерна.
     */
    DealAggregate persistenceToDomain(DealAggregateEntity entity);

    /** Строка базы зерна происшествий → доменная форма. */
    IncidentAggregate persistenceToDomain(IncidentAggregateEntity entity);

    /** Строка сделочного зерна → форма ответа поверхности. */
    DealAggregateApiResponse domainToApi(DealAggregate aggregate);

    /** Строка зерна происшествий → форма ответа поверхности. */
    IncidentAggregateApiResponse domainToApi(IncidentAggregate aggregate);

    /**
     * Страница вместе с позицией продолжения и объявленной полнотой.
     *
     * <p><b>Форма ответа собирается ОДНИМ переходом, а не по частям в
     * контроллере:</b> ручного маппинга у проекта нет
     * (.claude/rules/codestyle.md §Маппинг), и собранная руками оболочка
     * была бы им — включая пустоты, которые обязаны уезжать пустотами
     * (перечень чужого зерна, позиция продолжения, граница полноты).
     *
     * <p>Зерно уходит наружу строкой: перевод перечня в имя делает маппер
     * (.claude/rules/codestyle.md §«Слои моделей и enum'ы»).
     */
    /**
     * Свидетельство о полноте — форма api.
     *
     * <p><b>Метод лежит ЗДЕСЬ, а не у соседнего маппера:</b> обе величины
     * едут со строками агрегатов и другого читателя у формы нет
     * (docs/rules/durable-consumer-reception.md §«Выдача чисел несёт СВОИ
     * величины, а не соседские»).
     */
    ReceptionCompletenessApiResponse domainToApi(ReceptionCompleteness completeness);

    AggregatePageApiResponse domainToApi(AggregatePage page);
}
