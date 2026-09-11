package com.example.auditstatistics.domain.service;

import static java.util.Objects.isNull;

import com.example.auditstatistics.domain.model.JournalCompleteness;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Что журнал объявляет о своей полноте: нижняя граница и предикат
 * непрерывности (docs/models/domain/other/AuditRecord.md §«Позиция чтения
 * и с какого момента журнал полон»; исполнимая форма —
 * docs/spec/audit-journal.json, {@code journalLowerBound},
 * {@code continuityClaimable}).
 *
 * <p><b>Носитель СВЁРТКИ один, и заведён он затем, чтобы остаться
 * одним.</b> Величины читают трое: чистка журнала — чтобы понять, чей
 * разрыв вынесен за границу, — и обе выборки чтения, отдающие обе величины
 * с КАЖДОЙ выдачей чисел. Форма у всех трёх одна, дом её — спека; вторая
 * реализация той же свёртки расходилась бы с первой молча
 * (.claude/rules/policy-home.md).
 *
 * <p><b>Операнды приходят ИСТОЧНИКОМ, а не внедряются сюда</b>
 * ({@link JournalCompletenessSource}): подключений к базе журнала у
 * процесса два — своё под ролью журнала и кросс-подключение под ролью
 * агрегатов, — и выбор подключения принадлежит читателю, а не свёртке
 * (docs/architecture/data-ownership.md §Раскладка). Отсюда у класса нет ни
 * одного внедрённого поля: он и есть свёртка.
 *
 * <p><b>Собственной транзакции методы не открывают:</b> границу называет
 * вызывающий — у чистки она общая со снятием момента разрыва, чтобы
 * граница и правка по ней говорили об одном состоянии базы.
 */
@Service
public class JournalCompletenessService {

    /**
     * Обе величины разом — так, как их и обязан получить читатель
     * (docs/models/domain/other/AuditRecord.md §«Читатель получает
     * предикат вместе с числами, а не отдельным запросом»).
     *
     * <p><b>Собраны они вместе не ради удобства.</b> Граница выражает
     * начало ряда, предикат — отсутствие дыры в нём, и на состоянии «ни
     * одной наблюдаемой темы» обе обязаны сказать одно и то же: границы
     * нет, непрерывность не утверждаема. Выдача, спрашивающая одну без
     * другой, это состояние разложила бы на два разных ответа.
     *
     * @param source      откуда читаются операнды: подключение читателя, а
     *                    не свёртки
     * @param staleBefore момент, раньше которого строка состояния приёма
     *                    считается устаревшей: момент выдачи за вычетом
     *                    допустимого возраста
     */
    public JournalCompleteness completeness(JournalCompletenessSource source,
                                            String consumerGroup,
                                            OffsetDateTime staleBefore) {
        return new JournalCompleteness(lowerBound(source, consumerGroup).orElse(null),
                continuityClaimable(source, consumerGroup, staleBefore));
    }

    /**
     * Позднейший из двух моментов: самого раннего момента приёма среди
     * СОХРАНИВШИХСЯ строк журнала и позднейшего из моментов, с которых
     * группа наблюдает свои темы.
     *
     * <p><b>Обе ветви пустоты разобраны, и они разные.</b> Журнал пуст —
     * границей служит позднейший момент наблюдения: обещать по пустому
     * журналу нечего, но наблюдение уже идёт. <b>В подписке нет ни одной
     * темы — границы нет</b>, и отсутствие есть значение, а не ноль
     * (docs/rules/absent-value-semantics.md): группа, не наблюдающая ни
     * одной темы, не обещает ничего, и минимум по журналу обещал бы
     * полноту, которой никто не мерил.
     *
     * <p><b>Область пустой ветви — ПОДПИСАННЫЕ пары, а не строки
     * состояния вообще</b> (docs/spec/audit-journal.json,
     * {@code journalLowerBound} через {@code pairsObserved}). Разница
     * видна на состоянии «строки есть, но все сняты с подписки»: там
     * наблюдения не идёт ни по одной теме, и обещать нечего — ровно то
     * же, что при отсутствии строк.
     *
     * <p><b>А сам МАКСИМУМ считается по ВСЕМ строкам, включая снятые с
     * подписки</b> ({@code pairsObservedSince}): строки журнала снятой
     * темы из журнала не исчезли, и исключение её из максимума опустило
     * бы границу — ошибка в разрешающую сторону. Области у двух операндов
     * разные намеренно.
     *
     * <p><b>Величина монотонна:</b> оба операнда двигаются только вперёд —
     * первый чисткой непроизводственного окружения, второй появлением
     * новой темы и возобновлением наблюдения.
     */
    public Optional<OffsetDateTime> lowerBound(JournalCompletenessSource source, String consumerGroup) {
        if (source.countSubscribedPairs(consumerGroup) == 0) {
            return Optional.empty();
        }
        OffsetDateTime latestObserved = source.latestObservedSince(consumerGroup);
        OffsetDateTime earliestRecorded = source.earliestRecordedAt();
        if (isNull(earliestRecorded) || latestObserved.isAfter(earliestRecorded)) {
            return Optional.of(latestObserved);
        }
        return Optional.of(earliestRecorded);
    }

    /**
     * Непрерывность журнала после нижней границы утверждаема: область
     * квантора непуста И ни на одной подписанной паре дыры нет
     * (docs/spec/audit-journal.json, {@code continuityClaimable}).
     *
     * <p><b>Второй конъюнкт несущий.</b> Свёртка «все» по пустой
     * коллекции истинна: группа, не наблюдающая ни одной темы, без
     * сравнения с нулём отвечала бы «дыры нет» — ошибка в разрешающую
     * сторону и неразличение «не проверяли» с «проверили, всё в порядке»
     * (docs/concept.md, П1). Состояние достижимо: первый запуск
     * потребителя до первого такта тика, снятый выключатель тика при
     * живой поверхности чтения, очищенная таблица состояния в
     * непроизводственном окружении.
     *
     * <p><b>Ложь означает «не утверждаема», а не «дыра есть»:</b> поводов
     * у неё два, и читателю оба говорят одно — числам верить нельзя.
     */
    public Boolean continuityClaimable(JournalCompletenessSource source,
                                       String consumerGroup,
                                       OffsetDateTime staleBefore) {
        if (source.countSubscribedPairs(consumerGroup) == 0) {
            return Boolean.FALSE;
        }
        return source.countSubscribedPairsWithBreak(consumerGroup, staleBefore) == 0;
    }
}
