package com.example.tradingbot.domain.model.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exchange {

    private Long id;
    private String name;
    private String status;
    private String baseUrl;
}
