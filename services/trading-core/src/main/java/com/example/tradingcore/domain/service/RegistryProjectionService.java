package com.example.tradingcore.domain.service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.example.platform.exception.PeerServiceUnavailableException;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.config.ProjectionSyncProperties;
import com.example.tradingcore.integration.internal.api.AuthReadClient;
import com.example.tradingcore.integration.internal.api.MarketDataReadClient;
import com.example.tradingcore.integration.internal.api.model.ExchangeAccountAuthResponse;
import com.example.tradingcore.integration.internal.api.model.InstrumentMarketDataResponse;
import com.example.tradingcore.mapping.ExchangeAccountMapper;
import com.example.tradingcore.mapping.InstrumentMapper;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Сводит проекции чужих реестров с их владельцами: реестр счетов у
 * {@code auth}, каталог инструментов у {@code market-data}
 * (docs/architecture/data-ownership.md §«Копии чужих данных»).
 *
 * <p><b>Проекция не становится источником решения и второго писателя не
 * заводит:</b> её строки правит только этот синк, прикладной код их
 * читает.
 *
 * <p><b>Исчезнувшая у владельца строка из проекции НЕ удаляется.</b> На
 * счёт ссылаются торговые строки ядра числовым ключом, и удаление
 * оборвало бы их; на исчезнувший счёт просто не начинается ни одного
 * прохода — его отсутствие в реестре и есть ответ
 * (docs/models/domain/core/Instrument.md §«Проекция реестра счетов гейтом
 * не меряется, и это названо»). Снятый с листинга инструмент остаётся
 * строкой со СТАРЫМ моментом снимка — то есть операндом гейта свежести,
 * который и отвергнет вход по нему.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryProjectionService {

    private final AuthReadClient authReadClient;
    private final MarketDataReadClient marketDataReadClient;
    private final ExchangeAccountDataService accountDataService;
    private final InstrumentDataService instrumentDataService;
    private final TenantRiskAppetiteDataService riskAppetiteDataService;
    private final ExchangeAccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final ProjectionSyncProperties properties;

    /**
     * Сводит проекцию реестра счетов с {@code auth} и заводит место под
     * числа риск-аппетита тенантов, у которых счёт появился.
     *
     * <p>Отказ одной строки стоит одну строку, отказ чтения реестра — проход
     * (docs/components/RegistryProjectionJob.md §«Отказ одной строки стоит
     * одну строку»).
     *
     * @param projectedAt момент снимка
     * @return число сведённых строк
     */
    public Integer synchronizeExchangeAccounts(OffsetDateTime projectedAt) {
        List<ExchangeAccountAuthResponse> registered = authReadClient.getExchangeAccounts();
        if (isEmpty(registered)) {
            log.warn("Exchange account registry is empty: no pass will start");
            return 0;
        }
        Integer projected = 0;
        for (ExchangeAccountAuthResponse response : registered) {
            try {
                projectAccount(response, projectedAt);
                projected++;
            } catch (RuntimeException e) {
                log.error("Exchange account projection failed for {}", response.getInternalId(), e);
            }
        }
        accountDataService.findTenantInternalIds().forEach(riskAppetiteDataService::ensureRow);
        return projected;
    }

    /**
     * Сводит проекцию каталога с {@code market-data}: спецификация из
     * листинга плюс справочные правила каждой строки.
     *
     * <p><b>Правила читаются в том же проходе, что и спецификация, а не
     * окном за курсором.</b> Момент снимка обязан описывать строку
     * целиком: у владельца каталога окно оправдано лимитом ПЛОЩАДКИ,
     * который делится с невосполнимым сбором
     * (docs/components/InstrumentSyncJob.md), а здесь чтение внутрикластерное
     * и такого бюджета не тратит. Разведи их — и одна метка описывала бы
     * свежую спецификацию при правилах недельной давности.
     *
     * <p>Отказ по одному инструменту стоит один инструмент, недоступность
     * владельца — проход (docs/components/RegistryProjectionJob.md §«Отказ
     * одной строки стоит одну строку»).
     *
     * <p><b>Листинг обходится окнами за курсором</b>: целиком владелец его
     * не отдаёт (docs/models/domain/core/Instrument.md §«Проекция у
     * торгового ядра»). Окно сводится прежде, чем читается следующее.
     *
     * @param projectedAt момент снимка
     * @return число сведённых строк
     */
    public Integer synchronizeInstruments(OffsetDateTime projectedAt) {
        Integer window = properties.getListingWindow();
        List<InstrumentMarketDataResponse> listed = marketDataReadClient.getInstruments(null, window);
        if (isEmpty(listed)) {
            log.warn("Instrument catalogue listing is empty");
            return 0;
        }
        Integer projected = 0;
        String after = null;
        while (isNotEmpty(listed)) {
            for (InstrumentMarketDataResponse response : listed) {
                try {
                    projectInstrument(response, projectedAt);
                    projected++;
                } catch (PeerServiceUnavailableException e) {
                    log.error("Instrument projection stopped: catalogue owner is unavailable", e);
                    return projected;
                } catch (RuntimeException e) {
                    log.error("Instrument projection failed for {}", response.getInternalId(), e);
                }
            }
            if (isLastWindow(listed, window, after)) {
                return projected;
            }
            after = listed.getLast().getInternalId();
            listed = marketDataReadClient.getInstruments(after, window);
        }
        return projected;
    }

    /**
     * Окно короче предела — последнее. Окно, чей курсор не сдвинулся, —
     * тоже: владелец курсора не исполнил, и повтор того же окна зациклил
     * бы тик.
     */
    private Boolean isLastWindow(List<InstrumentMarketDataResponse> listed, Integer window, String after) {
        return listed.size() < window || Objects.equals(listed.getLast().getInternalId(), after);
    }

    private void projectAccount(ExchangeAccountAuthResponse response, OffsetDateTime projectedAt) {
        ExchangeAccount account = accountMapper.integrationToDomain(response);
        accountDataService.upsertProjection(account, projectedAt);
    }

    private void projectInstrument(InstrumentMarketDataResponse response, OffsetDateTime projectedAt) {
        Instrument instrument = instrumentMapper.integrationToDomain(response);
        InstrumentExternalRules rules = marketDataReadClient.getInstrumentRules(response.getInternalId());
        instrumentDataService.upsertProjection(instrument, rules, projectedAt);
    }
}
