package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Сделка, какой её видит читатель поверхности.
 *
 * <p><b>Идентичности — {@code internalId}, включая связанные:</b>
 * числовой ключ границу сервиса не пересекает
 * (.claude/rules/codestyle.md §«Идентичность наружу»).
 *
 * <p><b>Чисел риска и графа заявок здесь нет.</b> Они операнды решений
 * ядра, а не состояние, за которым читатель приходит; отдавать их наружу
 * значило бы приглашать считать по ним второй раз.
 */
@Getter
@Setter
public class DealApiResponse {

    @Schema(description = "Идентичность сделки")
    private String internalId;

    @Schema(description = "Идентичность биржевого счёта — корневая торговая строка")
    private String exchangeAccountInternalId;

    @Schema(description = "Идентичность инструмента")
    private String instrumentInternalId;

    @Schema(description = "Статус сделки")
    private String status;

    @Schema(description = "Сторона сделки")
    private String direction;

    @Schema(description = "Причина заведения: вход по стратегии либо восстановление живого риска")
    private String entryReason;

    @Schema(description = "Причина выхода из штатного ведения; пусто — сделка вышла не холдом")
    private String shutdownReason;

    @Schema(description = "Итоговая бизнес-причина закрытия; пусто — сделка не закрыта")
    private String closeReason;

    @Schema(description = "Посчитанный итог сделки; пусто — число не посчитано")
    private BigDecimal resultProfit;

    @Schema(description = "Валюта итога; пусто — валюта не резолвилась")
    private String resultProfitCurrency;

    @Schema(description = "Биржевой момент создания сделки, UTC")
    private OffsetDateTime externalCreatedAt;

    @Schema(description = "Транши сделки — по одному на объявление и на уровень шаблона")
    private List<DealTrancheApiResponse> tranches;
}
