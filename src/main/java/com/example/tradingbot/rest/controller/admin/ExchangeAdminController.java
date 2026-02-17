package com.example.tradingbot.rest.controller.admin;

import com.example.tradingbot.domain.model.admin.Exchange;
import com.example.tradingbot.domain.service.admin.ExchangeAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/exchanges")
@RequiredArgsConstructor
public class ExchangeAdminController {

    private final ExchangeAdminService exchangeAdminService;

    @PostMapping
    public Exchange createExchange(@RequestBody Exchange exchange) {
        return exchangeAdminService.createExchange(exchange);
    }

    @GetMapping
    public List<Exchange> list() {
        return exchangeAdminService.list();
    }
}
