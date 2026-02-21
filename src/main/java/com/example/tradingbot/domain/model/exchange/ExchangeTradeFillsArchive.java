package com.example.tradingbot.domain.model.exchange;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExchangeTradeFillsArchive {

    /** Ссылка на файл архива исполнений. */
    private String fileHref;
    /** Статус подготовки архива на бирже. */
    private String state;
    /** Временная метка ответа биржи. */
    private String timestamp;
    /** Код статуса ответа биржи. */
    private String code;
    /** Текст сообщения/ошибки ответа биржи. */
    private String message;
}
