package com.example.tradingcore.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingcore.persistence.model.AlgoOrderEntity;
import java.math.BigDecimal;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг отдельной условной заявки domain ↔ persistence
 * (docs/models/mapping/AlgoOrder.md).
 *
 * <p>Дерево условия и перечень идентификаторов порождённых заявок едут
 * через {@link RuntimeJsonConverter}: в колонке они лежат навесом, и их
 * разбор — не перенос полей.
 *
 * <p>Конвертер приезжает конструктором, а не полем: маппер с навесом
 * собирается и вне контейнера — в тесте, который проверяет обе половины
 * навеса.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = RuntimeJsonConverter.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AlgoOrderMapper {

    AlgoOrderEntity domainToPersistence(AlgoOrder algoOrder);

    AlgoOrder persistenceToDomain(AlgoOrderEntity entity);

    /**
     * Перенос добытых фактов площадки на нашу условную заявку.
     *
     * <p>Не переносятся: наш клиентский идентификатор и связи строки;
     * сторона и признак «только уменьшать» — у добытой копии это эхо, операнд
     * сверки, а на строке наше намерение, и перенос стёр бы то, с чем эхо
     * сверяется (docs/models/mapping/AlgoOrder.md §«Сверка эха»); дерево
     * условия целиком — в нём наша декларация, а эхо её уровней и баз либо
     * не переносится, либо сверяется; статус и причина закрытия — их
     * назначает исполнитель доменным переходом
     * (docs/rules/external-status-resolution.md); наблюдённая живость — её
     * пишет исполнитель по итогу всего цикла добычи, а не по одной записи
     * (docs/components/RefreshAlgoOrderExecutor.md).
     *
     * <p>Из условия на строку садится ровно одно поле — наблюдённый уровень
     * трейлинга ({@link #transferObservedTrailingLevel}).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "direction", ignore = true)
    @Mapping(target = "positionReducingOnly", ignore = true)
    @Mapping(target = "condition", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "closeReason", ignore = true)
    @Mapping(target = "externalLive", ignore = true)
    void updateFromFetched(AlgoOrder fetched, @MappingTarget AlgoOrder algoOrder);

    /**
     * Наблюдённый уровень трейлинга — единственное поле условия, которое эхо
     * переносит на строку, и только непустым: без него трейлинг не несёт
     * действующего уровня и покрытием не считается
     * (docs/spec/protection-coverage.json, величина
     * {@code carriesActiveStopLevel}), а четвёртое число сделки его уровня не
     * видит.
     *
     * <p><b>Ветвь трейлинга переносом не заводится:</b> у заявки триггерного
     * типа её нет, и уровень, пришедший эхом, на такую строку не садится —
     * иначе в навесе появилась бы вторая ветвь условия
     * (docs/models/mapping/AlgoOrder.md).
     */
    @AfterMapping
    default void transferObservedTrailingLevel(AlgoOrder fetched, @MappingTarget AlgoOrder algoOrder) {
        BigDecimal observed = isNull(fetched) || isNull(fetched.getCondition())
                || isNull(fetched.getCondition().getTrailing())
                ? null
                : fetched.getCondition().getTrailing().getExternalPrice();
        if (isNull(observed) || isNull(algoOrder.getCondition()) || isNull(algoOrder.getCondition().getTrailing())) {
            return;
        }
        algoOrder.getCondition().getTrailing().setExternalPrice(observed);
    }
}
