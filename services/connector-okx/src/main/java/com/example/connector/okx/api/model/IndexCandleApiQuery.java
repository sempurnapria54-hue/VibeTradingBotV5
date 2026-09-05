package com.example.connector.okx.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** Свеча индекса на конкретный момент. */
@Getter
@Setter
public class IndexCandleApiQuery {

    @Schema(description = "Идентификатор индекса на площадке")
    private String indexInstrumentId;

    @Schema(description = "Таймфрейм в словаре площадки")
    private String externalBar;

    @Schema(description = "Момент, на который нужна свеча; время UTC (docs/rules/time-utc.md)")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime at;
}
