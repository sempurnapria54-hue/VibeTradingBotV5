package com.example.tradingcore.domain.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingcore.integration.MarketDataDemandClient;
import com.example.tradingcore.integration.PeerServiceUnavailableException;
import com.example.tradingcore.integration.model.ComputationConfigResponse;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Объявляет потребность копий определения владельцу рыночных данных и
 * запоминает выданные идентичности вычисления
 * (docs/architecture/market-data-collection.md §«Как потребность доходит
 * до сбора»).
 *
 * <p><b>Зачем это нужно вообще.</b> Значения индикаторов и структуры
 * ключуются <b>идентичностью вычисления</b>, а не настройкой заказчика
 * (docs/models/domain/other/IndicatorValue.md §«Ключевание — идентичностью
 * вычисления»), и всякое чтение фич адресуется ею. Соответствие
 * «авторское имя операнда → идентичность» держит потребитель — то есть
 * ядро; без него не составляется ни один вызов к владельцу данных: ни
 * фаза рынка, ни значения условий, ни гейт свежести шага.
 *
 * <p><b>Почему тиком, а не при приёме определения.</b> Писателей копии у
 * ядра будет два — команда приёма сегодня и потребитель события
 * активации после шага 8 (docs/architecture/data-ownership.md §«Копии
 * чужих данных»), — и каждому пришлось бы помнить об объявлении. Тик
 * делает объявление свойством <b>наличия копии</b>, а не тропы её
 * прибытия. Второй довод несущий: владелец данных в момент приёма может
 * быть недоступен, а приём определения не обязан падать из-за соседа —
 * тик просто повторит.
 *
 * <p><b>Повтор безопасен по построению:</b> требования идемпотентны по
 * содержанию, и то же требование возвращает ту же идентичность. Работы у
 * тика ровно столько, сколько непривязанных объявлений: привязанное он не
 * трогает — копия неизменяема, и второй идентичности у объявления быть не
 * может.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyDemandService {

    private static final TypeReference<Map<String, Object>> PARAMS_MAP = new TypeReference<>() { };

    private final StrategyDataService strategyDataService;
    private final InstrumentDataService instrumentDataService;
    private final MarketDataDemandClient demandClient;
    private final ObjectMapper objectMapper;

    /**
     * Объявляет потребность всех копий, у которых есть непривязанное
     * объявление.
     *
     * @return число копий, объявивших потребность этим проходом
     */
    public Integer declarePendingDemands() {
        List<Long> pending = strategyDataService.findIdsWithUnboundComputation();
        Integer declared = 0;
        for (Long strategyId : pending) {
            try {
                declareFor(strategyId);
                declared++;
            } catch (PeerServiceUnavailableException e) {
                log.error("Strategy demand pass stopped: market data owner is unavailable", e);
                return declared;
            } catch (RuntimeException e) {
                log.error("Strategy demand declaration failed for strategyId={}", strategyId, e);
            }
        }
        return declared;
    }

    /**
     * Объявляет потребность одной копии: сперва ряды свечей, затем
     * идентичности индикаторов, затем идентичности структур.
     *
     * <p><b>Порядок несущий.</b> Структура адресует свои входы
     * идентичностями, а не авторскими ключами, поэтому её требование
     * составимо только после того, как индикаторы получили свои. Ряды
     * идут первыми потому, что без них считать нечего: идентичность
     * вычисления инструмента не называет, и вычисляется она по тем
     * инструментам, у которых ряд её таймфрейма заведён.
     */
    private void declareFor(Long strategyId) {
        Strategy strategy = strategyDataService.findWithSettings(strategyId).orElse(null);
        if (isNull(strategy)) {
            log.warn("Strategy copy disappeared before demand declaration strategyId={}", strategyId);
            return;
        }
        Instrument instrument = instrumentDataService.getRequiredByInternalId(strategy.getInstrumentInternalId());
        requiredSeries(strategy).forEach((timeframe, depthBars) ->
                demandClient.requireCandles(instrument.getInternalId(), timeframe, depthBars));
        Map<String, String> identityByKey = declareIndicators(strategy);
        declareMarketStructures(strategy, identityByKey);
    }

    /**
     * Ряды свечей, нужные копии: таймфрейм → глубина истории.
     *
     * <p><b>Глубина берётся ОБЪЯВЛЕННАЯ, и пустота означает «вся доступная
     * история».</b> Прогрев индикатора выводится из его типа и периода
     * реализацией — то есть владельцем данных; ядро этого вывода не
     * повторяет. Подставить сюда своё число значило бы заказать МЕНЬШЕ
     * нужного и получить непосчитанный индикатор, а такой отказ пришёл бы
     * не к тому, кто число подставил.
     *
     * <p>Условие пересмотра — вывод глубины владельцем по прогреву самой
     * идентичности (`.claude/work/backlog.md` §«Глубина ряда свечей
     * выводится потребителем, а не владельцем вычисления»).
     */
    private Map<TimeFrame, Long> requiredSeries(Strategy strategy) {
        Map<TimeFrame, Long> series = new LinkedHashMap<>();
        emptyIfNull(strategy.getIndicatorSettings()).stream()
                .map(StrategyIndicatorSetting::getParams)
                .filter(params -> nonNull(params) && nonNull(params.getTimeframe()))
                .forEach(params -> mergeDepth(series, params.getTimeframe(), warmupBars(params)));
        emptyIfNull(strategy.getMarketStructureSettings()).stream()
                .filter(setting -> nonNull(setting.getTimeframe()))
                .forEach(setting -> mergeDepth(series, setting.getTimeframe(), structureBars(setting)));
        return series;
    }

    /**
     * Сводит требования одного таймфрейма: побеждает бо́льшая глубина, а
     * неназванная глубина побеждает любую названную — она и есть «вся
     * доступная история», то есть самое глубокое требование.
     */
    private void mergeDepth(Map<TimeFrame, Long> series, TimeFrame timeframe, Long depthBars) {
        if (series.containsKey(timeframe) && isNull(series.get(timeframe))) {
            return;
        }
        if (isNull(depthBars) || isNull(series.get(timeframe))) {
            series.put(timeframe, depthBars);
            return;
        }
        series.put(timeframe, Math.max(series.get(timeframe), depthBars));
    }

    /** Явный прогрев объявления; пусто — выводит реализация у владельца. */
    private Long warmupBars(IndicatorParams params) {
        return isNull(params.getWarmup()) ? null : params.getWarmup().longValue();
    }

    /**
     * Глубина, объявленная структурой: окно расчёта и поиск свингов.
     * Названа хотя бы одна — берётся бо́льшая; не названо ни одной —
     * пустота, то есть вся доступная история.
     */
    private Long structureBars(StrategyMarketStructureSetting setting) {
        MarketStructureParams params = setting.getParams();
        if (isNull(params)) {
            return null;
        }
        Integer lookback = params.getLookbackBars();
        Integer swing = params.getSwingLookbackBars();
        if (isNull(lookback) && isNull(swing)) {
            return null;
        }
        if (isNull(lookback)) {
            return swing.longValue();
        }
        if (isNull(swing)) {
            return lookback.longValue();
        }
        return Math.max(lookback.longValue(), swing.longValue());
    }

    /**
     * Требует идентичности всех индикаторных объявлений копии и
     * возвращает раскладку «авторский ключ → идентичность».
     *
     * <p>В раскладку попадают и <b>уже привязанные</b> объявления: структура
     * адресует свои входы идентичностями, и её требование обязано быть
     * составимо, даже когда индикаторы привязались прошлым проходом.
     */
    private Map<String, String> declareIndicators(Strategy strategy) {
        Map<String, String> identityByKey = new HashMap<>();
        for (StrategyIndicatorSetting setting : emptyIfNull(strategy.getIndicatorSettings())) {
            identityByKey.put(setting.getKey(), indicatorIdentity(setting));
        }
        return identityByKey;
    }

    private String indicatorIdentity(StrategyIndicatorSetting setting) {
        if (isBlank(setting.getComputationConfigInternalId())) {
            ComputationConfigResponse config = demandClient.requireIndicator(setting.getIndicatorType().name(),
                    setting.getParams().getTimeframe(), paramsMap(setting.getParams()));
            strategyDataService.bindIndicatorComputation(setting.getId(), config.getInternalId());
            return config.getInternalId();
        }
        return setting.getComputationConfigInternalId();
    }

    /**
     * Требует идентичности структурных объявлений копии.
     *
     * <p><b>Объявленный вход, который не резолвится, останавливает
     * требование этой структуры.</b> Пустая ссылка у владельца означает
     * «вход не объявлен», и подставить её вместо неразрешимого ключа
     * значило бы молча заказать ДРУГОЕ вычисление — резолвер откатился бы
     * на внутренний прокси, а условие считалось бы по нему как по
     * заказанному. Полная валидация определения приезжает с его владельцем
     * (docs/models/domain/aggregate/Strategy.md §«Где живёт определение и
     * где — его копия»); до неё дефект виден непривязанным объявлением и
     * этой записью журнала.
     */
    private void declareMarketStructures(Strategy strategy, Map<String, String> identityByKey) {
        for (StrategyMarketStructureSetting setting : emptyIfNull(strategy.getMarketStructureSettings())) {
            if (isBlank(setting.getComputationConfigInternalId())) {
                declareMarketStructure(strategy, setting, identityByKey);
            }
        }
    }

    private void declareMarketStructure(Strategy strategy, StrategyMarketStructureSetting setting,
                                        Map<String, String> identityByKey) {
        String efficiencyRatio = inputIdentity(setting.getEfficiencyRatioKey(), identityByKey);
        String atr = inputIdentity(setting.getAtrKey(), identityByKey);
        if (unresolvedInput(setting.getEfficiencyRatioKey(), efficiencyRatio)
                || unresolvedInput(setting.getAtrKey(), atr)) {
            log.error("Market structure input key is not declared by the strategy catalogue "
                            + "strategyInternalId={} structureKey={} erKey={} atrKey={}",
                    strategy.getInternalId(), setting.getKey(),
                    setting.getEfficiencyRatioKey(), setting.getAtrKey());
            return;
        }
        ComputationConfigResponse config = demandClient.requireMarketStructure(setting.getTimeframe(),
                paramsMap(setting.getParams()), efficiencyRatio, atr);
        strategyDataService.bindMarketStructureComputation(setting.getId(), config.getInternalId());
    }

    /** Идентичность входа по авторскому ключу; ключ не назван — вход не объявлен. */
    private String inputIdentity(String key, Map<String, String> identityByKey) {
        return isBlank(key) ? null : identityByKey.get(key);
    }

    /** Ключ входа назван, а идентичности за ним нет — объявления с таким ключом в каталоге не существует. */
    private Boolean unresolvedInput(String key, String identity) {
        return isBlank(key) ? false : isBlank(identity);
    }

    /**
     * Параметры расчёта картой имён — форма, которой их принимает
     * владелец. Тега подтипа в ней нет: подтип владелец восстанавливает по
     * названному типу индикатора, как и наша собственная персистентность
     * (docs/rules/persistence-representation.md).
     */
    private Map<String, Object> paramsMap(Object params) {
        return objectMapper.convertValue(params, PARAMS_MAP);
    }
}
