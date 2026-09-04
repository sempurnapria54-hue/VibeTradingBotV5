package com.example.tradingbot.domain.security;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.abbreviate;

import com.example.tradingbot.persistence.service.AccessDenialDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Писатель журнальной строки отвергнутого по правам вызова
 * (docs/models/domain/other/AccessDenial.md). Зовут его точки входа отказа
 * фильтр-цепочки — до сборки ответа вызывающему.
 *
 * <p><b>Отказ записи не превращается в доступ.</b> Если строку завести не
 * удалось, вызов всё равно отвергается: сбой записи уходит в лог (уровень 1
 * внутренней градации), а ответ остаётся отказом. Обратный порядок — «не
 * смогли записать, значит пропускаем» — был бы ошибкой в разрешающую сторону
 * (П1 следствие 3).
 *
 * <p><b>Почему сбой уходит именно в лог, при том что лог носителем
 * наблюдаемости не является.</b> Носитель здесь и есть та запись, которая не
 * удалась; второго персистентного носителя под сбой первого не заводится — он
 * упирался бы в ту же недоступную базу. Отказ при этом громкий, а не тихий.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessDenialService {

    /**
     * Потолок ширины колонки поверхности. Значение <b>контролируется
     * вызывающим, который себя не предъявил</b>: длинный путь — это его выбор,
     * а не наш факт. Без усечения такой вызов ронял бы запись строки на
     * ограничении колонки, то есть отказ доступа стирал бы собственный след.
     */
    private static final int SURFACE_MAX_LENGTH = 256;

    private final AccessDenialDataService dataService;

    /**
     * Завести строку отказа. {@code principal} — <b>принятый</b> принципал
     * либо пусто; заявленное, но не удостоверенное имя сюда не передаётся.
     */
    public void record(String surface, AccessDenial.Outcome outcome, String principal) {
        AccessDenial denial = new AccessDenial();
        denial.setInternalId(ClientIdGenerator.generate());
        denial.setSurface(truncateSurface(surface));
        denial.setOutcome(outcome);
        denial.setPrincipal(principal);

        if (isFalse(denial.isConsistent())) {
            // Дефект писателя, а не вызывающего: класс отказа и принципал
            // выражают одно состояние дважды и разойтись не вправе.
            log.error("Access denial row is inconsistent: outcome={} principal present={}",
                    outcome, nonNull(principal));
        }
        try {
            dataService.save(denial);
        } catch (RuntimeException failure) {
            log.error("Access denial row not persisted for surface {} ({}): {}",
                    surface, outcome, failure.getMessage(), failure);
        }
    }

    /** Усечение по потолку колонки — см. {@link #SURFACE_MAX_LENGTH}. */
    private String truncateSurface(String surface) {
        return abbreviate(surface, SURFACE_MAX_LENGTH);
    }
}
