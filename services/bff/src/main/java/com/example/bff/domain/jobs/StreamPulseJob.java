package com.example.bff.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.bff.api.model.StreamRecordApiModel;
import com.example.bff.config.PerimeterProperties;
import com.example.bff.domain.stream.StreamRegistry;
import com.example.bff.integration.internal.event.ConsumerLivenessProvider;
import com.example.bff.util.Constants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Пульс потока: молчание без него — наблюдаемый отказ
 * (docs/architecture/contracts.md §«Поток несёт пульс, и молчание без
 * пульса — наблюдаемый отказ»).
 *
 * <p><b>Против чего заведён.</b> HTTP-соединение остаётся живым, когда
 * потребитель за ним встал: браузер держит открытый сокет и показывает
 * последнюю картину как текущую, а «тихо» и «сломано» у смотрящего
 * неразличимы. Автоматическое переподключение {@code EventSource} здесь
 * не помогает по построению — оно лечит разорванное соединение, а это
 * живое.
 *
 * <p><b>Пульс отражает живость ПОТРЕБИТЕЛЯ, а не планировщика — и это
 * несущее свойство.</b> Пульс, идущий по расписанию независимо от
 * подписки на темы, доказывал бы ровно то, что в периметре тикает
 * таймер: потребитель мёртв, событий нет, а картина выглядит живой —
 * то самое состояние, против которого пульс и заведён. Поэтому тик
 * молчит, пока хотя бы один слушатель не жив; что это значит, держит
 * {@link ConsumerLivenessProvider}.
 *
 * <p><b>Стороны разведены:</b> испускает пульс периметр, показывает его
 * отсутствие фронт (шаг 12 фазы 2) — и это вход шага фронта, а не
 * подразумеваемое поведение.
 *
 * <p><b>Ручного запуска и защиты от перекрытия у тика нет, и это
 * названо, а не забыто</b> (.claude/rules/codestyle.md §Джобы,
 * клауза о тике живости): пульс работы не производит и «догнать» его
 * нечем, а {@code fixedDelay} следующий тик до конца текущего не
 * запускает. Выключатель у него есть — как у всякой джобы.
 */
@Component
@RequiredArgsConstructor
public class StreamPulseJob {

    private final StreamRegistry streamRegistry;
    private final KafkaListenerEndpointRegistry listenerRegistry;
    private final ConsumerLivenessProvider consumerLivenessProvider;
    private final PerimeterProperties properties;

    /** Тик пульса; период — ось окружения, не хардкод. */
    @Scheduled(fixedDelayString = "${perimeter.stream.pulse-interval}")
    public void beat() {
        if (isFalse(properties.getStream().getPulseEnabled())) {
            return;
        }
        if (isFalse(streamRegistry.hasSubscriptions())) {
            return;
        }
        if (isFalse(consumerLivenessProvider.isLive(listenerRegistry.getListenerContainers()))) {
            return;
        }
        streamRegistry.broadcast(new StreamRecordApiModel(null, Constants.StreamRecords.PULSE,
                OffsetDateTime.now(ZoneOffset.UTC), null));
    }
}
