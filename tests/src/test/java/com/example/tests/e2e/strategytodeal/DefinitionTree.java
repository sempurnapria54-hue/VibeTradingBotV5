package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.isNull;

/**
 * Подпись дерева определения — перечень его узлов, по которому копию у ядра
 * сверяют со снимком на проводе ПОЭЛЕМЕНТНО
 * (.claude/tests/cases/e2e-strategy-to-deal.md, кейсы {@code E2.1},
 * {@code E2.5}).
 *
 * <p><b>Узел называется своими доменными ключами, а не ключом базы:</b>
 * деталь — фазой, транш — ключом транша, шаг — статусом и позицией в своём
 * перечне (с нуля, по порядку колонки позиции — от её собственной базы
 * отсчёта сверка не зависит), действие — ключом действия. Ключи баз у двух сторон разные по
 * построению (docs/architecture/data-ownership.md §Идентификаторы), и
 * сверка по ним мерила бы не дерево, а совпадение счётчиков.
 */
final class DefinitionTree {

    private static final String NONE = "-";

    private DefinitionTree() {
    }

    /**
     * Подпись снимка определения из содержимого записи активации.
     *
     * @param definition узел {@code definition} содержимого
     * @return узлы дерева в каноническом порядке
     */
    static List<String> ofSnapshot(JsonNode definition) {
        List<String> nodes = new ArrayList<>();
        for (JsonNode detail : definition.path("details")) {
            String phase = detail.path("marketPhaseType").asString();
            nodes.add("detail:" + phase);
            for (JsonNode tranche : detail.path("tranches")) {
                String key = tranche.path("key").asString();
                nodes.add("tranche:" + phase + "/" + key);
                addSteps(nodes, phase, key, tranche.path("stepsByStatus"));
            }
            addSteps(nodes, phase, NONE, detail.path("stepsByStatus"));
        }
        definition.path("indicatorSettings").forEach(setting ->
                nodes.add("indicator:" + setting.path("key").asString()));
        definition.path("marketStructureSettings").forEach(setting ->
                nodes.add("structure:" + setting.path("key").asString()));
        return nodes.stream().sorted().toList();
    }

    /**
     * Подпись копии определения в базе ядра.
     *
     * @param core       база ядра
     * @param internalId идентичность определения
     * @return узлы дерева в каноническом порядке
     */
    static List<String> ofCopy(Database core, String internalId) {
        List<String> nodes = new ArrayList<>();
        core.query("""
                select d.market_phase_type as phase from strategy_details d
                join strategies s on s.id = d.strategy_id where s.internal_id = ?
                """, internalId).forEach(row -> nodes.add("detail:" + row.get("phase")));
        core.query("""
                select d.market_phase_type as phase, t.key as key from strategy_tranches t
                join strategy_details d on d.id = t.strategy_detail_id
                join strategies s on s.id = d.strategy_id where s.internal_id = ?
                """, internalId).forEach(row -> nodes.add("tranche:" + row.get("phase") + "/" + row.get("key")));
        core.query("""
                select d.market_phase_type as phase, t.key as tranche,
                       coalesce(st.tranche_status, st.deal_status) as status,
                       row_number() over (partition by d.id, st.strategy_tranche_id,
                           coalesce(st.tranche_status, st.deal_status) order by st.step_index) - 1 as position
                from strategy_steps st
                left join strategy_tranches t on t.id = st.strategy_tranche_id
                join strategy_details d on d.id = coalesce(st.strategy_detail_id, t.strategy_detail_id)
                join strategies s on s.id = d.strategy_id where s.internal_id = ?
                """, internalId).forEach(row -> nodes.add(step(row)));
        core.query("""
                with steps as (
                    select st.id as id, d.market_phase_type as phase, t.key as tranche,
                           coalesce(st.tranche_status, st.deal_status) as status,
                           row_number() over (partition by d.id, st.strategy_tranche_id,
                               coalesce(st.tranche_status, st.deal_status) order by st.step_index) - 1 as position
                    from strategy_steps st
                    left join strategy_tranches t on t.id = st.strategy_tranche_id
                    join strategy_details d on d.id = coalesce(st.strategy_detail_id, t.strategy_detail_id)
                    join strategies s on s.id = d.strategy_id where s.internal_id = ?)
                select steps.phase, steps.tranche, steps.status, steps.position, a.key as action
                from strategy_actions a join steps on steps.id = a.strategy_step_id
                """, internalId).forEach(row -> nodes.add("action:" + stepPath(row) + "/" + row.get("action")));
        core.query("""
                select i.key as key from strategy_indicator_settings i
                join strategies s on s.id = i.strategy_id where s.internal_id = ?
                """, internalId).forEach(row -> nodes.add("indicator:" + row.get("key")));
        core.query("""
                select m.key as key from strategy_market_structure_settings m
                join strategies s on s.id = m.strategy_id where s.internal_id = ?
                """, internalId).forEach(row -> nodes.add("structure:" + row.get("key")));
        return nodes.stream().sorted().toList();
    }

    /**
     * Ключи базы ядра у всех узлов копии: неизменность дерева на смене статуса
     * читается ими — ни одна строка поддерева не пересоздана.
     */
    static List<Object> nodeKeys(Database core, String internalId) {
        List<Object> keys = new ArrayList<>();
        core.query("""
                select distinct d.id as detail, st.id as step, a.id as action
                from strategy_details d
                join strategies s on s.id = d.strategy_id
                left join strategy_tranches t on t.strategy_detail_id = d.id
                left join strategy_steps st on st.strategy_detail_id = d.id or st.strategy_tranche_id = t.id
                left join strategy_actions a on a.strategy_step_id = st.id
                where s.internal_id = ? order by 1, 2, 3
                """, internalId).forEach(row -> keys.add(List.of(String.valueOf(row.get("detail")),
                String.valueOf(row.get("step")), String.valueOf(row.get("action")))));
        return keys;
    }

    private static void addSteps(List<String> nodes, String phase, String tranche, JsonNode stepsByStatus) {
        stepsByStatus.properties().forEach(entry -> {
            Integer position = 0;
            for (JsonNode step : entry.getValue()) {
                String path = phase + "/" + tranche + "/" + entry.getKey() + "/" + position;
                nodes.add("step:" + path);
                for (JsonNode action : step.path("actions")) {
                    nodes.add("action:" + path + "/" + action.path("key").asString());
                }
                position++;
            }
        });
    }

    private static String step(Map<String, Object> row) {
        return "step:" + stepPath(row);
    }

    private static String stepPath(Map<String, Object> row) {
        Object tranche = row.get("tranche");
        return row.get("phase") + "/" + (isNull(tranche) ? NONE : tranche) + "/" + row.get("status") + "/"
                + row.get("position");
    }
}
