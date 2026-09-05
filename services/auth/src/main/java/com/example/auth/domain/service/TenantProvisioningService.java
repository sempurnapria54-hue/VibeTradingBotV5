package com.example.auth.domain.service;

import com.example.auth.domain.model.Membership;
import com.example.auth.persistence.model.MembershipEntity;
import com.example.auth.persistence.model.TenantEntity;
import com.example.auth.persistence.repository.MembershipRepository;
import com.example.auth.persistence.repository.TenantRepository;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingbot.domain.util.InternalIdFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Заведение тенанта вместе с членством его владельца.
 *
 * <p><b>Одной транзакцией, и это несущее свойство.</b> Инвариант «тенанта
 * без владельца не бывает» ограничением схемы не выражается: частичный
 * уникальный индекс даёт единственность владельца, но не его
 * существование. Держит существование этот исполнитель — тем, что тенант
 * и членство появляются вместе либо не появляются вовсе.
 *
 * <p>Пользователь при этом заводится не здесь: он регистрируется у
 * провайдера идентичности сам, а {@code auth} узнаёт о нём при первом
 * предъявлении токена (docs/architecture/tenant-and-exchange.md
 * §«Пользователи и роли»).
 */
@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;

    public TenantProvisioningService(TenantRepository tenantRepository,
                                     MembershipRepository membershipRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Заводит тенанта и делает предъявившего пользователя его владельцем.
     *
     * @param name   имя тенанта, видимое человеку
     * @param userId идентификатор пользователя у провайдера идентичности
     * @return {@code internalId} заведённого тенанта
     */
    @Transactional
    public String provision(String name, String userId) {
        TenantEntity tenant = new TenantEntity();
        tenant.setInternalId(InternalIdFactory.forInternalEntity());
        tenant.setName(name);
        tenant.setStatus(Tenant.Status.ACTIVE.name());
        tenantRepository.save(tenant);

        MembershipEntity owner = new MembershipEntity();
        owner.setInternalId(InternalIdFactory.forInternalEntity());
        owner.setUserId(userId);
        owner.setTenantId(tenant.getInternalId());
        owner.setRole(Membership.Role.OWNER.name());
        membershipRepository.save(owner);

        return tenant.getInternalId();
    }
}
