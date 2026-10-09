package com.example.connector.okx.box;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.connector.okx.util.OkxConstants;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Состояние demo-счёта, прочитанное у ПЛОЩАДКИ ключом только чтения, и
 * принудительная зачистка торговым ключом ({@code B11.3-D};
 * .claude/tests/case-material/connector-okx.md §«2. Инвариант восстановления
 * состояния stateful-кейса»).
 *
 * <p><b>Авторитет «вернулся ли счёт» — площадка, а не коннектор.</b> Чтение
 * коннектора есть мнение проверяемой системы о площадке; читатель здесь свой.
 *
 * <p><b>Радиус снимка — инструмент кейсов плюс настройки счёта, а не счёт
 * целиком, и это названо.</b> На том же demo-счёте стенда ведут сделки живые
 * стратегии ядра (`ETH-USDT-SWAP`, `SOL-USDT-SWAP` — .claude/skills/local-stand.md
 * §«Тестовые данные стенда»): их заявки и позиции меняются независимо от
 * набора, и снимок всего счёта краснел бы от чужого хода. Набор трогает один
 * инструмент, которого стратегии не ведут, — его сущности и плечо, — и
 * настройки счёта, общие всем инструментам: режим счёта и режим позиций.
 *
 * <p><b>Зачистка снимает ВСЁ живое на инструменте кейсов.</b> Это законно ровно
 * потому, что набор начинается только на чистом инструменте
 * ({@link State#instrumentClean()}): всё, что на нём появилось, поставил набор.
 */
final class DemoAccount {

    /** Семьи живых условных заявок: площадка отдаёт их только по семье. */
    private static final List<String> ALGO_FAMILIES = List.of(
            OkxConstants.ALGO_ORD_TYPE_CONDITIONAL + "," + OkxConstants.ALGO_ORD_TYPE_OCO,
            "trigger",
            OkxConstants.ALGO_ORD_TYPE_MOVE_STOP);

    private static final String LEVERAGE_INFO_PATH = "/api/v5/account/leverage-info";

    private DemoAccount() {
    }

    /**
     * Снимок состояния, относящегося к набору.
     *
     * @param readKey    ключ только чтения
     * @param instrument инструмент кейсов
     * @param record     префикс имени записи для стаба; пусто — не записывать
     */
    static State snapshot(DemoKeys readKey, String instrument, String record) {
        DemoExchange.Raw orders = liveOrdersRaw(readKey, instrument);
        Set<String> algos = new TreeSet<>();
        for (String family : ALGO_FAMILIES) {
            DemoExchange.Raw raw = DemoExchange.signedGet(readKey, OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH
                    + "?ordType=" + family + "&instType=" + OkxConstants.INST_TYPE_SWAP + "&instId=" + instrument)
                    .required();
            raw.data().forEach(algo -> algos.add(family + ":" + algo.get("algoId")));
            keep(record, "trade-orders-algo-pending-" + family.replace(',', '-'), raw);
        }
        DemoExchange.Raw positions = DemoExchange.signedGet(readKey, OkxConstants.ACCOUNT_POSITIONS_PATH
                + "?instType=" + OkxConstants.INST_TYPE_SWAP + "&instId=" + instrument).required();
        DemoExchange.Raw leverage = DemoExchange.signedGet(readKey, LEVERAGE_INFO_PATH
                + "?instId=" + instrument + "&mgnMode=" + OkxConstants.TD_MODE_ISOLATED).required();
        DemoExchange.Raw config = DemoExchange.signedGet(readKey, OkxConstants.ACCOUNT_CONFIG_PATH).required();
        keep(record, "trade-orders-pending", orders);
        keep(record, "account-positions-instrument", positions);
        keep(record, "account-leverage-info", leverage);
        keep(record, "account-config", config);
        Map<String, Object> settings = config.data().getFirst();
        return new State(
                orders.data().stream().map(order -> String.valueOf(order.get("ordId")))
                        .collect(Collectors.toCollection(TreeSet::new)),
                algos,
                positions.data().stream()
                        .filter(position -> isOpen(position.get("pos")))
                        .map(position -> position.get("mgnMode") + ":" + position.get("posSide") + ":"
                                + position.get("pos"))
                        .sorted()
                        .collect(Collectors.toList()),
                leverage.data().stream()
                        .map(entry -> entry.get("mgnMode") + ":" + entry.get("posSide") + ":" + entry.get("lever"))
                        .sorted()
                        .collect(Collectors.toList()),
                String.valueOf(settings.get("acctLv")),
                String.valueOf(settings.get("posMode")));
    }

    /** Живые заявки инструмента у площадки — сырым ответом. */
    static DemoExchange.Raw liveOrdersRaw(DemoKeys readKey, String instrument) {
        return DemoExchange.signedGet(readKey, OkxConstants.TRADE_ORDERS_PENDING_PATH
                + "?instType=" + OkxConstants.INST_TYPE_SWAP + "&instId=" + instrument).required();
    }

    /**
     * Принудительная зачистка инструмента кейсов торговым ключом: живые
     * заявки и условные снимаются, позиция закрывается по рынку.
     *
     * <p>Это ПОПЫТКА, а не доказательство: вернулся ли счёт, решает
     * следующий снимок.
     */
    static void forceClean(DemoKeys tradeKey, DemoKeys readKey, String instrument) {
        for (Map<String, Object> order : liveOrdersRaw(readKey, instrument).data()) {
            DemoExchange.signedPost(tradeKey, OkxConstants.TRADE_CANCEL_ORDER_PATH,
                    "{\"instId\":\"" + instrument + "\",\"ordId\":\"" + order.get("ordId") + "\"}");
        }
        for (String family : ALGO_FAMILIES) {
            String cancelPath = Objects.equals(OkxConstants.ALGO_ORD_TYPE_MOVE_STOP, family)
                    ? OkxConstants.TRADE_CANCEL_ADVANCE_ALGOS_PATH
                    : OkxConstants.TRADE_CANCEL_ALGOS_PATH;
            List<Map<String, Object>> algos = DemoExchange.signedGet(readKey,
                    OkxConstants.TRADE_ORDERS_ALGO_PENDING_PATH + "?ordType=" + family
                            + "&instType=" + OkxConstants.INST_TYPE_SWAP + "&instId=" + instrument)
                    .required().data();
            if (isEmpty(algos)) {
                continue;
            }
            DemoExchange.signedPost(tradeKey, cancelPath, algos.stream()
                    .map(algo -> "{\"instId\":\"" + instrument + "\",\"algoId\":\"" + algo.get("algoId") + "\"}")
                    .collect(Collectors.joining(",", "[", "]")));
        }
        List<Map<String, Object>> positions = DemoExchange.signedGet(readKey, OkxConstants.ACCOUNT_POSITIONS_PATH
                + "?instType=" + OkxConstants.INST_TYPE_SWAP + "&instId=" + instrument).required().data();
        for (Map<String, Object> position : positions) {
            if (isOpen(position.get("pos"))) {
                DemoExchange.signedPost(tradeKey, OkxConstants.TRADE_CLOSE_POSITION_PATH,
                        "{\"instId\":\"" + instrument + "\",\"mgnMode\":\"" + position.get("mgnMode")
                                + "\",\"posSide\":\"" + position.get("posSide") + "\",\"autoCxl\":true}");
            }
        }
    }

    private static void keep(String record, String name, DemoExchange.Raw raw) {
        if (isNotBlank(record)) {
            DemoRecordings.keep(name + "-" + record, raw.body());
        }
    }

    /** Строка позиции открыта: размер непуст и не ноль. */
    private static Boolean isOpen(Object size) {
        String text = String.valueOf(size);
        if (isBlank(text) || Objects.equals("null", text)) {
            return Boolean.FALSE;
        }
        return new BigDecimal(text).signum() != 0;
    }

    /**
     * Состояние счёта в радиусе набора.
     *
     * @param liveOrders    биржевые идентификаторы живых заявок инструмента
     * @param liveAlgos     семья и идентификатор живых условных заявок инструмента
     * @param positions     открытые позиции инструмента: режим маржи, сторона, размер
     * @param leverage      плечо инструмента в изолированной марже по сторонам
     * @param accountLevel  режим счёта
     * @param positionMode  режим позиций
     */
    record State(Set<String> liveOrders, Set<String> liveAlgos, List<String> positions, List<String> leverage,
                 String accountLevel, String positionMode) {

        /** На инструменте нет ничего живого: ни заявок, ни условных, ни позиции. */
        Boolean instrumentClean() {
            return isEmpty(liveOrders) && isEmpty(liveAlgos) && isEmpty(positions);
        }
    }
}
