package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxInstrumentDto;
import com.example.tradingbot.mapping.InstrumentMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5/public")
public class OkxPublicProxyController {
    private final OkxRestClient okxRestClient;
    private final OkxProxyResponseHandler responseHandler;
    private final InstrumentMapper instrumentMapper;

    public OkxPublicProxyController(
            OkxRestClient okxRestClient,
            OkxProxyResponseHandler responseHandler,
            InstrumentMapper instrumentMapper
    ) {
        this.okxRestClient = okxRestClient;
        this.responseHandler = responseHandler;
        this.instrumentMapper = instrumentMapper;
    }

    @GetMapping("/instruments")
    public ResponseEntity<?> getInstruments(@RequestParam String instType, @RequestParam(required = false) String instId) {
        try {
            OkxApiResponse<OkxInstrumentDto> response = okxRestClient.getInstruments(instType, instId);
            return responseHandler.handleResponse(response, instrumentMapper::clientToDomain, instrumentMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }
}
