package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KillSwitchService {

    public void executeKillSwitch(Deal deal) {

    }

    public void executeTradeRuleViolation(Exchange exchange,
                                          Instrument instrument,
                                          Long dealId,
                                          String code) {
        log.warn("Trade rule violation kill-switch executed. Exchange: {}, instrument: {}, dealId: {}, code: {}",
                 exchange.getName(), instrument.getExchangeId(), dealId, code);
    }
}
