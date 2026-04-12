package com.example.tradingbot.util;

import lombok.experimental.UtilityClass;

import java.util.Set;

public class Constant {

    @UtilityClass
    public class ExchangeNames {

        public static final String EXCHANGE_NAME_OKX = "OKX";
    }

    @UtilityClass
    public class ErrorCode {

        public static final String EXCHANGE_NOT_FOUND = "exchange.not.found";
        public static final String INSTRUMENT_NOT_FOUND = "instrument.not.found";
        public static final String DEAL_NOT_FOUND = "deal.not.found";
        public static final String ORDER_NOT_FOUND = "order.not.found";
        public static final String POSITION_NOT_FOUND = "position.not.found";
        public static final String ALGO_ORDER_NOT_FOUND = "algo.order.not.found";
        public static final String INSTRUMENT_ALREADY_EXISTS = "instrument.already.exists";
        public static final String CANDLE_GROUP_ALREADY_EXISTS = "candle.group.already.exists";
        public static final String CANDLE_GROUP_NOT_FOUND = "candle.group.not.found";
        public static final String EXCHANGE_ALREADY_EXISTS = "exchange.already.exists";
        public static final String RECONCILE_REPORT_NOT_FOUND = "reconcile.report.not.found";
        public static final String ANOMALY_REPORT_NOT_FOUND = "anomaly.report.not.found";
        public static final String CLIENT_SERVICE_NOT_FOUND = "client.service.not.found";

        public static final String ALGO_ORDER_NOT_FOUND_ON_EXCHANGE = "algo.order.not.found.on.exchange";

    }

    @UtilityClass
    public class Status {

        @UtilityClass
        public class Exchange {

            public static final String EXCHANGE_STATUS_CREATED = "CREATED";
        }


        @UtilityClass
        public class Order {

            public static final String ORDER_STATUS_CREATED = "CREATED";
            public static final String ORDER_STATUS_IN_PROGRESS = "IN_PROGRESS";
        }

        @UtilityClass
        public class AlgoOrder {

            public static final String ALGO_ORDER_STATUS_CREATED = "CREATED";
            public static final String ALGO_ORDER_STATUS_IN_PROGRESS = "IN_PROGRESS";
        }

        @UtilityClass
        public class Instrument {

            public static final String INSTRUMENT_STATUS_CREATED = "CREATED";
            public static final String INSTRUMENT_STATUS_SYNC = "SYNC";
            public static final String INSTRUMENT_STATUS_HOLD = "HOLD";
            public static final String INSTRUMENT_STATUS_ACTIVE = "ACTIVE";
            public static final String INSTRUMENT_STATUS_CANDLES_LOADING = "CANDLES_LOADING";

        }

        @UtilityClass
        public class CandleGroup {

            public static final String CANDLE_GROUP_STATUS_CREATED = "CREATED";
            public static final String CANDLE_GROUP_STATUS_BACK_FILL = "BACK_FILL";
            public static final String CANDLE_GROUP_STATUS_REPAIR = "REPAIR";
            public static final String CANDLE_GROUP_STATUS_SYNC = "SYNC";
            public static final String CANDLE_GROUP_STATUS_READY = "READY";
            public static final String CANDLE_GROUP_STATUS_ERROR = "ERROR";

            public static final Set<String> CANDLE_GROUP_ELIGIBLE_STATUSES =
                    Set.of(CANDLE_GROUP_STATUS_CREATED,
                           CANDLE_GROUP_STATUS_BACK_FILL,
                           CANDLE_GROUP_STATUS_REPAIR,
                           CANDLE_GROUP_STATUS_SYNC);
        }
    }

    @UtilityClass
    public class Service {

        public static final String DEFAULT_TRADE_MODE = "isolated";
        public static final String DEFAULT_POSITION_MODE = "NONE";
        public static final int PRICE_PRECISION = 50;
        public static final int PRICE_SCALE = 30;
    }

}
