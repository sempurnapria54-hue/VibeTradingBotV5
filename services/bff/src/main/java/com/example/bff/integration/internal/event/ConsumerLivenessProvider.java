package com.example.bff.integration.internal.event;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.bff.util.Constants;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.config.ConfigDef;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Живость потребления — операнд, по которому измеритель живости молчит
 * (docs/architecture/contracts.md §«Пульс отражает живость ПОТРЕБИТЕЛЯ, а не
 * планировщика»).
 *
 * <p><b>Конъюнктов три, и третий несущий.</b> Контейнер запущен, держит
 * назначенные партиции и <b>на связи с координатором группы</b>. Первых
 * двух мало: у остановленного брокера клиент назначения не теряет — он ждёт
 * координатора, не отдавая партиций, и запущенный слушатель с непустым
 * назначением выглядел бы живым сколь угодно долго после потери связи.
 *
 * <p><b>Связь мерится возрастом последнего контакта с координатором</b> —
 * младшей из двух отметок, которые отдаёт сам клиент: последнего
 * сердцебиения и последней успешной ребалансировки. Пока координатор
 * известен, клиент бьётся в него с периодом сердцебиения; потеряв его,
 * перестаёт, и возраст растёт. Вторая отметка нужна свежему участнику:
 * первое сердцебиение уходит через период после вступления в группу, а
 * вступление само есть контакт — без неё реплика, только что получившая
 * назначение, этот период считалась бы неживой.
 *
 * <p><b>Порог — срок сессии потребителя, а не новая величина.</b> Дольше
 * него без сердцебиения брокер считает участника выбывшим и отдаёт его
 * партиции другим: назначение, которое клиент ещё держит, к этому моменту
 * уже ничьё. Срок берётся из конфигурации фабрики потребителя, а не
 * указан рядом: вторая его запись разошлась бы с первой.
 *
 * <p><b>Неизмеренное живостью не считается.</b> Ни одной отметки ещё не
 * было ({@code -1}), значение не число либо метрик нет вовсе — живость не
 * доказана, и измеритель молчит: ложное «жив» есть то самое состояние,
 * против которого он заведён.
 *
 * <p><b>Контейнеры приходят ПАРАМЕТРОМ, а не берутся из реестра:</b>
 * реестр обходит вызывающий, и второй обход мог бы застать другой состав.
 */
@Component
public class ConsumerLivenessProvider {

    private final Long sessionTimeoutSeconds;

    public ConsumerLivenessProvider(ConsumerFactory<?, ?> consumerFactory) {
        this.sessionTimeoutSeconds = sessionTimeoutSeconds(consumerFactory.getConfigurationProperties());
    }

    /** Живы все переданные контейнеры; контейнеров нет — потреблять некому. */
    public Boolean isLive(Collection<MessageListenerContainer> containers) {
        if (isEmpty(containers)) {
            return Boolean.FALSE;
        }
        return containers.stream().allMatch(this::isLive);
    }

    private Boolean isLive(MessageListenerContainer container) {
        return isTrue(container.isRunning())
                && isFalse(isEmpty(container.getAssignedPartitions()))
                && isInTouchWithCoordinator(container.metrics().values());
    }

    /** На связи каждый клиент контейнера; клиентов нет — связи нет. */
    private Boolean isInTouchWithCoordinator(Collection<? extends Map<MetricName, ? extends Metric>> clients) {
        if (isEmpty(clients)) {
            return Boolean.FALSE;
        }
        return clients.stream().allMatch(this::isContactFresh);
    }

    /** Последний контакт клиента с координатором моложе срока сессии. */
    private Boolean isContactFresh(Map<MetricName, ? extends Metric> metrics) {
        Double youngest = null;
        for (Map.Entry<MetricName, ? extends Metric> metric : metrics.entrySet()) {
            if (isFalse(isCoordinatorContact(metric.getKey()))) {
                continue;
            }
            Double age = measuredAge(metric.getValue());
            if (nonNull(age) && (isNull(youngest) || age < youngest)) {
                youngest = age;
            }
        }
        return nonNull(youngest) && youngest < sessionTimeoutSeconds;
    }

    private Boolean isCoordinatorContact(MetricName name) {
        return Constants.ConsumerMetrics.LAST_HEARTBEAT_SECONDS_AGO.equals(name.name())
                || Constants.ConsumerMetrics.LAST_REBALANCE_SECONDS_AGO.equals(name.name());
    }

    /** Возраст отметки в секундах; пусто — отметки не было либо значение не число. */
    private Double measuredAge(Metric metric) {
        Object value = metric.metricValue();
        if (isFalse(value instanceof Number)) {
            return null;
        }
        Double age = ((Number) value).doubleValue();
        return age >= 0 ? age : null;
    }

    /** Срок сессии, объявленный фабрике; не объявлен — умолчание клиента. */
    private static Long sessionTimeoutSeconds(Map<String, Object> settings) {
        Object declared = settings.getOrDefault(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                ConsumerConfig.configDef().defaultValues().get(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG));
        Object millis = ConfigDef.parseType(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, declared, ConfigDef.Type.INT);
        return Duration.ofMillis(((Number) millis).longValue()).toSeconds();
    }
}
