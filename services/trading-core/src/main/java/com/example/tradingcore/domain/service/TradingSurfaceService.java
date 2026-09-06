package com.example.tradingcore.domain.service;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.domain.model.PairCheck;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Прикладной слой поверхности ядра: чтения торгового состояния и
 * назначение чисел риск-аппетита.
 *
 * <p><b>Отдаёт и принимает ДОМЕННЫЕ модели.</b> Перевод api ↔ domain
 * делает контроллер маппером — это граница слоёв, и сервис её не
 * пересекает (.claude/rules/codestyle.md §Слои).
 *
 * <p><b>Резолв «числовой ключ → идентичность» живёт здесь</b>, потому что
 * он есть ЧТЕНИЕ, а не перенос полей: наружу ядро отдаёт
 * {@code internalId}, внутри ссылки идут по числовому ключу
 * (.claude/rules/codestyle.md §«Идентичность наружу»). Отдаётся он
 * раскладкой на пачку, а не по одному ключу на строку ответа: чтение на
 * каждую строку окна — тот самый запрос в цикле, который правило выборки
 * и запрещает.
 *
 * <p>Торговых решений не принимает и статусов не двигает: ступени двигает
 * ручная поверхность остановки, статусы сделок — проход.
 */
@Service
@RequiredArgsConstructor
public class TradingSurfaceService {

    /** Окно чтения сделок счёта: читатель приходит за текущим состоянием. */
    private static final Integer DEAL_WINDOW = 100;

    private final DealDataService dealDataService;
    private final DealTrancheDataService dealTrancheDataService;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final InstrumentDataService instrumentDataService;
    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final TenantRiskAppetiteDataService tenantRiskAppetiteDataService;

    /** Биржевой счёт по идентичности; нет — негодный вход вызова. */
    public ExchangeAccount getAccount(String exchangeAccountInternalId) {
        return exchangeAccountDataService.getRequiredByInternalId(exchangeAccountInternalId);
    }

    /** Биржевой счёт по внутреннему ключу — резолв ссылки уже прочитанной строки. */
    public ExchangeAccount getAccountById(Long exchangeAccountId) {
        return exchangeAccountDataService.getRequiredById(exchangeAccountId);
    }

    /** Сделки счёта недавним окном, от новых. */
    public List<Deal> findDeals(Long exchangeAccountId) {
        return dealDataService.findRecentOnAccount(exchangeAccountId, DEAL_WINDOW);
    }

    /** Одна сделка со своими траншами, уложенными в сам агрегат. */
    public Deal getDeal(String internalId) {
        Deal deal = dealDataService.getRequiredByInternalId(internalId);
        deal.setTranches(dealTrancheDataService.findByDealId(deal.getId()));
        return deal;
    }

    /**
     * Инструменты счёта со стоящей ступенью пары — их идентичности.
     * Одно чтение на радиус плюс одно на резолв идентичностей.
     */
    public List<String> instrumentInternalIdsWithStandingRung(Long exchangeAccountId) {
        List<Long> instrumentIds =
                accountInstrumentStateDataService.findInstrumentIdsWithStandingRung(exchangeAccountId);
        Map<Long, String> identities = instrumentDataService.findInternalIdsByIds(instrumentIds);
        return instrumentIds.stream()
                .map(identities::get)
                .filter(identity -> nonNull(identity))
                .collect(Collectors.toList());
    }

    /** Раскладка «ключ инструмента → идентичность» на пачку ключей. */
    public Map<Long, String> instrumentInternalIds(Collection<Long> instrumentIds) {
        return instrumentDataService.findInternalIdsByIds(instrumentIds);
    }

    /**
     * Числа риск-аппетита тенанта; строки нет — ядро о тенанте ещё не
     * знает, и пустой ответ отличается от ответа с пустыми числами.
     */
    public Tenant getRiskAppetite(String tenantInternalId) {
        return tenantRiskAppetiteDataService.findByTenantInternalId(tenantInternalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant risk appetite row not found: " + tenantInternalId));
    }

    /**
     * Назначить числа риск-аппетита тенанта. Снимок намерения целиком:
     * непереданное поле стирает прежнее число.
     */
    public Tenant applyRiskAppetite(String tenantInternalId, Tenant appetite) {
        appetite.setInternalId(tenantInternalId);
        return tenantRiskAppetiteDataService.applyRiskAppetite(appetite);
    }

    /**
     * Разрешаются ли ссылки определения стратегии в контексте тенанта.
     *
     * <p><b>Отвечают ПРОЕКЦИИ чужих реестров, и это названо.</b> Реестром
     * счетов владеет {@code auth}, каталогом инструментов —
     * {@code market-data}; ядро держит проекции обоих, и вызывающий
     * читает их со своим моментом снимка. Направление ошибки счётно:
     * членство в обоих реестрах монотонно, поэтому устаревшая проекция
     * может лишь не знать о новом — то есть отвергнуть в запрещающую
     * сторону (docs/rules/strategy-validation.md §«Что проверяется на
     * активации»).
     *
     * <p><b>Ненайденность здесь не исключение, а ответ:</b> вопрос
     * вызывающего и есть «существует ли», и бросок вместо признака
     * заставил бы его разбирать статус ответа вместо тела.
     */
    public PairCheck checkPair(String tenantInternalId, String exchangeAccountInternalId,
                               String instrumentInternalId) {
        Optional<String> owner =
                exchangeAccountDataService.findTenantInternalIdByInternalId(exchangeAccountInternalId);
        return new PairCheck(
                owner.isPresent(),
                owner.map(found -> Objects.equals(tenantInternalId, found)).orElse(false),
                instrumentDataService.existsByInternalId(instrumentInternalId));
    }
}
