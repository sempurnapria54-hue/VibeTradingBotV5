package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.security.AccessDenial;
import com.example.tradingbot.mapping.AccessDenialMapper;
import com.example.tradingbot.persistence.repository.AccessDenialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link AccessDenial}.
 *
 * <p><b>Своя транзакция ({@code REQUIRES_NEW}), и это несущее решение.</b>
 * Строка заводится в фильтр-цепочке — до контроллера и вне какой-либо
 * прикладной транзакции; если бы она подхватывала чужую, откат этой чужой
 * уносил бы с собой и след отказа. След отказа обязан пережить всё, что
 * происходит с отвергнутым запросом дальше.
 */
@Service
@RequiredArgsConstructor
public class AccessDenialDataService {

    private final AccessDenialRepository repository;
    private final AccessDenialMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccessDenial save(AccessDenial denial) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(denial)));
    }
}
