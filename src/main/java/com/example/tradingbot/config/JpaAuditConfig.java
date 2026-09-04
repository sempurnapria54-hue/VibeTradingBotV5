package com.example.tradingbot.config;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.util.Constants;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Включает JPA-аудит: persistence-слой проставляет системные
 * createdAt/modifiedAt/createdBy/modifiedBy на AuditableEntity.
 * Биржевые externalCreatedAt/externalModifiedAt проставляет код,
 * производящий данные (см. docs/models/domain/other/Auditable.md).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditConfig {

    /**
     * Резолвер актора записи. Классов значений два — <b>имя принципала</b> и
     * <b>собственный проход контура</b>, — и разделяет их
     * <b>предъявленный принципал</b>, а не тред записи и не место исполнения
     * (docs/models/domain/other/Auditable.md §«Область значений актора»).
     *
     * <p><b>Носитель дискриминатора — контекст хода.</b> Его ставит точка
     * порождения хода: обработка внешнего вызова под предъявленным
     * принципалом. Здесь он только читается — в момент, когда persistence
     * проставляет поля аудита.
     *
     * <p><b>Пусто в контексте означает «внешнего инициатора нет»</b>, и тогда
     * пишется класс контура: расписание, реакция обработчика, восстановление.
     * Это значение <b>по проверенному признаку</b>, а не умолчание.
     *
     * <p><b>Анонимная аутентификация значением не является.</b> Контур
     * доступа отдаёт её на открытой точке (проба живости); писать её именем
     * актора значило бы утверждать, что запись создал субъект, которого
     * контур не удостоверил.
     *
     * <p><b>Контекст переживает смену треда.</b> Ручной триггер джобы порождён
     * человеком, а запись создаётся в чужом треде асинхронного фасада —
     * перенос обеспечивает {@code AsyncSecurityContextConfig}. Без него
     * человеческая запись молча получила бы значение контура: ошибка
     * правдоподобная и неверная.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(currentActor());
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isNull(authentication)
                || isFalse(authentication.isAuthenticated())
                || authentication instanceof AnonymousAuthenticationToken) {
            return Constants.Audit.SYSTEM_PRINCIPAL;
        }
        return authentication.getName();
    }
}
