package com.example.tradingbot.rest.controller.admin;

import com.example.tradingbot.domain.model.admin.Instrument;
import com.example.tradingbot.domain.service.admin.InstrumentAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/instruments")
@RequiredArgsConstructor
public class InstrumentAdminController {

    private final InstrumentAdminService instrumentAdminService;

    @PostMapping
    public Instrument createInstrument(@RequestBody Instrument instrument) {
        return instrumentAdminService.createInstrument(instrument);
    }

    @GetMapping
    public List<Instrument> list() {
        return instrumentAdminService.list();
    }

    @GetMapping("/{id}")
    public Instrument get(@PathVariable Long id) {
        return instrumentAdminService.get(id);
    }
}
