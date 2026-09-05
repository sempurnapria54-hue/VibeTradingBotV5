package com.example.connector.okx.credentials;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.connector.okx.config.CredentialsProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Кэш ключей счёта на короткий срок поверх чтения из хранилища.
 *
 * <p><b>Кэш требует архитектура, а не удобство</b>
 * ({@code docs/architecture/tenant-and-exchange.md} §Ключи): без него
 * каждая команда площадке и каждый опрос факта ходили бы в хранилище, и
 * недоступность Vault останавливала бы торговлю мгновенно, а не по
 * истечении срока.
 *
 * <p><b>Срок — единственный механизм устаревания, пока нет события.</b>
 * Дом объявляет и сброс по {@code ExchangeKeysRotated}; события пока не
 * существует — его заводит шаг производителя ({@code auth}), а тем и
 * брокера в контуре ещё нет. До тех пор ротация ключей вступает в силу
 * через срок кэша, и это named-долг:
 * {@code .claude/work/backlog.md} §«Сброс кэша ключей по событию ротации».
 * Точка подключения готова — {@link #evict(String)}.
 *
 * <p><b>Отказ не кэшируется.</b> Ключей нет — вопрос задаётся хранилищу
 * заново: иначе заведение ключей вступало бы в силу через срок кэша, то
 * есть регистрация счёта выглядела бы сломанной ровно столько, сколько
 * живёт запись об отказе.
 */
@Primary
@Component
@RequiredArgsConstructor
public class CachingExchangeCredentialsResolver implements ExchangeCredentialsResolver {

    private final Map<String, CachedCredentials> cache = new ConcurrentHashMap<>();

    private final VaultExchangeCredentialsResolver delegate;
    private final CredentialsProperties properties;

    @Override
    public ExchangeCredentials resolve(String accountInternalId) {
        if (cacheDisabled()) {
            return delegate.resolve(accountInternalId);
        }
        CachedCredentials cached = cache.get(accountInternalId);
        if (nonNull(cached) && cached.isFresh()) {
            return cached.getCredentials();
        }
        ExchangeCredentials resolved = delegate.resolve(accountInternalId);
        cache.put(accountInternalId, new CachedCredentials(resolved, expiryFromNow()));
        return resolved;
    }

    /**
     * Забыть ключи счёта.
     *
     * <p>Точка, в которую придёт подписчик события ротации. Пока
     * вызывается только тестом — и это названо в javadoc класса, чтобы
     * метод не выглядел неиспользуемым кодом
     * ({@code .claude/rules/codestyle.md} §«Неиспользуемый код»).
     */
    public void evict(String accountInternalId) {
        cache.remove(accountInternalId);
    }

    private Boolean cacheDisabled() {
        Duration ttl = properties.getCacheTtl();
        return isNull(ttl) || ttl.isZero() || ttl.isNegative();
    }

    private Instant expiryFromNow() {
        return Instant.now().plus(properties.getCacheTtl());
    }
}
