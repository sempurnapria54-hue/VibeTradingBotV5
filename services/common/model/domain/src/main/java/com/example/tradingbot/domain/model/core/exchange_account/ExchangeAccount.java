package com.example.tradingbot.domain.model.core.exchange_account;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Биржевой счёт тенанта: тенант × площадка × метка
 * (docs/models/domain/core/ExchangeAccount.md).
 *
 * <p><b>Писателей два, и они пишут разные наборы полей:</b> реестровую
 * часть (идентичность, контур, статус) — {@code auth}; торговое состояние
 * (база риска, серия убытков, счётчик слепых проходов, момент наблюдённого
 * прохода) —
 * {@code trading-core}. Физически это две таблицы в двух базах с общим
 * {@link #internalId}, поэтому правило «у таблицы один писатель»
 * соблюдено. Класс один, потому что одна и та же сущность: разведение по
 * писателям — свойство хранения, не формы.
 *
 * <p>Ключей счёта здесь нет ни в каком виде: они в Vault по пути,
 * выводимому из {@link #internalId}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeAccount extends Auditable {

    /** Внутренний идентификатор. Границу сервиса не пересекает. */
    private Long id;

    /**
     * Идентичность счёта наружу и между сервисами. Из неё выводится путь
     * ключей в Vault; присваивается при регистрации, дальше неизменяема.
     */
    private String internalId;

    /** {@code internalId} тенанта-владельца. */
    private String tenantId;

    /** Код площадки. */
    private String exchangeCode;

    /**
     * Метка счёта, видимая человеку: у тенанта на одной площадке счетов
     * может быть несколько.
     */
    private String label;

    /**
     * Контур площадки. Лежит рядом с ключами, потому что ключ и контур
     * суть одна истина: демо-ключ на боевой площадке отвергается, боевой
     * на демо — тоже.
     */
    private Contour contour;

    /** Состояние счёта. */
    private Status status;

    /**
     * База риска счёта — операнд всех четырёх потолков через снимок на
     * сделке. Пусто ⇒ risk-creating действие отвергается: пустое место
     * означает отказ, а не ноль.
     */
    private BigDecimal riskBase;

    /** Валюта базы; источник — расчётная валюта инструмента. */
    private String riskBaseCurrency;

    /** Длина текущей серии подряд убыточных закрытых сделок. */
    private Integer consecutiveLossCount;

    /** Подряд идущие ненаблюдённые проходы проактивной детекции. */
    private Integer blindPassCount;

    /**
     * Момент начала последнего НАБЛЮДЁННОГО прохода проактивной детекции по
     * этому счёту: срез добыт целиком и детекция по нему отработала. Пусто —
     * наблюдения не было ни разу.
     *
     * <p><b>Операнд тропы, набирающей риск, а не счётчика слепоты.</b> Счёт
     * слепоты двигают только состоявшиеся проходы; тик, не исполнившийся
     * вовсе, оставляет его нулевым. Возраст этого момента растёт и тогда —
     * поэтому отбор входа спрашивает его, а не счёт
     * (docs/components/EntryScannerJob.md §«Гейт входа»).
     */
    private OffsetDateTime observedPassAt;

    /**
     * Ступень лестницы реакций, стоящая на этом счёте
     * (docs/rules/exchange-hold.md). Пишет её {@code trading-core} — он её
     * и поднимает; в реестровой таблице {@code auth} ступени нет ни в
     * каком виде.
     */
    private SafetyRung safetyRung;

    /**
     * Проход проактивной детекции наблюдал счёт не раньше, чем {@code maxAge}
     * до {@code now}. Ложь — и когда наблюдение старше допуска, и когда его
     * не было вовсе: молчание детекции разрешением набирать риск не является
     * (docs/components/EntryScannerJob.md §«Гейт входа»).
     *
     * @param maxAge допустимый возраст наблюдения
     * @param now    момент вопроса
     */
    public Boolean observedWithin(Duration maxAge, OffsetDateTime now) {
        return nonNull(observedPassAt) && isFalse(observedPassAt.plus(maxAge).isBefore(now));
    }

    /** Контур площадки, к которому принадлежат ключи счёта. */
    public enum Contour {

        /** Боевая площадка: сделки двигают капитал владельца. */
        LIVE,

        /** Демо-контур площадки: сделки капитала не двигают. */
        DEMO
    }

    /**
     * Состояние РЕГИСТРАЦИИ счёта у платформы (писатель {@code auth}):
     * торгует он или отключён владельцем.
     *
     * <p><b>Ступеней лестницы реакций здесь нет,</b> их несёт
     * {@link SafetyRung} торговой части. Прежняя редакция держала один
     * перечень на оба поля — одна истина получала два носителя в двух
     * базах, и читатель ступени не знал, какой авторитетен
     * (docs/models/domain/core/ExchangeAccount.md §«Енум `Status`
     * (реестровая часть, писатель `auth`)»).
     */
    public enum Status {

        /** Счёт зарегистрирован и доступен торговле. */
        ACTIVE,

        /**
         * Счёт отключён владельцем; ключи отозваны.
         *
         * <p><b>Достижим только при отсутствии живого риска:</b>
         * отключение лишает единственной тропы снять риск — kill-switch
         * исполняется теми же кредами. Владелец сворачивает счёт штатной
         * лестницей и отключает уже пустой.
         */
        CLOSED
    }

    /**
     * Ступень лестницы реакций на счёте (писатель {@code trading-core}).
     *
     * <p>Семантика ступеней, условия входа и снятия живут в
     * docs/rules/exchange-hold.md и здесь не пересказываются.
     */
    public enum SafetyRung {

        /** Ступени нет: рабочее состояние. */
        ACTIVE,

        /**
         * Мягкий холд: счёт выпадает из выборки сканера входа, новые
         * сделки не создаются; живые сопровождаются полностью.
         */
        HOLD,

        /**
         * Сворачивание: снятие живого риска по счёту, каскад активных
         * сделок в ошибку, блок торговых команд.
         */
        TRADE_BLOCKED;

        /**
         * Ранг ступени: 2 — сворачивание, 1 — мягкий холд, 0 — ступени нет
         * (docs/spec/manual-halt.json, величина {@code rungRankBefore}).
         * Механизм монотонности — тот же, что у лестницы инструмента
         * (docs/rules/exchange-hold.md §«Границы и эскалация»): подъём
         * применяется только СТРОГО выше стоящей ступени.
         */
        public Integer rank() {
            return switch (this) {
                case ACTIVE -> 0;
                case HOLD -> 1;
                case TRADE_BLOCKED -> 2;
            };
        }
    }
}
