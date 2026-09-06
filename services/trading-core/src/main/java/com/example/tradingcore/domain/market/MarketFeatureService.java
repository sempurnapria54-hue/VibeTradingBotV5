package com.example.tradingcore.domain.market;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.integration.MarketDataReadClient;
import com.example.tradingcore.integration.model.MarketFeatureBinding;
import com.example.tradingcore.integration.model.MarketFeatureReadRequest;
import com.example.tradingcore.mapping.MarketFeatureMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Снимает фичи момента у владельца рыночных данных по объявлениям копии
 * определения (docs/architecture/contracts.md §«Синхронные вызовы»).
 *
 * <p><b>Соответствие «авторское имя → идентичность вычисления» держим
 * мы.</b> Значения ключуются идентичностью, а условия адресуют операнды
 * авторскими именами; владелец данных об именах не знает и знать не должен
 * (docs/models/domain/other/IndicatorValue.md §«Ключевание — идентичностью
 * вычисления»). Идентичности выдаёт тик объявления потребности
 * ({@code StrategyDemandService}).
 *
 * <p><b>Чтение одно на момент, а не по операнду.</b> Условиям нужны сразу
 * все входы; россыпь вызовов собрала бы контекст из значений РАЗНЫХ
 * моментов, и правило пересечения сравнило бы величины, не существовавшие
 * одновременно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketFeatureService {

    private final MarketDataReadClient readClient;
    private final MarketFeatureMapper mapper;

    /**
     * Фичи момента для оценки условий детали: значения объявленных
     * индикаторов и структур, цена — если её спрашивает хоть одно условие
     * детали, и фаза — если у стратегии объявлены клаузы.
     */
    public MarketFeatures readForEvaluation(Strategy strategy, StrategyDetail detail, Instrument instrument) {
        MarketFeatureReadRequest request = MarketFeatureReadRequest.builder()
                .indicatorBindings(indicatorBindings(strategy))
                .structureBindings(structureBindings(strategy))
                .phaseRules(phaseRules(strategy))
                .priceRequired(isNull(detail) ? false : isTrue(detail.readsPrice()))
                .build();
        return mapper.responseToDomain(readClient.readFeatures(instrument.getInternalId(), request));
    }

    /**
     * Фичи момента для ОТБОРА ВХОДА: те же значения, структуры и фаза, а
     * надобность цены выводится по <b>всей</b> стратегии.
     *
     * <p><b>Деталь на этом чтении ещё не выбрана.</b> Выбирает её фаза, а
     * фаза приезжает этим же чтением: спросить «читает ли цену выбранная
     * деталь» здесь не у кого. Второе чтение — после выбора детали —
     * собрало бы контекст из значений <b>разных моментов</b>, ровно то,
     * против чего заведено чтение одним вызовом.
     *
     * <p>Ответ по стратегии — надмножество ответа по детали
     * ({@link Strategy#readsPrice()}), и цена надмножества названа: у
     * стратегии, чью цену читает не всякая деталь, тик отбора платит
     * лишним обращением наружу.
     */
    public MarketFeatures readForEntry(Strategy strategy, Instrument instrument) {
        MarketFeatureReadRequest request = MarketFeatureReadRequest.builder()
                .indicatorBindings(indicatorBindings(strategy))
                .structureBindings(structureBindings(strategy))
                .phaseRules(phaseRules(strategy))
                .priceRequired(isTrue(strategy.readsPrice()))
                .build();
        return mapper.responseToDomain(readClient.readFeatures(instrument.getInternalId(), request));
    }

    /**
     * Фичи момента для РАСЧЁТА параметров действия: те же значения и
     * структуры, но цена спрашивается всегда, а фаза — нет.
     *
     * <p><b>Цена безусловна, потому что от неё считают.</b> Базой
     * размещения объявляется рыночная цена, ею же режется дистанция до
     * уровня остановки, и без неё расчёт отказывает по своему коду
     * (docs/components/PriceCalculator.md). Выводить её надобность из
     * объявления действия значило бы завести второй разбор грамматики
     * размещений ради вызова, который всё равно нужен почти всегда.
     *
     * <p><b>Фаза здесь не спрашивается.</b> Её потребители — условия
     * шагов, а они оценены раньше расчёта: классифицировать её второй раз
     * значило бы платить за ответ, которого никто не прочтёт.
     */
    public MarketFeatures readForCalculation(Strategy strategy, Instrument instrument) {
        MarketFeatureReadRequest request = MarketFeatureReadRequest.builder()
                .indicatorBindings(indicatorBindings(strategy))
                .structureBindings(structureBindings(strategy))
                .priceRequired(true)
                .build();
        return mapper.responseToDomain(readClient.readFeatures(instrument.getInternalId(), request));
    }

    private List<MarketFeatureBinding> indicatorBindings(Strategy strategy) {
        List<MarketFeatureBinding> bindings = new ArrayList<>();
        for (StrategyIndicatorSetting setting : emptyIfNull(strategy.getIndicatorSettings())) {
            addBinding(bindings, strategy, "indicator", setting.getKey(),
                    setting.getComputationConfigInternalId(), setting.getExpirationDuration());
        }
        return bindings;
    }

    private List<MarketFeatureBinding> structureBindings(Strategy strategy) {
        List<MarketFeatureBinding> bindings = new ArrayList<>();
        for (StrategyMarketStructureSetting setting : emptyIfNull(strategy.getMarketStructureSettings())) {
            addBinding(bindings, strategy, "market-structure", setting.getKey(),
                    setting.getComputationConfigInternalId(), setting.getExpirationDuration());
        }
        return bindings;
    }

    /**
     * Привязка объявления к идентичности; непривязанное и объявление без
     * срока свежести в запрос не попадают.
     *
     * <p><b>Пустой срок — не «бессрочно свежо», а отказ читать.</b>
     * Объявление, не назвавшее толерантности, не сказало, чему оно готово
     * доверять, и подставить сюда своё число значило бы решить за автора
     * (docs/rules/absent-value-semantics.md). Операнд оказывается
     * недоступен, предикат на нём консервативно ложен — и это <b>не</b>
     * молчаливое неисполнение: причина названа записью журнала, а
     * непривязанность снимает ближайший тик объявления потребности.
     */
    private void addBinding(List<MarketFeatureBinding> bindings, Strategy strategy, String kind,
                            String key, String configInternalId, Duration tolerance) {
        if (isBlank(configInternalId)) {
            log.debug("Feature binding skipped: computation identity is not declared yet "
                    + "strategyInternalId={} kind={} key={}", strategy.getInternalId(), kind, key);
            return;
        }
        if (isNull(tolerance)) {
            log.error("Feature binding skipped: declaration names no freshness tolerance, and the operand "
                            + "stays unavailable strategyInternalId={} kind={} key={}",
                    strategy.getInternalId(), kind, key);
            return;
        }
        bindings.add(MarketFeatureBinding.builder()
                .key(key)
                .configInternalId(configInternalId)
                .tolerance(tolerance)
                .build());
    }

    /** Клаузы классификации фазы стратегии; пусто — фазу не спрашиваем. */
    private List<StrategyMarketPhaseRule> phaseRules(Strategy strategy) {
        StrategyMarketPhaseSetting setting = strategy.getMarketPhaseSetting();
        return nonNull(setting) ? setting.getPhaseRules() : null;
    }
}
