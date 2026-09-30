package com.example.marketdata.box;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

/**
 * Площадка с настоящей историей для стаба коннектора: на запрос истории
 * отдаёт окно ряда, ЧЕСТНО отвечающее на курсор и лимит запроса.
 *
 * <p><b>Почему не заготовленный ответ.</b> Окно починки выводит бинарный
 * поиск по count, и курсор его запроса кейсу заранее не известен:
 * заготовка под каждый курсор пересказала бы алгоритм в тесте, то есть
 * проверяла бы реализацию против её же копии, а один общий ответ на любой
 * курсор залатал бы дыру любой ширины одним проходом и отнял бы у кейса
 * предмет. Здесь площадка ведёт себя по контракту соседа — свечи строго
 * старше курсора, не больше лимита, — а решение, какой курсор спросить,
 * остаётся за предметом.
 *
 * <p><b>Ряд задаётся параметрами заготовки, а не полем расширения:</b>
 * расширение одно на JVM, а заготовки сбрасываются перед каждой клеткой
 * ({@link ConnectorStub#reset()}), и ряд уходит вместе с ними.
 */
final class HistoryWindow implements ResponseDefinitionTransformerV2 {

    /** Имя, которым заготовка называет это расширение. */
    static final String NAME = "history-window";

    /** Параметр заготовки: открытие первого бара ряда площадки. */
    static final String FIRST_OPEN = "firstOpenTimestamp";

    /** Параметр заготовки: длительность бара. */
    static final String STEP = "stepMillis";

    /** Параметр заготовки: число баров ряда площадки. */
    static final String COUNT = "count";

    /** Цена первого бара ряда: цена бара растёт на единицу с его номером. */
    private static final Integer BASE_PRICE = 50000;

    /** Курсор запроса истории: свечи строго старше него. */
    private static final String CURSOR = "afterMillis";

    /** Лимит запроса истории. */
    private static final String LIMIT = "limit";

    /**
     * Курсор запроса без значения: «с самого свежего». Половина предела, а
     * не предел, — разность с открытием первого бара не переполняется.
     */
    private static final long NO_CURSOR = Long.MAX_VALUE / 2L;

    @Override
    public String getName() {
        return NAME;
    }

    /** Только заготовки, назвавшие расширение: прочие ответы стаба им не трогаются. */
    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        Parameters parameters = serveEvent.getTransformerParameters();
        long first = Long.parseLong(parameters.getString(FIRST_OPEN));
        long step = Long.parseLong(parameters.getString(STEP));
        long count = Long.parseLong(parameters.getString(COUNT));
        LoggedRequest request = serveEvent.getRequest();
        long limit = Long.parseLong(request.queryParameter(LIMIT).firstValue());
        long newest = Math.min(count - 1L, Math.ceilDiv(cursor(request) - first, step) - 1L);
        long oldest = Math.max(0L, newest - limit + 1L);
        String body = newest < 0L
                ? Feed.empty()
                : Feed.candles(first + oldest * step, step, (int) (newest - oldest + 1L), BASE_PRICE + (int) oldest);
        return ResponseDefinitionBuilder.responseDefinition()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)
                .build();
    }

    /** Курсор запроса; без курсора — «с самого свежего», как у площадки. */
    private static long cursor(LoggedRequest request) {
        QueryParameter cursor = request.queryParameter(CURSOR);
        if (cursor.isPresent() && isNotBlank(cursor.firstValue())) {
            return Long.parseLong(cursor.firstValue());
        }
        return NO_CURSOR;
    }
}
