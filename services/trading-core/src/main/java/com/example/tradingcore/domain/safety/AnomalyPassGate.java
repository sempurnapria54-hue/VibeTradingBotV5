package com.example.tradingcore.domain.safety;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.config.AnomalyJobProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.util.Constants;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Детектор «проход добыт не целиком» и предел слепоты
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
 *
 * <p><b>Предмет — неполнота ПРОХОДА, а не отказ вызова.</b> Отказ,
 * нарушающий контракт интеграции, поднимает биржевую ступень 2 своим
 * ловцом у джобы, и этот гейт ему не реакция, а только отметка: такой
 * проход тоже ненаблюдён. Своя реакция здесь — у класса, до которого граница
 * не достаёт, потому что бросает на вызов, а не на проход: два среза из трёх
 * получены, третий — нет.
 *
 * <p><b>Слепота счётна.</b> Отчёт заводится с первого же неполного
 * прохода: «ничего не нашли» и «не смотрели» обязаны быть различимы в
 * данных. Дедуп по стоящему состоянию держит строку одной, пока слепота
 * держится.
 *
 * <p><b>Предел мягкий, и это следствие оси, а не смягчение.</b> Слепота
 * нашего наблюдения посылку «защита стоит на бирже и исполняется ею
 * независимо от нашей интеграции» не нарушает — стопы стоя́т и работают,
 * пока мы их не видим. Принятый риск покрыт, снимать его нечем; под
 * сомнением право НАБИРАТЬ новый вслепую.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyPassGate {

    private final ExchangeAccountDataService exchangeAccountDataService;
    private final AnomalyReportService reportService;
    private final HoldService holdService;
    private final AnomalyJobProperties properties;

    /**
     * Отметить проход и, если слепота держится дольше предела, поднять
     * мягкую счётную ступень.
     *
     * <p>Операнд — <b>исход наблюдения</b>, а не полнота среза: проход,
     * чей срез добыт целиком, но детекция по нему не отработала, есть
     * такая же слепота. Различать их значило бы засчитывать чистым
     * проход, на котором никто не смотрел.
     *
     * <p>Наблюдённый проход отмечается своим моментом начала: его возраст —
     * операнд гейта входа, который видит и тик, не исполнившийся вовсе
     * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
     */
    public void apply(Boolean observed, ExchangeAccount account, OffsetDateTime passStartedAt) {
        Integer blindPasses = exchangeAccountDataService.markPass(account.getId(), observed, passStartedAt);
        if (isTrue(observed)) {
            return;
        }
        DealContext context = DealContext.builder().exchangeAccount(account).build();
        log.warn("Anomaly pass is incomplete exchangeAccountId={} blindPasses={}",
                account.getId(), blindPasses);
        if (blindPasses >= properties.getBlindPassLimit()) {
            holdService.raise(HoldSignal.exchangeAccountSoft(Constants.Hold.ANOMALY_PASS_INCOMPLETE),
                    context);
            return;
        }
        reportService.journalState(context,
                HoldSignal.exchangeAccountJournal(Constants.Hold.ANOMALY_PASS_INCOMPLETE), null);
    }
}
