package com.example.bff.unit.stream;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;

/**
 * Запись, собранная строителем SSE и увиденная контейнером, — в виде,
 * пригодном для ассертов.
 *
 * <p><b>Читается ровно то, что уходит на провод.</b> Строитель каркаса
 * отдаёт кусками: текст скелета (`event:`, `id:`) и само содержимое
 * отдельным элементом без медиатипа. Отсюда признак разбора — наличие
 * медиатипа: он есть у скелета и пуст у содержимого.
 *
 * <p><b>Границы фреймов восстанавливаются, а не приходят готовыми:</b>
 * записи, отданные до подключения наблюдателя (переигрывание внутри
 * {@code open}), каркас выталкивает ОДНОЙ пачкой. Начало фрейма
 * опознаётся текстом {@code event:} — им начинается каждый.
 *
 * @param eventName класс записи, ушедший полем {@code event}
 * @param id        идентичность, ушедшая полем {@code id}; пусто — поля не было
 * @param payload   содержимое записи — то, что каркас сериализовал бы в тело
 */
public record SseFrame(String eventName, String id, Object payload) {

    private static final String EVENT_PREFIX = "event:";
    private static final String ID_PREFIX = "id:";

    /**
     * Разобрать поток кусков в записи.
     *
     * @param items всё, что каркас получил на запись, в порядке отдачи
     * @return записи в том же порядке
     */
    public static List<SseFrame> decode(List<DataWithMediaType> items) {
        List<SseFrame> frames = new ArrayList<>();
        String eventName = null;
        String id = null;
        Object payload = null;
        Boolean started = Boolean.FALSE;
        for (DataWithMediaType item : items) {
            if (isNull(item.getMediaType())) {
                payload = item.getData();
                continue;
            }
            String text = String.valueOf(item.getData());
            if (text.startsWith(EVENT_PREFIX)) {
                if (started) {
                    frames.add(new SseFrame(eventName, id, payload));
                }
                started = Boolean.TRUE;
                eventName = valueAfter(text, EVENT_PREFIX);
                id = null;
                payload = null;
                continue;
            }
            if (text.contains(ID_PREFIX)) {
                id = valueAfter(text.substring(text.indexOf(ID_PREFIX)), ID_PREFIX);
            }
        }
        if (started) {
            frames.add(new SseFrame(eventName, id, payload));
        }
        return frames;
    }

    private static String valueAfter(String text, String prefix) {
        String tail = text.substring(prefix.length());
        int lineEnd = tail.indexOf('\n');
        return lineEnd < 0 ? tail : tail.substring(0, lineEnd);
    }

    /** Несёт ли запись поле идентичности. */
    public Boolean carriesId() {
        return nonNull(id);
    }
}
