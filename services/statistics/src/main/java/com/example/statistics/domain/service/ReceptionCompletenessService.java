package com.example.statistics.domain.service;

import com.example.statistics.domain.model.ReceptionCompleteness;
import com.example.statistics.persistence.service.ReceptionCompletenessSource;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Что статистика объявляет о полноте своих чисел: нижняя граница и
 * предикат непрерывности (docs/rules/durable-consumer-reception.md;
 * исполнимая форма — docs/spec/durable-reception.json,
 * {@code receptionLowerBound}, {@code continuityClaimable}).
 *
 * <p><b>Носитель СВЁРТКИ один, и заведён он затем, чтобы остаться
 * одним.</b> Величины читает агрегатная выборка, отдающая обе с КАЖДОЙ
 * выдачей чисел; вторая реализация той же свёртки расходилась бы с первой
 * молча (.claude/rules/policy-home.md).
 *
 * <p><b>Операнды приходят источником, а не внедряются сюда</b>
 * ({@link ReceptionCompletenessSource}): у класса нет ни одного внедрённого
 * поля — он и есть свёртка, и мокается в пробах читателей целиком.
 *
 * <p><b>Собственной транзакции методы не открывают:</b> границу называет
 * вызывающий.
 */
@Service
public class ReceptionCompletenessService {

    /**
     * Обе величины разом — так, как их и обязан получить читатель
     * (docs/rules/durable-consumer-reception.md §«Выдача чисел несёт СВОИ
     * величины, а не соседские»).
     *
     * <p><b>Собраны они вместе не ради удобства.</b> Граница выражает
     * начало ряда, предикат — отсутствие дыры в нём, и на состоянии «ни
     * одной наблюдаемой темы» обе обязаны сказать одно и то же: границы
     * нет, непрерывность не утверждаема.
     *
     * @param source      откуда читаются операнды
     * @param staleBefore момент, раньше которого строка состояния приёма
     *                    считается устаревшей: момент выдачи за вычетом
     *                    допустимого возраста
     */
    public ReceptionCompleteness completeness(ReceptionCompletenessSource source,
                                              String consumerGroup,
                                              OffsetDateTime staleBefore) {
        return new ReceptionCompleteness(lowerBound(source, consumerGroup).orElse(null),
                continuityClaimable(source, consumerGroup, staleBefore));
    }

    /**
     * Позднейший из моментов, с которых группа наблюдает свои темы.
     *
     * <p><b>Операнд ОДИН, и это следствие двух принятых решений, а не
     * упрощение</b> (docs/models/domain/other/StatisticsFact.md §«Состояние
     * приёма и полнота чисел статистики»): факты не чистятся ни в одном
     * окружении, значит начало ряда двигать нечем; и второй оси времени
     * факт не хранит вовсе — момента приёма у него нет, потому что его не
     * читает ни одна выборка. Арифметика при этом та же, что у соседа по
     * форме на пустом ряде следствий, и отдельной ветви исполнимая форма
     * для неё не заводит (docs/spec/durable-reception.json).
     *
     * <p><b>В подписке нет ни одной темы — границы нет</b>, и отсутствие
     * есть значение, а не ноль (docs/rules/absent-value-semantics.md):
     * группа, не наблюдающая ни одной темы, не обещает ничего.
     *
     * <p><b>Область пустой ветви — ПОДПИСАННЫЕ пары, а сам МАКСИМУМ
     * считается по ВСЕМ строкам, включая снятые с подписки.</b> Области у
     * двух операндов разные намеренно: факты снятой темы из базы не
     * исчезли, и исключение её из максимума опустило бы границу — ошибка в
     * разрешающую сторону.
     *
     * <p><b>Величина монотонна:</b> операнд двигается только вперёд —
     * появлением новой темы и возобновлением наблюдения.
     */
    public Optional<OffsetDateTime> lowerBound(ReceptionCompletenessSource source, String consumerGroup) {
        if (source.countSubscribedPairs(consumerGroup) == 0) {
            return Optional.empty();
        }
        return Optional.of(source.latestObservedSince(consumerGroup));
    }

    /**
     * Непрерывность принятого ряда после нижней границы утверждаема:
     * область квантора непуста И ни на одной подписанной паре дыры нет
     * (docs/spec/durable-reception.json, {@code continuityClaimable}).
     *
     * <p><b>Второй конъюнкт несущий.</b> Свёртка «все» по пустой коллекции
     * истинна: группа, не наблюдающая ни одной темы, без сравнения с нулём
     * отвечала бы «дыры нет» — ошибка в разрешающую сторону и
     * неразличение «не проверяли» с «проверили, всё в порядке»
     * (docs/concept.md, П1).
     *
     * <p><b>Гасящего писателя у момента разрыва здесь нет</b>, и по тому же
     * доводу, что у единственного операнда границы: гасит его чистка
     * следствий, а чистки у фактов не существует. Разрыв, случившийся
     * однажды, держит предикат ложным навсегда — верно по существу: дыра в
     * принятом есть безвозвратная потеря.
     *
     * <p><b>Ложь означает «не утверждаема», а не «дыра есть»:</b> поводов у
     * неё два, и читателю оба говорят одно — числам верить нельзя.
     */
    public Boolean continuityClaimable(ReceptionCompletenessSource source,
                                       String consumerGroup,
                                       OffsetDateTime staleBefore) {
        if (source.countSubscribedPairs(consumerGroup) == 0) {
            return Boolean.FALSE;
        }
        return source.countSubscribedPairsWithBreak(consumerGroup, staleBefore) == 0;
    }
}
