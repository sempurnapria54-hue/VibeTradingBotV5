package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class Auditable {

    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime modifiedAt;
    private String modifiedBy;
    private OffsetDateTime externalCreatedAt;
    private OffsetDateTime externalModifiedAt;
}
