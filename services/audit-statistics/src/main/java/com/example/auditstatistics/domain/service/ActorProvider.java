package com.example.auditstatistics.domain.service;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.auditstatistics.util.Constants;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Отвечает на вопрос «кто инициировал ход» — один поставщик на всех
 * читателей сервиса (docs/models/domain/other/Auditable.md §«Носитель
 * дискриминатора — контекст хода, а не поле модели»).
 *
 * <p><b>Читатель сегодня один</b> — колонки аудита строки отвергнутого
 * вызова (docs/models/domain/other/AccessDenial.md); строка журнала полей
 * аудита не несёт вовсе, потому что событие произошло однажды и правок у
 * записи о нём не бывает. Отдельным бином, а не приватным методом
 * конфигурации: ответ доменный, и у соседей он выражен так же — одна
 * конвенция двумя способами внутри одной дельты была бы расхождением сама
 * по себе.
 *
 * <p><b>Со-расположение с поставщиками соседей названо, а не умолчано.</b>
 * Тот же предикат «принципал либо контур» написан у {@code trading-core} и
 * у {@code strategies}: дом у ПРАВИЛА один
 * (docs/models/domain/other/Auditable.md §«Область значений актора»), а
 * общего носителя у РЕАЛИЗАЦИИ нет — она читает контекст Spring Security, и
 * вынос её в общий артефакт сделал бы эту зависимость обязательной для
 * всякого сервиса, который артефакт собирает
 * (.claude/rules/codestyle.md §«Новый модуль монорепозитория»). Цена
 * названа: расхождение трёх копий, в отличие от расхождения литерала, не
 * наблюдаемо — значение остаётся правдоподобным. Оживитель — задача о
 * едином носителе предиката (.claude/work/backlog.md §«Предикат актора —
 * три реализации при одном доме правила»).
 *
 * <p><b>Разделяет ПРЕДЪЯВЛЕННЫЙ принципал, а не тред записи и не место
 * исполнения.</b> Классов значений два — имя принципала и класс
 * собственного прохода контура.
 *
 * <p><b>Обе тропы отказа доступа достижимы, и значения у них разные.</b>
 * Принципал не предъявлен либо предъявленный не принят — внешнего
 * инициатора, которого контур удостоверил, нет, и актором идёт класс
 * контура. Принципал принят, а операция не разрешена — ход порождён
 * внешним вызовом, и актором идёт имя принципала. Схлопывать актора с
 * предметом строки нельзя: {@code createdBy} отвечает на «кто инициировал
 * ход», {@code principal} — на «кого отвергли», и на первой тропе они уже
 * расходятся (docs/models/domain/other/AccessDenial.md).
 *
 * <p><b>Пусто в контексте означает «внешнего инициатора нет»</b> — это
 * значение <b>по проверенному признаку</b>, а не умолчание.
 *
 * <p><b>Анонимная аутентификация значением не является.</b> Контур отдаёт
 * её на открытых точках (проба живости, съём метрик); писать её именем
 * актора значило бы утверждать, что ход начал субъект, которого контур не
 * удостоверил.
 *
 * <p><b>Переноса контекста в чужой тред здесь не требуется, и это названо,
 * а не забыто.</b> У сервиса нет ни одного асинхронного фасада:
 * поверхность объявлена только читающей, ручных триггеров у его джоб нет
 * (docs/components/JournalCleanupJob.md). Единственная запись с актором
 * создаётся в треде самого отвергнутого вызова.
 */
@Component
public class ActorProvider {

    /**
     * Актор текущего хода: имя предъявленного принципала либо класс
     * собственного прохода контура.
     */
    public String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isNull(authentication)
                || isFalse(authentication.isAuthenticated())
                || authentication instanceof AnonymousAuthenticationToken) {
            return Constants.Audit.SYSTEM_PRINCIPAL;
        }
        return authentication.getName();
    }
}
