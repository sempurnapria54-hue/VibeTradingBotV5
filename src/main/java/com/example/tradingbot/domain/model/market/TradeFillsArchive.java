package com.example.tradingbot.domain.model.market;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFillsArchive extends Auditable {

    /**
     * Ссылка на файл архива исполнений.
     */
    private String fileHref;
    /**
     * Статус подготовки архива на бирже.
     */
    private String state;
    /**
     * Временная метка ответа биржи.
     */
    private String timestamp;
    /**
     * Код статуса ответа биржи.
     */
    private String externalStatusCode;
    /**
     * Текст сообщения/ошибки ответа биржи.
     */
    private String externalStatusMessage;
}
