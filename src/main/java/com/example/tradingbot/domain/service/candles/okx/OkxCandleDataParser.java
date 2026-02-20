package com.example.tradingbot.domain.service.candles.okx;

import com.example.tradingbot.client.model.okx.CandleResponse;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class OkxCandleDataParser {

    private static final int EXPECTED_FIELDS_COUNT = 9;

    public List<ClientCandle> parse(List<CandleResponse> source) {
        if (Objects.isNull(source) || source.isEmpty()) {
            return List.of();
        }

        return source.stream()
            .map(this::parseSingle)
            .sorted(Comparator.comparingLong(ClientCandle::getTimestampMillis))
            .toList();
    }

    private ClientCandle parseSingle(CandleResponse source) {
        validateExpectedShape(source);
        return ClientCandle.builder()
            .timestampMillis(parseLong(source.getTs(), "timestamp"))
            .open(parseDecimal(source.getOpen(), "open"))
            .high(parseDecimal(source.getHigh(), "high"))
            .low(parseDecimal(source.getLow(), "low"))
            .close(parseDecimal(source.getClose(), "close"))
            .volume(parseDecimal(source.getVolume(), "volume"))
            .volumeCurrency(parseDecimal(source.getVolumeCurrency(), "volumeCurrency"))
            .volumeCurrencyQuote(parseDecimal(source.getVolumeCurrencyQuote(), "volumeCurrencyQuote"))
            .build();
    }

    private void validateExpectedShape(CandleResponse source) {
        if (Objects.isNull(source)) {
            throw new IllegalArgumentException("OKX candle item cannot be null");
        }

        int presentFields = 0;
        if (Objects.nonNull(source.getTs())) {
            presentFields++;
        }
        if (Objects.nonNull(source.getOpen())) {
            presentFields++;
        }
        if (Objects.nonNull(source.getHigh())) {
            presentFields++;
        }
        if (Objects.nonNull(source.getLow())) {
            presentFields++;
        }
        if (Objects.nonNull(source.getClose())) {
            presentFields++;
        }
        if (Objects.nonNull(source.getVolume())) {
            presentFields++;
        }
        if (Objects.nonNull(source.getVolumeCurrency())) {
            presentFields++;
        }
        if (Objects.nonNull(source.getVolumeCurrencyQuote())) {
            presentFields++;
        }
        if (Objects.nonNull(source.getConfirm())) {
            presentFields++;
        }

        if (presentFields != EXPECTED_FIELDS_COUNT) {
            throw new IllegalArgumentException("Unexpected OKX candle payload size: " + presentFields);
        }
    }

    private long parseLong(String rawValue, String fieldName) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Cannot parse " + fieldName + " value: " + rawValue, exception);
        }
    }

    private BigDecimal parseDecimal(String rawValue, String fieldName) {
        try {
            return new BigDecimal(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Cannot parse " + fieldName + " value: " + rawValue, exception);
        }
    }
}
