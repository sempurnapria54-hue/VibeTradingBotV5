package com.example.tradingbot.rest.model.request.instrument;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateInstrumentRequest {

    private String name;
    private String externalName;
    private String type;
    private Set<String> timeFrames;
}
