package com.example.tradingcore.persistence.service;

import com.example.tradingcore.persistence.model.OutboxEntity;
import com.example.tradingcore.persistence.repository.OutboxRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для outbox.
 *
 * <p><b>Запись присоединяется к транзакции ВЫЗЫВАЮЩЕГО.</b> Требование
 * «решение и его событие одной транзакцией» исполнимо ровно тогда, когда
 * строку пишет тот код, который пишет решение
 * (docs/architecture/contracts.md §«У каждого класса события назван
 * писатель, и он же писатель решения»); посредник атомарности не
 * добавляет, а прячет.
 */
@Service
@RequiredArgsConstructor
public class OutboxDataService {

    private final OutboxRepository repository;

    /**
     * Записать строку в транзакции вызывающего.
     *
     * <p>Своей транзакции ход не открывает и чужую не требует: писатель
     * события зовёт его изнутри транзакции решения, а вне транзакции
     * запись строки была бы событием без решения.
     */
    @Transactional
    public OutboxEntity save(OutboxEntity entity) {
        return repository.save(entity);
    }

    /** Неопубликованные строки окном в порядке записи — вход реле. */
    @Transactional(readOnly = true)
    public List<OutboxEntity> findUnpublished(Integer limit) {
        return repository.findUnpublished(PageRequest.of(0, limit));
    }

    /**
     * Пометить строку опубликованной; {@code true} — отметка применилась.
     *
     * <p>Гард непустоты держит однократность: повторная публикация
     * безопасна по построению (потребитель дедуплицирует по идентичности
     * события), а повторная отметка сдвигала бы момент публикации назад
     * во времени наблюдения.
     */
    @Transactional
    public Boolean markPublished(Long id, OffsetDateTime publishedAt) {
        return repository.markPublished(id, publishedAt) > 0;
    }
}
