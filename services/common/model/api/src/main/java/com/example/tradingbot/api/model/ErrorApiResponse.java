package com.example.tradingbot.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Единый error-DTO внешней поверхности — <b>один на все сервисы</b>
 * (docs/rules/error-handling-policy.md §«Внешняя поверхность»).
 *
 * <p><b>Носитель единственный, и это и есть содержание клейма.</b> Пока
 * форма стояла копией у каждой поверхности, слово «единый» держалось
 * памятью пишущего: восемь копий уже разошлись описаниями полей, и
 * следующей разошлась бы форма — причём наружу, у читателя, который
 * объявленный контракт и разбирает.
 *
 * <p><b>Поле {@code reason} необязательно, и пустота у него — значение.</b>
 * Класс отказа ({@code code}) читается машинно; причина <b>внутри</b>
 * класса нужна там, где принимающая сторона выбирает по ней реакцию — так
 * у коннектора площадки, где ядро по коду проблемного статуса выводит
 * причину закрытия сущности (docs/rules/controlled-exchange-exceptions.md).
 * Там, где класс причин не различает, поле пусто, и это означает «различать
 * нечего», а не «причина неизвестна»
 * (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Форма — бин, и это следствие читателя, а не вкус:</b> тело отказа
 * разбирают потребители поверхности, а у формы без пустого конструктора и
 * creator'а сериализатор отказывает у читателя
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»). Запись переименовала бы аксессоры у всех потребителей.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorApiResponse {

    @Schema(description = "Класс отказа — устойчивый машиночитаемый идентификатор причины")
    private String code;

    @Schema(description = "Причина внутри класса, когда класс её различает; пусто, если не различает")
    private String reason;

    @Schema(description = "Пояснение для человека; секретов не несёт")
    private String message;

    @Schema(description = "Момент отказа, UTC")
    private OffsetDateTime occurredAt;
}
