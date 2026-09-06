package com.example.bff.mapping;

import com.example.bff.api.model.stream.AnomalyReportedStreamApiModel;
import com.example.bff.api.model.stream.DealClosedStreamApiModel;
import com.example.bff.api.model.stream.DealOpenedStreamApiModel;
import com.example.bff.api.model.stream.HoldRaisedStreamApiModel;
import com.example.bff.api.model.stream.OrderDecidedStreamApiModel;
import com.example.bff.api.model.stream.StrategyActivatedStreamApiModel;
import com.example.bff.api.model.stream.StrategyLifecycleStreamApiModel;
import com.example.tradingbot.domain.event.AnomalyReportedContent;
import com.example.tradingbot.domain.event.DealClosedContent;
import com.example.tradingbot.domain.event.DealOpenedContent;
import com.example.tradingbot.domain.event.HoldRaisedContent;
import com.example.tradingbot.domain.event.OrderDecidedContent;
import com.example.tradingbot.domain.event.StrategyActivatedContent;
import com.example.tradingbot.domain.event.StrategyLifecycleContent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Перевод содержимого события в форму периметра.
 *
 * <p><b>Это и есть граница, ради которой правило заведено.</b> Доменный
 * класс наружу не выходит: у rich-модели предикаты — методы, и
 * сериализатор вывел бы их полями, а браузер обопрётся на то, что
 * увидит (docs/architecture/contracts.md §«Форма на проводе к браузеру —
 * своя, а не доменный класс»).
 *
 * <p><b>Активация — единственный класс, где перевод не 1:1.</b> Её
 * содержимое несёт снимок дерева целиком (он нужен ядру, которое
 * дочитать его не может); браузеру уходят идентичности, а дерево он
 * дочитывает у владельца через проксируемую поверхность.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StreamEventMapper {

    OrderDecidedStreamApiModel toApi(OrderDecidedContent content);

    DealOpenedStreamApiModel toApi(DealOpenedContent content);

    DealClosedStreamApiModel toApi(DealClosedContent content);

    HoldRaisedStreamApiModel toApi(HoldRaisedContent content);

    AnomalyReportedStreamApiModel toApi(AnomalyReportedContent content);

    StrategyLifecycleStreamApiModel toApi(StrategyLifecycleContent content);

    @Mapping(target = "strategyInternalId", source = "definition.internalId")
    @Mapping(target = "exchangeAccountInternalId", source = "definition.exchangeAccountInternalId")
    @Mapping(target = "instrumentInternalId", source = "definition.instrumentInternalId")
    @Mapping(target = "name", source = "definition.name")
    StrategyActivatedStreamApiModel toApi(StrategyActivatedContent content);
}
