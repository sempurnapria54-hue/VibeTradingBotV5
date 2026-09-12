package com.example.tradingbot.message;

import java.math.BigDecimal;

/**
 * Содержимое события «сделка закрыта»: идентичности, исход терминала и
 * <b>операнды отчёта</b> (docs/architecture/contracts.md §«Отсюда состав
 * `DealClosed` — он несёт операнды отчёта, а не только исход»).
 *
 * <p><b>Операнды едут потому, что потребитель их не дочитает.</b> Строки на
 * чтение базы ядра у аудита нет, и величина, не поехавшая в событии,
 * <b>невосстановима задним числом</b>: журнал уже накопил строки без неё
 * (docs/rules/statistics-aggregates.md — дом состава чисел).
 *
 * <p><b>Результата до финансирования полем НЕТ, и это не пропуск:</b> он
 * производен от итога, накопленного финансирования и признака полноты
 * графа, и складывает его ЧИТАТЕЛЬ по форме дома
 * (docs/rules/loss-streak-halt.md §«Операнд собирается на терминале из уже
 * добытых чисел»). Признак полноты графа стои́т в составе ради этого
 * третьего операнда: на усечённой загрузке сумма по эпизодам выходит нулём
 * молча.
 *
 * <p><b>Числа едут числами, а не текстом.</b> Величина, которой нет
 * (недоступный результат, причина закрытия до терминала), кладётся
 * <b>отсутствующей</b>: сериализация её в строку {@code "null"} завела бы
 * значение, которого в домене не существует, и предикат «значения нет» у
 * читателя стал бы тождественно ложным
 * (docs/architecture/contracts.md §«Пустое значение едет пустым, а не
 * текстом», docs/rules/absent-value-semantics.md).
 *
 * <p><b>Знак трёх издержек нормализован издержкой — положительной.</b> В
 * домене сырой знак у комиссии и штрафа, нормализованный у финансирования
 * (docs/models/domain/core/Position.md); сводит их к одной конвенции
 * <b>писатель</b>, потому что суммы агрегата объявлены издержкой
 * положительными (docs/rules/statistics-aggregates.md), а смешение
 * конвенций дало бы расхождение на всей популяции.
 *
 * <p><b>Области значений перечней — домовые, и здесь они не
 * переписываются.</b> Дом у всех перечисленных ниже один —
 * docs/models/domain/aggregate/Deal.md ({@code Status},
 * {@code CloseReason}, {@code CloseOutcome}, {@code ReconciliationStatus},
 * {@code BreakdownCompleteness}, {@code RiskBenchmarkAvailability});
 * правило формы — docs/architecture/contracts.md, домен значения
 * содержимого объявляется указателем.
 *
 * <p><b>Форму строит маппер производителя, а не домен.</b> Прежде здесь
 * стояла фабрика {@code of(...)}, принимавшая доменную модель, — то есть
 * форма провода собиралась доменным кодом. Перевод в форму сообщения
 * принадлежит границе (.claude/rules/codestyle.md §«Слой сообщения:
 * внутренняя шина»), и делает его маппер сервиса-производителя.
 *
 * @param dealInternalId            идентичность сделки
 * @param exchangeAccountInternalId биржевой счёт сделки
 * @param instrumentInternalId      инструмент сделки
 * @param strategyInternalId        определение стратегии; пусто у
 *                                  восстановленной сделки — она заведена
 *                                  вокруг живого риска, и определения у неё
 *                                  не было
 * @param status                    терминальное состояние сделки — имя
 *                                  значения {@code Deal.Status}
 * @param closeReason               причина закрытия — имя значения
 *                                  {@code Deal.CloseReason}
 * @param tookRisk                  сделка принимала риск: позиция
 *                                  наблюдалась. Ложь — закрыта без входа, и
 *                                  в долях отчёта она не участвует
 * @param graphComplete             граф сделки предъявлен целиком на момент
 *                                  терминала
 * @param result                    итог сделки — net по всем издержкам,
 *                                  включая финансирование
 * @param resultCurrency            расчётная валюта итога; пусто — число
 *                                  есть, а в чём оно выражено, неизвестно
 * @param fee                       комиссии обеих ног, издержкой
 *                                  положительные
 * @param funding                   накопленное финансирование эпизодов,
 *                                  издержкой положительное
 * @param liquidationPenalty        штраф принудительного закрытия,
 *                                  издержкой положительный
 * @param plannedRisk               плановый риск сделки — знаменатель
 *                                  отношения к риску
 * @param closeOutcome              торговый исход закрытия — имя значения
 *                                  {@code Deal.CloseOutcome}
 * @param reconciliationStatus      состояние сверки итога с разбивкой —
 *                                  имя значения
 *                                  {@code Deal.ReconciliationStatus}
 * @param breakdownIncomplete       полнота разбивки движений — имя
 *                                  значения {@code Deal.BreakdownCompleteness}
 * @param riskBenchmarkAvailability доступность базы риска — имя значения
 *                                  {@code Deal.RiskBenchmarkAvailability}
 */
public record DealClosedMessage(String dealInternalId,
                                String exchangeAccountInternalId,
                                String instrumentInternalId,
                                String strategyInternalId,
                                String status,
                                String closeReason,
                                Boolean tookRisk,
                                Boolean graphComplete,
                                BigDecimal result,
                                String resultCurrency,
                                BigDecimal fee,
                                BigDecimal funding,
                                BigDecimal liquidationPenalty,
                                BigDecimal plannedRisk,
                                String closeOutcome,
                                String reconciliationStatus,
                                String breakdownIncomplete,
                                String riskBenchmarkAvailability) {
}
