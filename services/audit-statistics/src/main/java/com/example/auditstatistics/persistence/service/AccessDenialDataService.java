package com.example.auditstatistics.persistence.service;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.domain.model.AccessDenial;
import com.example.auditstatistics.mapping.AccessDenialMapper;
import com.example.auditstatistics.persistence.repository.journal.AccessDenialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для строки отвергнутого вызова.
 *
 * <p><b>Транзакционная граница стои́т ЗДЕСЬ, а не на доменном писателе, как
 * у остальных ходов модуля, и довод механический.</b> Писатель обязан
 * <b>поглотить</b> отказ записи — иначе сбой журнала превратил бы отказ
 * доступа в ответ 500 (docs/models/domain/other/AccessDenial.md
 * §Инварианты). Перехват внутри собственного транзакционного метода этого
 * не даёт: исключение ловится до выхода из прокси, а фиксация на выходе
 * поднимает его снова — уже мимо перехвата. Поэтому транзакция
 * заканчивается здесь, а ловит вызывающий.
 *
 * <p><b>Своя транзакция ({@code REQUIRES_NEW}).</b> Строка заводится в
 * фильтр-цепочке — до контроллера и вне какой-либо прикладной транзакции;
 * подхватив чужую, она ушла бы вместе с её откатом. След отказа обязан
 * пережить всё, что происходит с отвергнутым запросом дальше.
 *
 * <p><b>Менеджер транзакций назван явно.</b> Умолчания у выбора нет:
 * отображений схемы на классы у процесса три, ни одно не помечено
 * основным ({@link JournalPersistenceConfig}), и неквалифицированный
 * {@code @Transactional} взял бы менеджер молча.
 */
@Service
@RequiredArgsConstructor
public class AccessDenialDataService {

    private final AccessDenialRepository repository;
    private final AccessDenialMapper mapper;

    /**
     * Завести строку отказа.
     *
     * <p>Возврата у хода нет: читателя у только что вставленной строки не
     * существует — её единственный потребитель разбирает базу руками
     * (docs/models/domain/other/AccessDenial.md).
     */
    @Transactional(transactionManager = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRES_NEW)
    public void save(AccessDenial denial) {
        repository.save(mapper.domainToPersistence(denial));
    }
}
