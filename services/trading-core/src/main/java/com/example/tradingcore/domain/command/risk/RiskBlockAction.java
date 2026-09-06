package com.example.tradingcore.domain.command.risk;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import lombok.Builder;
import lombok.Value;

/**
 * Действие, которое {@code RiskBlockResolver} возвращает обработчику по
 * результату риск-проверки, чтобы обработчик не содержал большой
 * {@code switch} по всем кодам риска
 * (docs/components/models/RiskBlockAction.md).
 *
 * <p><b>Код вердикта несёт {@link #riskCode}, а не поле типа
 * {@code RuntimeErrorCode}.</b> Карта реакции требует положить в действие
 * «код вердикта», а прежняя форма типовала это поле классификацией
 * НЕОЖИДАННЫХ исключений, чей собственный дом объявляет прямо: результат
 * риск-проверки в неё не превращается
 * (docs/rules/runtime-error-classification.md). Записанный туда вердикт
 * читался бы как техническая ошибка исполнения, а её код назначает
 * граница исполнения по своему разбору, не риск-контроль.
 *
 * <p>Живёт только в памяти прохода, поэтому неизменяемая форма здесь
 * дефолт (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
@Value
@Builder
public class RiskBlockAction {

    /** Что должен сделать обработчик FSM. */
    Type type;

    /** Причина закрытия, если нужно закрыть кандидатную сделку. */
    Deal.CloseReason closeReason;

    /**
     * Старший код вердикта — причина реакции. Пуст у разрешающих реакций
     * и у отложенного вердикта: причины у них нет
     * (docs/components/RiskBlockResolver.md §«Карта «вердикт → действие»»).
     */
    RiskCheckCode riskCode;

    /** Короткое пояснение для логов и разбора. */
    String comment;

    /** Что должен сделать обработчик FSM по результату риск-проверки. */
    public enum Type {

        /** Продолжить выполнение действия. */
        CONTINUE,

        /** Продолжить, но сохранить предупреждение. */
        CONTINUE_WITH_WARNING,

        /** Закрыть кандидатную сделку без ошибки — живого риска ещё нет. */
        CLOSE_CANDIDATE_DEAL,

        /** Перевести сделку в ERROR; дальше — обработчик ошибок и safety-тропа. */
        MOVE_DEAL_TO_ERROR,

        /** Не выполнять текущее действие, запросить добычу фактов. */
        REQUEST_REFRESH,

        /** Пропустить действие как более не актуальное. */
        SKIP_ACTION
    }
}
