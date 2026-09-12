package com.example.auditstatistics.integration.internal.event;

import com.example.auditstatistics.config.ReceptionProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Обработчик ошибок контейнера у группы журнала — <b>без ограничения числа
 * попыток</b> (docs/components/AuditEventListener.md §«Обработчик ошибок —
 * часть конструкции, а не настройка»).
 *
 * <p><b>Умолчание даёт обратное, и потому обработчик назван.</b> Оно —
 * конечное число попыток, затем восстановление логированием и
 * <b>продвижение смещения</b>; при нём «смещение продвинулось, строки нет»
 * перестаёт быть несуществующей ветвью и становится штатным исходом. У
 * журнала пропущенное не восстанавливается ничем, поэтому отказ обработки
 * не восстанавливается и смещения не двигает: группа остаётся на
 * отравленном сообщении, лаг растёт, возраст последнего принятого события
 * растёт монотонно — это и есть остановка приёма.
 *
 * <p><b>Соседняя группа умолчание оставляет законно:</b> потребитель
 * определений у ядра и поток периметра эфемерны либо восстановимы по
 * факту, и пропуск стои́т им строки лога.
 *
 * <p><b>Пауза между повторами — величина конфигурации, число попыток —
 * нет.</b> Пауза управляет тем, как часто повтор бьётся в брокер и базу;
 * сдаться повтор не может по построению.
 */
@Component
public class JournalReceptionErrorHandler extends DefaultErrorHandler {

    public JournalReceptionErrorHandler(ReceptionProperties properties, ReceptionHaltMarker haltMarker) {
        super(new FixedBackOff(properties.getRetryInterval().toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        setRetryListeners(haltMarker);
    }
}
