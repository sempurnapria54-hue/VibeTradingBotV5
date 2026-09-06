package com.example.auth.domain.service;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.example.auth.persistence.model.MembershipEntity;
import com.example.auth.persistence.repository.MembershipRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Резолв членств предъявителя — вход контекста тенанта
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * <p><b>Он же заводит первое членство, и это не два разных исполнителя.</b>
 * Пользователь регистрируется у провидера идентичности сам, а платформа
 * узнаёт о нём при первом предъявлении токена
 * (docs/architecture/tenant-and-exchange.md §«Пользователи и роли»).
 * Отсюда «ноль членств» — не отказ, а заведение: тропа заведения не
 * должна зависеть от того, первый это пользователь или нет, иначе у неё
 * появилось бы два разных устройства для одного и того же события.
 *
 * <p><b>Служебная идентичность кластера этой тропы не проходит</b> — её
 * отсекает поверхность по клиенту токена, и здесь эта проверка не
 * повторяется: второй носитель одного условия разошёлся бы с первым.
 *
 * <p><b>Названное ограничение: гонки двух одновременных первых
 * предъявлений схема не исключает.</b> Проверка «членств нет» и вставка
 * идут одной транзакцией, но уникального ограничения на
 * {@code user_id} в схеме нет — при одном субъекте одновременного
 * первого предъявления не бывает, а со вторым субъектом заведение
 * переезжает на приглашения (фаза 5) и вопрос снимается вместе с тропой.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipResolutionService {

    private final MembershipRepository membershipRepository;
    private final TenantProvisioningService tenantProvisioningService;

    /**
     * Членства предъявителя; при их отсутствии — заведение тенанта с
     * членством владельца.
     *
     * @param userId    идентификатор пользователя у провайдера
     * @param tenantName имя тенанта, видимое человеку, — им становится
     *                   имя предъявителя, когда тенант заводится
     * @return членства предъявителя, минимум одно
     */
    @Transactional
    public List<MembershipEntity> resolveOrProvision(String userId, String tenantName) {
        List<MembershipEntity> existing = membershipRepository.findAllByUserId(userId);
        if (isNotEmpty(existing)) {
            return existing;
        }
        String tenantInternalId = tenantProvisioningService.provision(tenantName, userId);
        log.info("A tenant has been provisioned on the first token presentation tenantInternalId={}",
                tenantInternalId);
        return membershipRepository.findAllByUserId(userId);
    }
}
