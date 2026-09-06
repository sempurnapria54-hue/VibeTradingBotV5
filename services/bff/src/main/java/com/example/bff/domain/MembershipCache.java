package com.example.bff.domain;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.bff.config.PerimeterProperties;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Эфемерный кэш резолвленного контекста тенанта.
 *
 * <p><b>Персистентного состояния периметр не держит</b>
 * (docs/architecture/contracts.md §«Состояния периметр не держит —
 * persistentного»): холодный под перечитывает контекст у владельца, а
 * записи живут ровно срок годности.
 *
 * <p><b>Направление ошибки названо: устаревшая запись ошибается в
 * РАЗРЕШАЮЩУЮ сторону</b> — отозванный участник сохраняет доступ до
 * истечения срока. Отсюда короткий срок; событие {@code MembershipChanged}
 * остаётся основной тропой сброса целевой конструкции, но производителя
 * у него сегодня нет, и на REST-тропе срок годности — тропа
 * единственная (.claude/work/backlog.md §«События `auth` —
 * производителя и темы нет»).
 *
 * <p><b>Своей реализацией, а не библиотекой кэша, — и это названный
 * выбор.</b> Зависимость общего назначения ради одной карты с одним
 * ключом на субъекта была бы бо́льшим следом без счётной разницы
 * (.claude/rules/design-simplicity.md); просроченные записи вытесняются
 * на чтении и на записи, поэтому фонового вытеснителя не требуется.
 */
@Component
@RequiredArgsConstructor
public class MembershipCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private final PerimeterProperties properties;

    /**
     * Контекст субъекта: из кэша, пока запись годна, иначе — вычисленный
     * заново.
     *
     * @param subject  идентичность предъявителя у провайдера
     * @param resolver добытчик контекста, зовомый при промахе
     * @return контекст тенанта предъявителя
     */
    public TenantContext get(String subject, Supplier<TenantContext> resolver) {
        Entry cached = entries.get(subject);
        if (isTrue(isFresh(cached))) {
            return cached.context();
        }
        TenantContext resolved = resolver.get();
        entries.put(subject, new Entry(resolved, Instant.now().plus(properties.getMembership().getCacheTtl())));
        evictExpired();
        return resolved;
    }

    /** Годность записи: пустая и просроченная неотличимы для читателя. */
    private Boolean isFresh(Entry entry) {
        return isNull(entry) ? Boolean.FALSE : entry.expiresAt().isAfter(Instant.now());
    }

    /** Вытеснение просроченного: без него карта росла бы по числу субъектов. */
    private void evictExpired() {
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
    }

    /**
     * Запись кэша.
     *
     * @param context   резолвленный контекст
     * @param expiresAt момент, после которого запись негодна
     */
    private record Entry(TenantContext context, Instant expiresAt) {
    }
}
