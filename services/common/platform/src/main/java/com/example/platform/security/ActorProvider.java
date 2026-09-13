package com.example.platform.security;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.platform.util.Constants;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Отвечает на вопрос «кто инициировал ход» — один поставщик на всех
 * читателей (docs/models/domain/other/Auditable.md §«Носитель
 * дискриминатора — контекст хода, а не поле модели»).
 *
 * <p><b>Носитель предиката единственный, и это и есть предмет класса.</b>
 * Дом у ПРАВИЛА был один всегда, а реализаций стояло четыре — по одной у
 * каждого сервиса, пишущего актора. Расхождение таких копий, в отличие от
 * расхождения литерала, не наблюдаемо ничем: значение остаётся
 * правдоподобным, а уезжает оно содержимым события в чужой журнал.
 *
 * <p><b>Разделяет ПРЕДЪЯВЛЕННЫЙ принципал, а не тред записи и не место
 * исполнения.</b> Классов значений два — имя принципала и класс
 * собственного прохода контура.
 *
 * <p><b>Пусто в контексте означает «внешнего инициатора нет»</b> — это
 * значение <b>по проверенному признаку</b>, а не умолчание.
 *
 * <p><b>Анонимная аутентификация значением не является.</b> Контур отдаёт
 * её на открытых точках (проба живости, съём метрик); писать её именем
 * актора значило бы утверждать, что ход начал субъект, которого контур не
 * удостоверил.
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
 * <p><b>Перенос контекста в чужой тред здесь не делается, и это названо.</b>
 * У сервиса с асинхронным фасадом ручного триггера контекст обязан
 * пережить смену треда — иначе запись, порождённая человеком, получит
 * значение контура тихо. Носитель переноса свой у каждого такого сервиса
 * ({@code AsyncActorContextConfigurer}): он настраивает исполнитель
 * <b>своего</b> фасада, а не читает контекст.
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
