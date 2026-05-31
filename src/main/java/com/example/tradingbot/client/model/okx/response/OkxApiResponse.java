package com.example.tradingbot.client.model.okx.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Обёртка ответа OKX REST: {@code code} ("0" — успех), {@code msg} и
 * {@code data} (список элементов типа {@code T}). Сырой DTO источника
 * — за ClientService/adapter не выходит
 * (docs/rules/raw-exchange-dto-boundary.md).
 *
 * @param <T> тип элемента data (объект-DTO или позиционный массив свечи).
 */
@Getter
@Setter
public class OkxApiResponse<T> {

    /** Код ответа OKX: "0" — успех, иначе — ошибка. */
    private String code;

    /** Текст ошибки OKX (при code != "0"). */
    private String msg;

    /** Полезная нагрузка ответа. */
    private List<T> data;
}
