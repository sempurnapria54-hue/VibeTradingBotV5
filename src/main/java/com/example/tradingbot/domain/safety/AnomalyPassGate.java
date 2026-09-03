package com.example.tradingbot.domain.safety;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.config.AnomalyJobProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детектор `A10` — <b>проход добыт не целиком</b> — и предел слепоты.
 *
 * <p><b>Предмет — неполнота ПРОХОДА, а не отказ вызова.</b> Отказ вызова,
 * нарушающий контракт интеграции, поднимает биржевую ступень 2 сам и до
 * джобы не доходит (docs/rules/controlled-exchange-exceptions.md). Сюда
 * приходит класс, до которого граница не достаёт, потому что бросает на
 * вызов, а не на проход: два среза из трёх получены, третий — нет.
 *
 * <p><b>Слепота счётна.</b> Отчёт заводится с первого же неполного
 * прохода: «ничего не нашли» и «не смотрели» обязаны быть различимы в
 * данных (П3). Дедуп по стоящему состоянию держит строку одной, пока
 * слепота держится.
 *
 * <p><b>Предел мягкий, и это следствие оси, а не смягчение.</b> Слепота
 * нашего наблюдения посылку «защита стоит на бирже и исполняется ею
 * независимо от нашей интеграции» не нарушает — стопы стоя́т и работают,
 * пока мы их не видим. Принятый риск покрыт, снимать его нечем; под
 * сомнением право НАБИРАТЬ новый вслепую. Жёсткая ступень здесь снимала
 * бы покрытый риск по рынку из-за отказа канала
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyPassGate {

    private final ExchangeDataService exchangeDataService;
    private final AnomalyReportService reportService;
    private final HoldService holdService;
    private final AnomalyJobProperties properties;

    /**
     * Отметить проход и, если слепота держится дольше предела, поднять
     * мягкую биржевую ступень. Возвращает {@code true}, если проход полон.
     */
    public Boolean apply(AnomalyScan scan, Exchange exchange) {
        Integer blindPasses = exchangeDataService.markPass(exchange.getId(), scan.getComplete());
        if (isTrue(scan.getComplete())) {
            return true;
        }
        DealContext context = DealContext.builder().exchange(exchange).build();
        log.warn("[anomaly] проход неполон exchangeId={} подряд={}", exchange.getId(), blindPasses);
        if (blindPasses >= properties.getBlindPassLimit()) {
            holdService.raise(HoldSignal.exchangeSoft(Constants.Hold.ANOMALY_PASS_INCOMPLETE), context);
            return false;
        }
        reportService.journal(context, HoldSignal.exchangeJournal(Constants.Hold.ANOMALY_PASS_INCOMPLETE));
        return false;
    }
}
