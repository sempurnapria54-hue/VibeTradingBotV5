package com.example.tradingcore.domain.account;

import java.util.Objects;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ступень и торговые настройки СЧЁТА НА ИНСТРУМЕНТЕ
 * (docs/models/domain/core/Instrument.md §«Ступень и настройки счёта на
 * инструменте — своя таблица ядра»).
 *
 * <p><b>Ключ — пара, а не инструмент.</b> Инструмент принадлежит площадке,
 * и ступень, поднятая отказами одного счёта, не описывает состояние
 * другого; плечо и режим маржи объявлены настройкой «счёта на
 * инструменте», не инструмента
 * (docs/architecture/tenant-and-exchange.md §Инструменты).
 *
 * <p><b>Почему не колонки проекции каталога.</b> Проекцию перезаписывает
 * синк, а эти поля пишет само ядро — запись ядра синк затирал бы каждым
 * тиком.
 *
 * <p><b>Модель у ядра, а не в общей библиотеке:</b> читателей и писателей
 * у строки один сервис, и общий артефакт от этого не выигрывает ничего
 * (libs/README.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountInstrumentState {

    /** Внутренний идентификатор строки. */
    private Long id;

    /** Биржевой счёт — первая половина ключа. */
    private Long exchangeAccountId;

    /** Инструмент — вторая половина ключа. */
    private Long instrumentId;

    /**
     * Ступень лестницы инструмента, стоящая на ЭТОМ счёте
     * (docs/rules/instrument-hold.md). Пишет её служба холдов.
     */
    private Instrument.SafetyRung safetyRung;

    /**
     * Режим маржи счёта на инструменте. Контур допускает единственный —
     * изолированный (docs/rules/trading-constraints.md); иной отвергается
     * преконтролем.
     */
    private Instrument.MarginMode marginMode;

    /**
     * Рабочее плечо счёта на инструменте — ручная статичная настройка
     * (docs/rules/trading-constraints.md). Пусто ⇒ плечо площадке не
     * пишется и биржевой максимум сверять не с чем.
     */
    private Integer leverage;

    /**
     * По паре стои́т safety-ступень с блок-сетом — мягкая
     * ({@code ENTRY_BLOCKED}) либо жёсткая ({@code TRADE_BLOCKED}).
     *
     * <p>Предикат на модели, а не сравнение перечня в сервисе: тот же
     * вопрос задают преконтроль риска, сканер входа и гейт повтора
     * отказавшей надобности, и ответ обязан быть один.
     */
    public Boolean hasStandingSafetyRung() {
        return Objects.equals(Instrument.SafetyRung.ENTRY_BLOCKED, safetyRung)
                || Objects.equals(Instrument.SafetyRung.TRADE_BLOCKED, safetyRung);
    }

    /** Режим маржи пары — изолированный, как требует контур. */
    public Boolean isMarginIsolated() {
        return Objects.equals(Instrument.MarginMode.ISOLATED, marginMode);
    }
}
