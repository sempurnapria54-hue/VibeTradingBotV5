package com.example.auth.api.controller;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.auth.api.model.MembershipApiResponse;
import com.example.auth.config.IdentityProperties;
import com.example.auth.domain.service.MembershipResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Резолв контекста тенанта: членства ПРЕДЪЯВИТЕЛЯ токена.
 *
 * <p><b>Единственный вызывающий — периметр</b>
 * (docs/architecture/contracts.md §«Синхронные вызовы», строка
 * {@code bff → auth}). Владелец «кто есть кто» один, и своей копии
 * членств периметр не держит — только эфемерный кэш.
 *
 * <p><b>Почему {@code POST}, а не {@code GET}.</b> Точка не только
 * читает: при первом предъявлении она ЗАВОДИТ тенанта с членством
 * владельца, то есть меняет состояние. Чтение под видом {@code GET}
 * скрывало бы это от всякого, кто смотрит на глагол.
 *
 * <p><b>Токен человека отличается от служебного по клиенту выдачи.</b>
 * Служебная идентичность кластера предъявляется на межсервисных
 * вызовах, где пользователя нет вовсе; заведение по ней создало бы
 * тенанта на каждый сервис. Признак — claim {@code azp}, и ось
 * «клиент браузера» приходит из манифеста окружения.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/memberships")
public class MembershipController {

    /** Claim, называющий клиента, которому выдан токен. */
    private static final String AUTHORIZED_PARTY_CLAIM = "azp";

    /** Claim с именем пользователя; им называется заводимый тенант. */
    private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";

    private final MembershipResolutionService membershipResolutionService;
    private final IdentityProperties identityProperties;

    /**
     * Членства предъявителя; при их отсутствии — заведение тенанта с
     * членством {@code OWNER}.
     *
     * @param token принятый токен предъявителя
     * @return членства предъявителя, минимум одно
     */
    @Operation(summary = "Резолв членств предъявителя токена")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Членства предъявителя"),
            @ApiResponse(responseCode = "403", description = "Токен выдан не браузерной тропой")
    })
    @PostMapping("/self")
    public List<MembershipApiResponse> resolveSelf(@AuthenticationPrincipal Jwt token) {
        requireBrowserToken(token);
        return membershipResolutionService.resolveOrProvision(token.getSubject(), tenantNameOf(token)).stream()
                .map(membership -> new MembershipApiResponse(membership.getInternalId(),
                        membership.getTenantId(), membership.getRole()))
                .collect(Collectors.toList());
    }

    /**
     * Отказ токену, выданному не браузерной тропой.
     *
     * <p>Незаданная ось означает, что браузерная тропа не настроена, —
     * тогда не проходит ни один токен: незаданное есть отказ, а не
     * разрешение.
     */
    private void requireBrowserToken(Jwt token) {
        if (isBlank(identityProperties.getBrowserClientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (isFalse(Objects.equals(identityProperties.getBrowserClientId(),
                token.getClaimAsString(AUTHORIZED_PARTY_CLAIM)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Имя заводимого тенанта — имя предъявителя, а при его отсутствии
     * идентификатор: имя видно человеку и ни на что не влияет, а
     * пустым оно быть не может.
     */
    private String tenantNameOf(Jwt token) {
        String username = token.getClaimAsString(PREFERRED_USERNAME_CLAIM);
        return isNull(username) ? token.getSubject() : username;
    }
}
