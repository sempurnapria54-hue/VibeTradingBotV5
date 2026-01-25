package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.rest.model.OkxErrorResponse;
import com.example.tradingbot.rest.model.OkxRestResponse;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class OkxProxyResponseHandler {
    public <C, D, R> ResponseEntity<?> handleResponse(
            OkxApiResponse<C> response,
            Function<C, D> clientToDomain,
            Function<D, R> domainToRest
    ) {
        if (response == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new OkxErrorResponse("OKX_EMPTY_RESPONSE", "Empty response from OKX"));
        }
        if (!"0".equals(response.getCode())) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new OkxErrorResponse(response.getCode(), response.getMsg()));
        }
        List<D> domainData = response.getData() == null
                ? List.of()
                : response.getData().stream().map(clientToDomain).toList();
        List<R> restData = domainData.stream().map(domainToRest).toList();
        OkxRestResponse<R> restResponse = new OkxRestResponse<>();
        restResponse.setCode(response.getCode());
        restResponse.setMsg(response.getMsg());
        restResponse.setInTime(response.getInTime());
        restResponse.setOutTime(response.getOutTime());
        restResponse.setData(restData);
        return ResponseEntity.ok(restResponse);
    }

    public ResponseEntity<OkxErrorResponse> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new OkxErrorResponse("OKX_HTTP_ERROR", ex.getMessage()));
    }
}
