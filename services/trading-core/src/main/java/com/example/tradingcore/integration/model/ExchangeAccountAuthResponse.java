package com.example.tradingcore.integration.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Строка реестра счетов в ответе {@code auth} — сырая форма соседа
 * (.claude/rules/codestyle.md §«Нейминг по слоям»: маркер источника
 * суффиксом).
 *
 * <p><b>Своя, а не импортированная из соседа:</b> api-модель принадлежит
 * тому, кто её отдаёт, и зависимость на неё сделала бы выкатку соседа
 * пересборкой ядра. Через границу едет форма, а не класс.
 *
 * <p>Торгового состояния счёта здесь нет: базу риска, серию убытков и
 * ступень пишет само ядро, и в реестре {@code auth} их не существует
 * (docs/models/domain/core/ExchangeAccount.md).
 */
@Getter
@Setter
@NoArgsConstructor
public class ExchangeAccountAuthResponse {

    /** Идентичность счёта; из неё выводится путь ключей в Vault. */
    private String internalId;

    /** Идентичность тенанта-владельца. */
    private String tenantInternalId;

    /** Код площадки. */
    private String exchangeCode;

    /** Метка счёта, видимая человеку. */
    private String label;

    /** Контур площадки: {@code LIVE} либо {@code DEMO}. */
    private String contour;

    /** Состояние счёта в реестре владельца. */
    private String status;
}
