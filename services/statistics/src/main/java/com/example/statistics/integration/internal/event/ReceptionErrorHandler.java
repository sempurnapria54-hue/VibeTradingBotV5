package com.example.statistics.integration.internal.event;

import com.example.statistics.config.ReceptionProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Обработчик ошибок контейнера у группы статистики — <b>без ограничения числа
 * попыток</b> (docs/rules/durable-consumer-reception.md §«Обработчик отказа —
 * часть конструкции, а не настройка»).
 *
 * <p><b>Умолчание даёт обратное, и потому обработчик назван.</b> Оно —
 * конечное число попыток, затем восстановление логированием и
 * <b>продвижение смещения</b>; при нём «смещение продвинулось, строки нет»
 * перестаёт быть несуществующей ветвью и становится штатным исходом. У
 * фактов пропущенное не восстанавливается ничем, поэтому отказ обработки
 * не восстанавливается и смещения не двигает: группа остаётся на
 * отравленном сообщении, лаг растёт, возраст последнего принятого события
 * растёт монотонно — это и есть остановка приёма.
 *
 * <p><b>Исход отказа у соседних групп — не предмет этого класса.</b> У
 * потребителя определений ядра его дом —
 * docs/architecture/data-ownership.md §«Копии чужих данных»; признак, по
 * которому выбирается обработчик, — дом формы выше.
 *
 * <p><b>Пауза между повторами — величина конфигурации, число попыток —
 * нет.</b> Пауза управляет тем, как часто повтор бьётся в брокер и базу;
 * сдаться повтор не может по построению.
 */
@Component
public class ReceptionErrorHandler extends DefaultErrorHandler {

    public ReceptionErrorHandler(ReceptionProperties properties, ReceptionHaltMarker haltMarker) {
        super(new FixedBackOff(properties.getRetryInterval().toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        setRetryListeners(haltMarker);
    }
}
