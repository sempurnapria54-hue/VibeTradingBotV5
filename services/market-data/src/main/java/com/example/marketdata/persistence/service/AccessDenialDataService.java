package com.example.marketdata.persistence.service;

import com.example.marketdata.mapping.AccessDenialMapper;
import com.example.marketdata.persistence.repository.AccessDenialRepository;
import com.example.tradingbot.domain.model.other.AccessDenial;
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
 * <p><b>Менеджер транзакций — умолчание, и это не пропуск:</b> подключение
 * у сервиса одно, и выбирать не из чего. У сервисов с объявленным
 * отображением ({@code audit}, {@code statistics}) тот же ход называет
 * менеджер явно — это единственное различие копий писателя.
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AccessDenial denial) {
        repository.save(mapper.domainToPersistence(denial));
    }
}
