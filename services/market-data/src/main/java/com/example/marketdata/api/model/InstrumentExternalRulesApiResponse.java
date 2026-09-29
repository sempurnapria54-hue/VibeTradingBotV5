package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Справочные правила инструмента наружу.
 *
 * <p><b>Своя форма, а не доменная модель на проводе</b>
 * (.claude/rules/codestyle.md §Маппинг): вычисляемые ответы доменной модели
 * наружу не едут, а числовой ключ инструмента заменён идентичностью
 * (§«Идентичность наружу»). Перечни идут строкой — их значения объявляет
 * доменный слой (§«Слои моделей и enum'ы»).
 *
 * <p><b>Значения площадки идут её строками, без разбора в число:</b> навес
 * хранит их так, как площадка их отдала
 * (docs/models/domain/other/InstrumentExternalRules.md).
 *
 * <p><b>Ставки комиссии в форме нет, и изъятие держит сама форма, а не
 * маппер на тропе.</b> Ставка — атрибут комиссионного уровня счёта
 * (docs/models/domain/other/InstrumentExternalRules.md §«Ставка комиссии»),
 * и наружу едет только ключ её группы. Прежде наружу уходила доменная
 * форма, и поле держалось вне ответа лишь тем, что у этого сервиса его
 * никто не наливает.
 */
@Getter
@Setter
public class InstrumentExternalRulesApiResponse {

    @Schema(description = "Инструмент, чьи правила сняты")
    private String instrumentInternalId;

    @Schema(description = "Тип инструмента в нашем словаре")
    private String instrumentType;

    @Schema(description = "Тип контракта в нашем словаре")
    private String contractType;

    @Schema(description = "Состояние торговли инструментом в нашем словаре")
    private String status;

    @Schema(description = "Тип инструмента в словаре площадки")
    private String externalInstrumentType;

    @Schema(description = "Инструмент в словаре площадки")
    private String externalInstrumentId;

    @Schema(description = "Тип контракта в словаре площадки")
    private String externalContractType;

    @Schema(description = "Размер одного контракта")
    private String externalContractValue;

    @Schema(description = "Валюта размера контракта")
    private String externalContractValueCurrency;

    @Schema(description = "Шаг цены")
    private String externalTickSize;

    @Schema(description = "Шаг размера заявки")
    private String externalLotSize;

    @Schema(description = "Наименьший размер заявки")
    private String externalMinSize;

    @Schema(description = "Наибольший размер лимитной заявки")
    private String externalMaxLimitSize;

    @Schema(description = "Наибольший размер рыночной заявки")
    private String externalMaxMarketSize;

    @Schema(description = "Наибольший размер триггерной заявки")
    private String externalMaxTriggerSize;

    @Schema(description = "Наибольший размер стоп-заявки")
    private String externalMaxStopSize;

    @Schema(description = "Наибольшее плечо")
    private String externalMaxLeverage;

    @Schema(description = "Состояние торговли инструментом в словаре площадки")
    private String externalState;

    @Schema(description = "Ключ комиссионной группы; ставку по нему резолвит владелец счёта")
    private String externalFeeGroupId;
}
