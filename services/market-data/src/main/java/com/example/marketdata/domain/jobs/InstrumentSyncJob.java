package com.example.marketdata.domain.jobs;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.marketdata.config.ConnectorProperties;
import com.example.marketdata.config.InstrumentSyncProperties;
import com.example.marketdata.domain.service.InstrumentCatalogService;
import com.example.marketdata.integration.ExchangeAccessException;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ведёт каталог инструментов и их справочный навес
 * (docs/components/InstrumentExternalRulesSyncJob.md): тик сводит каталог
 * с листингом площадки и обновляет правила окном инструментов.
 *
 * <p><b>Листинг и правила читаются разной ценой, и поэтому идут разным
 * охватом.</b> Листинг — агрегатное чтение на тип инструмента, его можно
 * брать целиком каждый тик; правила площадка отдаёт ПОИНСТРУМЕНТНО, и
 * полный обход стоил бы сотен запросов из того же бюджета лимитов, что и
 * сбор срезов, — а срез не добывается потом, правила добываются.
 *
 * <p><b>Ставок комиссий этот тик не собирает.</b> Ставка — атрибут
 * комиссионного уровня СЧЁТА и читается с ключами счёта
 * (docs/models/domain/other/TradeFeeRate.md), а market-data ходит к
 * площадке только публичными чтениями
 * (docs/architecture/contracts.md §«Синхронные вызовы»). Навес несёт
 * ключ комиссионной группы; ставку по нему резолвит тот, у кого счёт
 * есть.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentSyncJob {

    private static final String JOB_NAME = "instrumentSyncJob";

    /** Начало круга обхода: идентификаторы положительны. */
    private static final Long CURSOR_START = 0L;

    /** Статусы, при которых инструмент участвует в обновлении правил. */
    private static final Set<Instrument.Status> SYNCED_STATUSES = Set.of(
            Instrument.Status.SYNC,
            Instrument.Status.CANDLES_LOADING,
            Instrument.Status.ACTIVE);

    private final InstrumentCatalogService catalogService;
    private final InstrumentDataService instrumentDataService;
    private final InstrumentSyncProperties properties;
    private final ConnectorProperties connectorProperties;
    private final JobExecutionGuard executionGuard;

    /** Позиция обхода листинга для обновления правил. */
    private Long rulesCursor = CURSOR_START;

    @Scheduled(cron = "${instrument-sync.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        if (isTrue(syncListing())) {
            syncRules();
        }
    }

    /**
     * Сводит каталог с листингом по каждому типу инструмента.
     *
     * <p>Отказ доступа или лимита ПРЕКРАЩАЕТ тик: под исчерпанным лимитом
     * следующие чтения тратят бюджет, которым пользуется и сбор
     * невосполнимых срезов (docs/processes/snapshot-collection.md §«Отказ
     * на проходе»).
     *
     * @return прошёл ли листинг без отказа доступа; под отказом второй
     *         половине тика идти незачем.
     */
    private Boolean syncListing() {
        for (String instrumentType : connectorProperties.getInstrumentTypes()) {
            try {
                catalogService.synchronizeListing(instrumentType);
            } catch (ExchangeAccessException e) {
                log.error("Instrument sync tick stopped on listing: exchange refused access or limit", e);
                return false;
            } catch (RuntimeException e) {
                log.error("Instrument listing sync failed for instType={}", instrumentType, e);
            }
        }
        return true;
    }

    /**
     * Обновляет правила ОКНОМ ЗА КУРСОРОМ, а не первыми N инструментами.
     *
     * <p>Окно от начала обновляло бы вечно один и тот же префикс листинга,
     * и у хвоста правил не появилось бы никогда. Курсор идёт по кругу:
     * пустое окно означает, что круг пройден, и следующий тик начинает
     * заново.
     *
     * <p>Курсор держится в памяти: при рестарте обход начинается сначала —
     * цена названа и она меньше, чем колонка состояния, которую читает
     * только этот тик.
     */
    private void syncRules() {
        List<Instrument> instruments = instrumentDataService.findListedAfter(
                connectorProperties.getExchangeCode(), SYNCED_STATUSES, rulesCursor,
                properties.getRulesBatchSize());
        if (isEmpty(instruments)) {
            rulesCursor = CURSOR_START;
            return;
        }
        for (Instrument instrument : instruments) {
            try {
                catalogService.synchronizeRules(instrument);
            } catch (ExchangeAccessException e) {
                log.error("Instrument rules sync stopped: exchange refused access or limit", e);
                return;
            } catch (RuntimeException e) {
                log.error("Instrument rules sync failed for {}", instrument.getId(), e);
            }
        }
        rulesCursor = instruments.get(instruments.size() - 1).getId();
    }
}
