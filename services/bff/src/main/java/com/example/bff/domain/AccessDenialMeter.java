package com.example.bff.domain;

import com.example.bff.util.Constants;
import com.example.platform.exception.handler.AccessDenialRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/**
 * Писатель следа отказа доступа у периметра — <b>ряд частоты, а не
 * строка</b>.
 *
 * <p><b>Строки у периметра нет по правилу, а не по недостройке.</b> Базы
 * у него нет, и запись через соседа поставила бы сетевой вызов и запись в
 * чужую базу на тропу отказа — ту, объём которой задаёт тот, кто ломится
 * (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого есть
 * база»). Вместо строки правило называет лог и ряд частоты: лог пишет
 * точка входа отказа на каждом отказе, ряд — этот писатель.
 *
 * <p><b>Ряд на класс отказа, и оба заводятся при сборке.</b> Счётчик,
 * заведённый первым отказом, отсутствовал бы в экспозиции до первого
 * отказа, и «рядов нет» у наблюдателя было бы неотличимо от «съём не
 * работает»; заведённый сразу, он отвечает нулём.
 *
 * <p><b>Принципал в метку не идёт.</b> Метка ряда — ограниченное
 * множество, а имя принципала и путь под контролем вызывающего: ряд на
 * каждое значение рос бы без потолка ровно у того, кто ломится.
 */
@Service
public class AccessDenialMeter implements AccessDenialRecorder {

    private final Counter principalAbsent;
    private final Counter operationForbidden;

    public AccessDenialMeter(MeterRegistry meterRegistry) {
        this.principalAbsent = counter(meterRegistry, Constants.AccessDenialMetrics.PRINCIPAL_ABSENT);
        this.operationForbidden = counter(meterRegistry, Constants.AccessDenialMetrics.OPERATION_FORBIDDEN);
    }

    /** Принципал не предъявлен либо предъявленный не принят. */
    @Override
    public void recordPrincipalAbsent(String surface) {
        principalAbsent.increment();
    }

    /** Принципал принят, но операция ему не разрешена. */
    @Override
    public void recordOperationForbidden(String surface, String principal) {
        operationForbidden.increment();
    }

    private static Counter counter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(Constants.AccessDenialMetrics.DENIALS)
                .description("Отказы доступа на поверхности периметра")
                .tag(Constants.AccessDenialMetrics.OUTCOME_TAG, outcome)
                .register(meterRegistry);
    }
}
