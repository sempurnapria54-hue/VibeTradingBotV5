package com.example.tradingcore.domain.service;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.integration.AuthReadClient;
import com.example.tradingcore.integration.MarketDataReadClient;
import com.example.tradingcore.integration.PeerServiceUnavailableException;
import com.example.tradingcore.integration.model.ExchangeAccountAuthResponse;
import com.example.tradingcore.integration.model.InstrumentMarketDataResponse;
import com.example.tradingcore.mapping.ExchangeAccountMapper;
import com.example.tradingcore.mapping.InstrumentMapper;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * Сводит проекцию реестра счетов с {@code auth} и заводит место под
     * числа риск-аппетита тенантов, у которых счёт появился.
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
        for (ExchangeAccountAuthResponse response : registered) {
            ExchangeAccount account = accountMapper.integrationToDomain(response);
            accountDataService.upsertProjection(account, projectedAt);
        }
        accountDataService.findTenantInternalIds().forEach(riskAppetiteDataService::ensureRow);
        return registered.size();
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
     * <p><b>Отказ по одному инструменту не двигает его метку и не роняет
     * проход.</b> Строка остаётся со старым снимком, то есть сама себя
     * показывает гейту свежести. Недоступность владельца — исход другого
     * класса: она прекращает проход целиком, потому что следующие
     * четыреста вызовов дадут тот же отказ
     * (docs/rules/runtime-error-classification.md §«Отказ соседа по ярусу
     * — свой класс, и сделку в ошибку он не уводит»).
     *
     * @param projectedAt момент снимка
     * @return число сведённых строк
     */
    public Integer synchronizeInstruments(OffsetDateTime projectedAt) {
        List<InstrumentMarketDataResponse> listed = marketDataReadClient.getInstruments();
        if (isEmpty(listed)) {
            log.warn("Instrument catalogue listing is empty");
            return 0;
        }
        Integer projected = 0;
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
        return projected;
    }

    private void projectInstrument(InstrumentMarketDataResponse response, OffsetDateTime projectedAt) {
        Instrument instrument = instrumentMapper.integrationToDomain(response);
        InstrumentExternalRules rules = marketDataReadClient.getInstrumentRules(response.getInternalId());
        instrumentDataService.upsertProjection(instrument, rules, projectedAt);
    }
}
