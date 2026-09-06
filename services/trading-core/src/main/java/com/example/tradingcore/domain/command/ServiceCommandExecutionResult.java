package com.example.tradingcore.domain.command;

import com.example.tradingcore.domain.safety.HoldSignal;
import java.util.List;
import lombok.Value;

/**
 * Исход одной команды: успех либо классификация ошибки для политики
 * повтора, плюс — при необходимости — затребованная ступень реакции.
 *
 * <p>Сущность и строку исполнения обновляет сам исполнитель; результат
 * сигналит исход диспетчеру
 * (docs/components/ServiceCommandExecutor.md).
 */
@Value
public class ServiceCommandExecutionResult {

    /** Команда исполнена успешно. */
    Boolean success;

    /** Классификация ошибки; пусто при успехе. Задаёт повторяемость. */
    RuntimeErrorCode errorCode;

    /** Сообщение об ошибке; пусто при успехе. */
    String message;

    /**
     * Затребованные исполнителем ступени; пусто — не затребованы.
     *
     * <p><b>Исполнитель ступень ЗАТРЕБУЕТ, а поднимает её проход.</b>
     * Прямой вызов службы ступеней из звена и невозможен по построению:
     * она ведёт снятие риска тем же диспетчером, который зовёт звено, —
     * зависимость замкнулась бы в цикл.
     *
     * <p><b>Перечень, а не одно значение, и это не запас.</b> Терминальное
     * ребро затребует две ступени сразу — по расхождению сверки и по
     * достигнутому пределу серии убытков
     * (docs/components/MarkDealClosedExecutor.md §«Побочные эффекты
     * терминала»), — и у каждой свой машинный код причины. Одно поле
     * заставило бы выбрать одну: второе основание не оставило бы строки, и
     * различимость пропала бы ровно там, ради чего коды заведены
     * (docs/rules/error-handling-policy.md §«Идемпотентность реакции и
     * идемпотентность отчёта — разные ключи»).
     */
    List<HoldSignal> holdSignals;

    /** Успешный исход. */
    public static ServiceCommandExecutionResult ok() {
        return new ServiceCommandExecutionResult(Boolean.TRUE, null, null, List.of());
    }

    /** Успешный исход, затребовавший одну ступень. */
    public static ServiceCommandExecutionResult okWithHold(HoldSignal holdSignal) {
        return new ServiceCommandExecutionResult(Boolean.TRUE, null, null, List.of(holdSignal));
    }

    /** Успешный исход, затребовавший ступени по нескольким основаниям сразу. */
    public static ServiceCommandExecutionResult okWithHolds(List<HoldSignal> holdSignals) {
        return new ServiceCommandExecutionResult(Boolean.TRUE, null, null, List.copyOf(holdSignals));
    }

    /** Исход-ошибка с классификацией. */
    public static ServiceCommandExecutionResult failure(RuntimeErrorCode errorCode, String message) {
        return new ServiceCommandExecutionResult(Boolean.FALSE, errorCode, message, List.of());
    }

    /**
     * Исход «звено не завершено»: ошибки нет, но условие завершения не
     * выполнено — факт ещё не добыт либо граф предъявлен не целиком.
     *
     * <p><b>Третий исход, а не разновидность отказа.</b> Повтор идёт по
     * бюджету попыток строки, пока бюджет жив; исчерпание бюджета уводит
     * звено штатной ошибочной тропой действия
     * (docs/spec/deal-context-load.json: {@code recomputeLinkCompletes},
     * {@code recomputeWaitContinues}, {@code recomputeErrorPathRequired}).
     * Классификации у него нет намеренно: площадка ни при чём, и
     * радиусной реакции «мы не смогли дозвониться» он не порождает.
     */
    public static ServiceCommandExecutionResult notCompleted(String message) {
        return new ServiceCommandExecutionResult(Boolean.FALSE, null, message, List.of());
    }
}
