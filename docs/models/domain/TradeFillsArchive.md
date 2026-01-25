## 6) Доменная модель для архива fills (сделок)

```java
package com.example.tradingbot.domain.model.exchange;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Сущность для архива fills (сделок).
 *
 * Идея:
 * - POST /trade/fills-archive: заполняет поля requestLinkAlreadyExists + exchangeRequestReceivedAt
 * - GET  /trade/fills-archive: заполняет fileHref + state + exchangeLinkGeneratedAt
 */
@Getter
@Setter
public class TradeFillsArchive {

    /** Внутренний идентификатор записи в БД (если используешь). */
    private Long id;

    /** Год, за который запрашиваем архив (4 цифры), например "2025". */
    private String year;

    /** Квартал: Q1/Q2/Q3/Q4 (регистр важен). */
    private String quarter;

    /**
     * result из POST:
     * true  — ссылка уже была
     * false — ссылка ещё не готова, биржа начала генерацию
     */
    private Boolean requestLinkAlreadyExists;

    /** ts из POST: когда биржа впервые получила запрос (ms -> Instant). */
    private Instant exchangeRequestReceivedAt;

    /**
     * state из GET:
     * finished — ссылка готова
     * ongoing  — ещё генерируется
     * failed   — ошибка (обычно нужно применить заново)
     */
    private ArchiveState state;

    /** fileHref из GET: ссылка на файл с архивом (обычно временная). */
    private String fileHref;

    /** ts из GET: когда биржа сформировала ссылку на скачивание (ms -> Instant). */
    private Instant exchangeLinkGeneratedAt;

    // --------- Auditing (DB) ---------

    /** createdAt — когда запись создана в нашей БД. */
    private Instant createdAt;

    /** updatedAt — когда запись обновлена в нашей БД. */
    private Instant updatedAt;

    /** createdBy — кем создана (опционально). */
    private String createdBy;

    /** updatedBy — кем обновлена (опционально). */
    private String updatedBy;

    public enum ArchiveState {
        FINISHED,
        ONGOING,
        FAILED
    }
}
```

---

## 7) Маппинг (YAML)

### 7.1 POST `POST /api/v5/trade/fills-archive` (request body) → доменная модель

```yaml
year: year                                         # год (4 цифры)
quarter: quarter                                   # квартал Q1..Q4 (регистр важен)
```

### 7.2 POST `POST /api/v5/trade/fills-archive` (response) → доменная модель

```yaml
# data[0]
data[0].result: requestLinkAlreadyExists            # "true"/"false" -> Boolean
data[0].ts: exchangeRequestReceivedAt               # ms -> Instant
```

### 7.3 GET `GET /api/v5/trade/fills-archive` (response) → доменная модель (на будущее)

```yaml
# data[0]
data[0].state: state                               # finished/ongoing/failed -> enum (FINISHED/ONGOING/FAILED)
data[0].fileHref: fileHref                          # ссылка на файл
data[0].ts: exchangeLinkGeneratedAt                 # ms -> Instant
```

**Правила конвертации (чтобы было “рабоче”):**

* Пустые строки `""` → `null`.
* `result` приходит строкой `"true"/"false"` → `Boolean`.
* `ts` (миллисекунды строкой) → `Instant.ofEpochMilli(Long.parseLong(value))`.
* `state`:

    * `finished -> FINISHED`
    * `ongoing -> ONGOING`
    * `failed -> FAILED`