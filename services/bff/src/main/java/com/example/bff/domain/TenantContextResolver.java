package com.example.bff.domain;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.bff.integration.internal.api.AuthMembershipClient;
import com.example.bff.integration.internal.api.model.MembershipApiModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Вывод контекста тенанта из членств предъявителя
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * <p><b>Браузер тенанта не называет.</b> Присланное значение добытчиком
 * не является ни при каком варианте: сверять его пришлось бы всё равно
 * с членствами, то есть исполнять этот же вывод.
 *
 * <p><b>Три ветви, и у каждой назван исход:</b>
 * <ul>
 *   <li><b>ровно одно членство</b> — оно и есть контекст;</li>
 *   <li><b>ноль членств</b> — не отказ, а ЗАВЕДЕНИЕ: владелец «кто есть
 *       кто» заводит тенанта с членством {@code OWNER} той же точкой
 *       резолва, поэтому пустой ответ сюда не доходит вовсе;</li>
 *   <li><b>больше одного</b> — НЕДОСТИЖИМО до второго субъекта: фаза 5
 *       вводит приглашения, а заведение даёт ровно одно членство.
 *       Ветвь отвечает отказом, а не выбирает произвольное: молчаливый
 *       выбор дал бы контекст, о котором никто не решал.</li>
 * </ul>
 *
 * <p><b>Фаза 5 меняет обе крайние ветви разом</b>, и обе лежат на этой
 * тропе — внешнего контракта они не трогают.
 */
@Service
@RequiredArgsConstructor
public class TenantContextResolver {

    private final AuthMembershipClient membershipClient;
    private final MembershipCache membershipCache;

    /**
     * Контекст предъявителя.
     *
     * @param subject     идентичность предъявителя у провайдера — ключ кэша
     * @param bearerToken предъявленный токен, как есть
     * @return тенант и роль предъявителя
     */
    public TenantContext resolve(String subject, String bearerToken) {
        return membershipCache.get(subject, () -> fromMemberships(membershipClient.resolveSelf(bearerToken)));
    }

    private TenantContext fromMemberships(List<MembershipApiModel> memberships) {
        if (isEmpty(memberships)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Контекст тенанта не резолвится: членств у предъявителя нет");
        }
        if (memberships.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Контекст тенанта не резолвится: членств больше одного, а выбора ещё нет");
        }
        MembershipApiModel membership = memberships.get(0);
        return new TenantContext(membership.tenantId(), membership.role());
    }
}
