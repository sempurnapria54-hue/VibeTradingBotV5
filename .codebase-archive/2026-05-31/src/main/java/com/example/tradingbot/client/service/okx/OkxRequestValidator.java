package com.example.tradingbot.client.service.okx;

import com.example.tradingbot.client.model.okx.request.get.GetAlgoOrdersHistorySearchParams;
import org.springframework.stereotype.Service;

import java.util.Set;

import static com.example.tradingbot.util.CollectionUtils.checkContains;
import static com.example.tradingbot.util.NumberUtils.parseIntSafe;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
public class OkxRequestValidator {

    private static final Set<String> OKX_INSTRUMENT_TYPES = Set.of("SPOT", "MARGIN", "SWAP", "FUTURES", "OPTION");
    private static final Set<String> OKX_ALGO_ORDER_TYPES = Set.of("conditional", "oco", "trigger", "move_order_stop");
    private static final Set<String> OKX_ALGO_ORDER_STATES = Set.of("effective", "canceled",
                                                                    "order_failed", "partially_failed");

    public void validate(GetAlgoOrdersHistorySearchParams searchParams) {
        if (isNull(searchParams)) {
            throw new IllegalArgumentException("searchParams must not be null");
        }
        checkContains(searchParams.getExternalAlgoOrderType(), "externalAlgoOrderType", OKX_ALGO_ORDER_TYPES);

        if (isBlank(searchParams.getExternalStatus()) && isBlank(searchParams.getAlgoOrderExternalId())) {
            throw new IllegalArgumentException("Either externalStatus or algoOrderExternalId must be provided");
        }
        if (isNotBlank(searchParams.getExternalStatus())) {
            checkContains(searchParams.getExternalStatus(), "externalStatus", OKX_ALGO_ORDER_STATES);
        }
        if (isNotBlank(searchParams.getInstrumentExternalType())) {
            checkContains(searchParams.getInstrumentExternalType(), "instrumentExternalType", OKX_INSTRUMENT_TYPES);
        }
        if (isNotBlank(searchParams.getLimit())) {
            int parsedLimit = parseIntSafe(searchParams.getLimit(), "limit");
            if (parsedLimit < 0 || parsedLimit > 100) {
                throw new IllegalArgumentException("limit must between 0 and 100");
            }
        }
    }

}
