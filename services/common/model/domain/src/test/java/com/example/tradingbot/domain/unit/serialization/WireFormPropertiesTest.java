package com.example.tradingbot.domain.unit.serialization;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Формы общей библиотеки, пересекающие сериализацию вне навеса: у заявки,
 * отдельной условной заявки и встроенной защиты свойств сериализатора ровно
 * столько, сколько объявленных полей, — предикат-свойство не уезжает ключом
 * без поля.
 *
 * <p>Кейсы — группа `U14` (.claude/tests/cases/jsonb-overlay-roundtrip.md).
 *
 * <p><b>Ожидаемое множество выводится из полей, и это не противоречит пину
 * словом у навеса.</b> Предмет клетки — не имя ключа, а ПРИНАДЛЕЖНОСТЬ
 * свойства полю: переименование уводит поле и его аксессор вместе, и клетка
 * законно остаётся зелёной, а неизъятый предикат даёт свойство, которого нет
 * среди полей, — и её краснит. Клейм о множестве полей читается отражением
 * (.claude/skills/test-code.md §«Уровень 2 — юниты библиотеки или модуля»).
 *
 * <p><b>Действительное множество спрашивается у интроспектора сериализатора,
 * а не пересказывается отражением:</b> какой метод считать свойством —
 * нульарный {@code is}-метод с типом-обёрткой, правила выведения имени —
 * решает библиотека, и пересказ её правил был бы вторым носителем,
 * расходящимся с ней молча. Интроспекция ничего не вызывает: пустую форму
 * она не сериализует, и отказ предиката на пустом состоянии клетку не
 * подменяет.
 */
class WireFormPropertiesTest {

    @ParameterizedTest(name = "U14.1 — {0}")
    @ValueSource(classes = {Order.class, AlgoOrder.class, AttachedAlgoOrder.class})
    @DisplayName("U14.1 — свойства сериализатора формы равны её объявленным полям")
    void u14_1_theSerializerPropertiesOfTheFormEqualItsDeclaredFields(Class<?> form) {
        Set<String> fields = declaredFields(form);

        assertThat(fields)
                .as("U14.1: у формы %s не нашлось ни одного поля — мерить было бы нечего",
                        form.getSimpleName())
                .isNotEmpty();
        assertThat(serializerProperties(form))
                .as("U14.1: форма %s — свойство без поля есть неизъятый предикат, поле без "
                        + "свойства — аксессор, которого сериализатор не опознал", form.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(fields);
    }

    /**
     * Контрольная форма с НЕизъятым предикатом-обёрткой: проба обязана его
     * увидеть, иначе зелёный `U14.1` значил бы слепоту точки наблюдения, а
     * не отсутствие дефекта.
     */
    @Test
    @DisplayName("U14.2 — неизъятый нульарный is-предикат с типом-обёрткой проба видит лишним свойством")
    void u14_2_anUnignoredWrapperPredicateIsSeenAsAnExtraProperty() {
        assertThat(declaredFields(PredicateLeakingForm.class)).containsExactly("status");
        assertThat(serializerProperties(PredicateLeakingForm.class))
                .as("U14.2: ключ `live` без поля — ровно то, что `U14.1` обязан ловить")
                .containsExactlyInAnyOrder("status", "live");
    }

    // --- материал кейсов --------------------------------------------------

    /** Имена нестатических полей формы и всех её предков, кроме {@link Object}. */
    private static Set<String> declaredFields(Class<?> form) {
        Set<String> names = new TreeSet<>();
        Class<?> type = form;
        while (nonNull(type) && isFalse(Object.class.equals(type))) {
            for (Field field : type.getDeclaredFields()) {
                if (isFalse(Modifier.isStatic(field.getModifiers())) && isFalse(field.isSynthetic())) {
                    names.add(field.getName());
                }
            }
            type = type.getSuperclass();
        }
        return names;
    }

    /** Имена свойств, которые сериализатор запишет у формы: у каждого есть аксессор чтения. */
    private static Set<String> serializerProperties(Class<?> form) {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description = mapper.getSerializationConfig()
                .introspect(mapper.constructType(form));
        return description.findProperties().stream()
                .filter(BeanPropertyDefinition::couldSerialize)
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Контрольная форма `U14.2`: одно поле и предикат без изъятия. */
    @Getter
    public static class PredicateLeakingForm {

        private String status;

        public Boolean isLive() {
            return "LIVE".equals(status);
        }
    }
}
