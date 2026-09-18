# Ревью кейсов `platform-shared-logic` по критерию валидности

## На какой вопрос отвечает этот файл

Каков исход адверсариального ревью документа кейсов
`.claude/tests/cases/platform-shared-logic.md`.

## Охват

**Предмет** — шесть форм периметра, живущих десятью классами в четырёх
деревьях: три формы в общем артефакте `services/common/platform` одним
экземпляром (охрана джобы, резолв актора, точки входа отказа доступа), три
копиями у сервисов (разбор отказа соседа ×2, провайдер служебного токена ×3,
переносчик контекста хода ×2). **Тринадцатый предмет уровня 2** перечня
под-шага 1 `CODE` и **пятый из шести**, введённых правкой перечня 2026-09-17
(`.claude/decisions/test-contour-design-pass.md` §«Перечень предметов и
порядок работы»), **первый круг документа**.

Критерий апрува — `.claude/skills/test-review.md` §«Критерий апрува документа
кейсов»; потолок петли — три круга. Передача пришла с таблицей самопроверки
автора
(`.claude/work/progress/phase-2-step-12-hand-test-platform-shared-logic.md`)
— по строке на каждый из 96 кейсов; без неё круг не открывался бы.

**Метка своей дельты отбита до первой правки** (`git write-tree`,
`.claude/rules/edit-kind-obligations.md` §«Предмет обязанностей — дельта
захода»). Дельта круга: этот отчёт, три секции бэклога, хроника шага и
снапшот. **Документ кейсов круг НЕ правит** — ревьюер не чинит
(`.claude/skills/test-review.md` §«Назначение и границы», §«Петля с tester»);
дерево кода круг **читал** и **прогонял пробами**, но не менял.

**Новое против двенадцати апрувленных документов уровня: документ вводит
ось «у предмета нет дерева прогона, и его выбор — часть кейса», и ревью
обязано применить эту ось к его собственным клеткам** — та же форма, которой
круг `bff-perimeter-logic` проверял введённую им ось. Отсюда предмет круга:
у каждой клетки спрошено не «верно ли утверждение», а **«чем именно оно
наблюдается в том дереве, которое документ ей назначил»**.

Ось оправдалась: **два гейтящих дефекта из трёх пришли ровно оттуда** —
клетка, чьё наблюдение требует дерева, которого базовая сборка группы не
даёт, и клетка, чей предмет наблюдается только командой корпуса, а
утверждение о нём не сверено с деревьями.

**Что прочитано целиком** (носители, а не пассажи по имени величины):

| Носитель | Зачем |
|---|---|
| все десять классов предмета в четырёх деревьях | дом половины ожиданий: 25 звеньев `Z1`-`Z25` документа |
| `docs/models/domain/other/Auditable.md` целиком | дом группы `U3` и половины группы `U13`; здесь нашлась `N-2` |
| `docs/models/domain/other/AccessDenial.md` целиком | дом групп `U4`-`U6`: инварианты, структура, перечень исходов, персистентность |
| `docs/rules/error-handling-policy.md` §«Внешняя поверхность» целиком, с обоими вложенными разделами | дом двух исходов отказа доступа и формы ответа |
| `docs/rules/runtime-error-classification.md` целиком | дом группы `U7`; здесь нашлась `Ф-1` |
| `ErrorApiResponse`, `AccessDenialRecorder`, `Constants.Audit`, `ConnectorProperties`, обе копии `PeerReadException` | носители цитат, на которые опираются клетки |
| `SecurityConfig` четырёх сервисов, `JpaAuditConfig` шести, `@Import` восьми приложений | популяция группы `U13` — сверена командами, а не принята |
| библиотеки `spring-security-core`, `spring-security-oauth2-client`, `spring-web`, `jackson-databind` | восемь звеньев с пометкой «прогоном не проверено» — **проверены прогоном** (ниже) |

## Механические оси критерия — команды и их исход

### Ось 1. Метки уникальны, счёт документа сходится

```bash
export LC_ALL=C.UTF-8
grep -coE '^\| U[0-9]+\.[0-9]+ \|' .claude/tests/cases/platform-shared-logic.md
grep -oE '^\| U[0-9]+\.[0-9]+ \|' .claude/tests/cases/platform-shared-logic.md | sort -u | wc -l
grep -cE '^## U[0-9]+ ' .claude/tests/cases/platform-shared-logic.md
```

Исход: 102 строки, **96 уникальных меток**, **14 групп**. Шесть повторов —
строки таблицы §«Состояния, недостижимые обычной тропой», где та же метка
называется второй раз намеренно. Клейм «96 кейсов в 14 группах» сходится.

### Ось 2. Цитаты носителей разрешаются в корпусе

Ось введена этим кругом: клетка, чей носитель — **цитата** дока или javadoc,
проверяема механически, и выдуманная цитата глазу невидима — она читается
как верная.

```bash
export LC_ALL=C.UTF-8
mkdir -p target/probe-review && cat > target/probe-review/quotes.py <<'PY'
import re, io, glob
DOC = '.claude/tests/cases/platform-shared-logic.md'
doc = io.open(DOC, encoding='utf-8').read()
files = {}
for pat in ('docs/**/*.md', 'docs/**/*.json', '.claude/**/*.md', 'services/**/*.java'):
    for p in glob.glob(pat, recursive=True):
        q = p.replace(chr(92), '/')
        if q == DOC:
            continue
        try:
            files[q] = io.open(p, encoding='utf-8').read()
        except Exception:
            pass
def norm(s):
    s = re.sub(r'(?m)^\s*\*\s?', ' ', s)
    s = re.sub(r'<[^>]{1,12}>', '', s)
    s = s.replace('{@code ', '')
    s = re.sub(r'[*`"„“}]', '', s)
    return re.sub(r'\s+', ' ', s).strip().lower()
nfiles = {k: norm(v) for k, v in files.items()}
seen, miss, hits = set(), [], {}
for q in re.findall(r'«([^«»]{12,})»', doc):
    for part in q.split('…'):
        p = norm(part.strip(' .,;:'))
        if len(p) < 12 or p in seen:
            continue
        seen.add(p)
        where = [k for k, v in nfiles.items() if p in v]
        (hits.setdefault(p, where) if where else miss.append(p))
print('fragments:', len(seen), '| not found:', len(miss))
for m in miss:
    print('  MISS:', m[:160])
for p, w in sorted(hits.items()):
    if not any(k.startswith('docs/') or k.startswith('.claude/rules/') for k in w):
        print('  ONLY-OUTSIDE-DOCS:', p[:110], '->', w[:3])
PY
py target/probe-review/quotes.py
```

Скрипт берёт все фрагменты в «кавычках-ёлочках» длиной от 12 знаков, снимает
разметку markdown и javadoc-звёздочки, приводит к нижнему регистру и ищет
каждый в `docs/**`, `.claude/**`, `services/**`. Исход: **92 уникальных
фрагмента**, 12 не найдены нигде — и все двенадцать оказались ложными
срабатываниями оси: собственные формулировки документа, его же имена
разделов и цитаты с неотмеченным эллипсисом (`Ф-7`).

**Гейтящее дало не это, а вторая половина оси** — «где именно фрагмент
найден». Фрагменты, нашедшиеся только в коде, пробах и рабочих файлах, но
**не** в `docs/**` и не в `.claude/rules/**`, печатаются отдельно: там
клетка называет доковый пассаж, а цитата в нём не живёт. Так найдены `N-2`
и `Ф-1`.

### Ось 3. Популяция группы `U13` сверена своими же командами

Все пять команд группы прогнаны; выдача сверена с таблицей «Кто какую форму
забрал» и с клетками `U13.1`-`U13.9`.

```bash
export LC_ALL=C.UTF-8
grep -rn '@Import' --include=*.java services/*/src/main
grep -rln 'SecurityFilterChain' --include=*.java services/*/src/main \
  | xargs grep -ln 'authenticationEntryPoint'
grep -rn 'AuditorAware<String> auditorAware' -A 3 --include=*.java services/*/src/main
grep -rc '^\s*@Scheduled' --include=*.java services/*/src/main | grep -v ':0$'
grep -rn 'runExclusively' --include=*.java services/*/src/main | wc -l
```

Исход — **сходится целиком**: охрана джобы у пяти, резолв актора у пяти,
точки входа отказа у четырёх, исходящая идентичность у трёх; у
`connector-okx` не забрано ничего. Точки входа проведены ровно у тех четырёх,
кто их забрал. Шесть `JpaAuditConfig`, пятеро делегируют в поставщик, шестой
(`market-data`) отдаёт константу `WRITER = "market-data"`. Восемнадцать
`@Scheduled`-методов, пятнадцать вызовов охраны; три невзятия —
`ReceptionStateJob` у `audit` и `statistics`, `StreamPulseJob` у `bff` — у
всех трёх такт `fixedDelayString` и ни одного фасада в дереве. **Ни одного
расхождения с таблицей документа.**

### Ось 4. Звенья библиотек проверены ПРОГОНОМ, а не чтением

Восемь звеньев документа несут пометку «прогоном не проверено». Круг
прогнал их на тех самых артефактах локального репозитория, которые видит
сборка.

Проба одна и **самодостаточна**: текст ниже воспроизводит её целиком.

```bash
mkdir -p target/probe-review && cat > target/probe-review/LinkProbe.java <<'JAVA'
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.web.client.UnknownHttpStatusCodeException;

public class LinkProbe {

    static class Tok extends AbstractAuthenticationToken {
        private final Object principal;
        Tok(Object p, List<? extends GrantedAuthority> a) { super(a); this.principal = p; }
        public Object getCredentials() { return null; }
        public Object getPrincipal() { return principal; }
    }

    /** форма ErrorApiResponse: @Getter @Builder на private final полях */
    public static class Err {
        private final String code;
        private final String reason;
        private final String message;
        private final OffsetDateTime occurredAt;
        Err(String c, String r, String m, OffsetDateTime o) { code = c; reason = r; message = m; occurredAt = o; }
        public String getCode() { return code; }
        public String getReason() { return reason; }
        public String getMessage() { return message; }
        public OffsetDateTime getOccurredAt() { return occurredAt; }
    }

    static void probe(String name, Runnable r) {
        try { r.run(); System.out.println("NO-THROW " + name); }
        catch (Throwable e) { System.out.println("THROWS   " + name + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    public static void main(String[] args) throws Exception {
        Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        probe("Z1 computeIfAbsent(null)", () -> locks.computeIfAbsent(null, n -> new ReentrantLock()));

        ReentrantLock l = new ReentrantLock();
        System.out.println("Z4 tryLock=" + l.tryLock() + " reentrant=" + l.tryLock() + " holds=" + l.getHoldCount());

        System.out.println("Z6 getName(null principal)=[" + new Tok(null, List.of()).getName() + "]");

        var u599 = new UnknownHttpStatusCodeException(599, "x", null, null, null);
        var u299 = new UnknownHttpStatusCodeException(299, "x", null, null, null);
        System.out.println("Z14 599.is5xx=" + u599.getStatusCode().is5xxServerError()
                + " 299.is5xx=" + u299.getStatusCode().is5xxServerError()
                + " isRestClientResponseException=" + (u599 instanceof RestClientResponseException));
        System.out.println("Z15 UnknownContentTypeException super=" + UnknownContentTypeException.class.getSuperclass().getSimpleName()
                + " toRestClientResponseException=" + RestClientResponseException.class.isAssignableFrom(UnknownContentTypeException.class)
                + " toRestClientException=" + RestClientException.class.isAssignableFrom(UnknownContentTypeException.class));

        ClientRegistration reg = ClientRegistration.withRegistrationId("peer")
                .clientId("cid").clientSecret("sec")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost/token").build();
        probe("Z17 OAuth2AuthorizedClient(null token)", () -> new OAuth2AuthorizedClient(reg, "p", null));
        OAuth2AccessToken t = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "abc",
                Instant.now(), Instant.now().plusSeconds(60));
        System.out.println("Z17 control accessToken=" + new OAuth2AuthorizedClient(reg, "p", t).getAccessToken().getTokenValue());
        probe("Z21 withClientRegistrationId(empty)", () -> OAuth2AuthorizeRequest.withClientRegistrationId("").principal("p").build());
        probe("Z21 withClientRegistrationId(null)", () -> OAuth2AuthorizeRequest.withClientRegistrationId(null).principal("p").build());

        ObjectMapper m = new ObjectMapper();
        probe("Z11 writeValueAsString(OffsetDateTime) без JavaTimeModule", () -> {
            try { m.writeValueAsString(OffsetDateTime.now(ZoneOffset.UTC)); }
            catch (Exception e) { throw new IllegalStateException(e.getClass().getSimpleName()); }
        });
        String json = m.writeValueAsString(new Err("ACCESS_UNAUTHENTICATED", null, "Unauthorized", null));
        System.out.println("F9 serialized=" + json + " fieldNames=" + m.readTree(json).properties().size()
                + " publicCtors=" + Err.class.getConstructors().length);
        probe("F9 readValue(json, Err.class)", () -> {
            try { m.readValue(json, Err.class); }
            catch (Exception e) { throw new IllegalStateException(e.getClass().getSimpleName() + " (no Creators)"); }
        });

        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        Tok holder = new Tok("holder", List.of());
        holder.setAuthenticated(true);
        ctx.setAuthentication(holder);
        SecurityContextHolder.setContext(ctx);
        SimpleAsyncTaskExecutor pool = new SimpleAsyncTaskExecutor();
        pool.setConcurrencyLimit(1);
        AsyncTaskExecutor wrapped = new DelegatingSecurityContextAsyncTaskExecutor(pool);
        AtomicReference<String> seen1 = new AtomicReference<>("?"), thr1 = new AtomicReference<>("?");
        AtomicReference<String> seen2 = new AtomicReference<>("?"), thr2 = new AtomicReference<>("?");
        AtomicReference<String> bare = new AtomicReference<>("?");
        wrapped.submit(() -> {
            thr1.set(Thread.currentThread().getName());
            var au = SecurityContextHolder.getContext().getAuthentication();
            seen1.set(au == null ? "null" : au.getName());
            throw new IllegalStateException("boom");
        });
        Thread.sleep(400);
        SecurityContextHolder.clearContext();
        wrapped.submit(() -> {
            thr2.set(Thread.currentThread().getName());
            var au = SecurityContextHolder.getContext().getAuthentication();
            seen2.set(au == null ? "null" : au.getName());
            return null;
        });
        Thread.sleep(400);
        SecurityContextHolder.setContext(ctx);
        pool.submit(() -> {
            var au = SecurityContextHolder.getContext().getAuthentication();
            bare.set(au == null ? "null" : au.getName());
        });
        Thread.sleep(400);
        SecurityContextHolder.clearContext();
        System.out.println("Z22 task1(throws) saw=" + seen1.get() + " task2(empty caller) saw=" + seen2.get()
                + " | bare executor saw=" + bare.get());
        System.out.println("N-3 SimpleAsyncTaskExecutor t1=" + thr1.get() + " t2=" + thr2.get()
                + " sameThread=" + thr1.get().equals(thr2.get()));
    }
}
JAVA
M="C:/Users/RomanKrd/.m2/repository"
CP="$M/org/springframework/security/spring-security-core/7.0.0/spring-security-core-7.0.0.jar;$M/org/springframework/security/spring-security-oauth2-client/7.0.0/spring-security-oauth2-client-7.0.0.jar;$M/org/springframework/security/spring-security-oauth2-core/7.0.0/spring-security-oauth2-core-7.0.0.jar;$M/org/springframework/spring-web/7.0.1/spring-web-7.0.1.jar;$M/org/springframework/spring-core/7.0.1/spring-core-7.0.1.jar;$M/org/springframework/spring-context/7.0.1/spring-context-7.0.1.jar;$M/org/springframework/spring-beans/7.0.1/spring-beans-7.0.1.jar;$M/org/springframework/spring-jcl/6.1.11/spring-jcl-6.1.11.jar;$M/com/fasterxml/jackson/core/jackson-databind/2.20.1/jackson-databind-2.20.1.jar;$M/com/fasterxml/jackson/core/jackson-core/2.20.1/jackson-core-2.20.1.jar;$M/com/fasterxml/jackson/core/jackson-annotations/2.20/jackson-annotations-2.20.jar"
"$HOME/.jdks/corretto-25.0.3/bin/java" -cp "$CP" target/probe-review/LinkProbe.java
```

Выдача прогона 2026-09-18 (строка предупреждения библиотеки о гранте снята):

```text
THROWS   Z1 computeIfAbsent(null) -> NullPointerException: null
Z4 tryLock=true reentrant=true holds=2
Z6 getName(null principal)=[]
Z14 599.is5xx=true 299.is5xx=false isRestClientResponseException=true
Z15 UnknownContentTypeException super=RestClientException toRestClientResponseException=false toRestClientException=true
THROWS   Z17 OAuth2AuthorizedClient(null token) -> IllegalArgumentException: accessToken cannot be null
Z17 control accessToken=abc
THROWS   Z21 withClientRegistrationId(empty) -> IllegalArgumentException: clientRegistrationId cannot be empty
THROWS   Z21 withClientRegistrationId(null) -> IllegalArgumentException: clientRegistrationId cannot be empty
THROWS   Z11 writeValueAsString(OffsetDateTime) без JavaTimeModule -> IllegalStateException: InvalidDefinitionException
F9 serialized={"code":"ACCESS_UNAUTHENTICATED","reason":null,"message":"Unauthorized","occurredAt":null} fieldNames=4 publicCtors=0
THROWS   F9 readValue(json, Err.class) -> IllegalStateException: InvalidDefinitionException (no Creators)
Z22 task1(throws) saw=holder task2(empty caller) saw=null | bare executor saw=null
N-3 SimpleAsyncTaskExecutor t1=SimpleAsyncTaskExecutor-1 t2=SimpleAsyncTaskExecutor-2 sameThread=false
```

**Ловушка среды, названная ради следующего захода:** classpath Windows-JDK в
Git Bash пишется **драйв-буквенной** формой (`C:/Users/...`) и разделителем
`;`; POSIX-форма `/c/Users/...` даёт «package does not exist» на каждом
импорте, то есть отказ читается как отсутствие библиотеки.

| Звено | Что утверждал документ | Исход прогона |
|---|---|---|
| Z1 | пустой ключ отказывает до взятия замка | **подтверждено**: `ConcurrentHashMap#computeIfAbsent(null, …)` → `NullPointerException` |
| Z4 | вложенный вызов из удерживающего потока проходит | **подтверждено**: `tryLock` дважды `true`, счётчик удержаний 2 |
| Z6 | у принятого токена с пустым принципалом имя — пустая строка | **подтверждено**: `AbstractAuthenticationToken#getName()` → строка длины 0 |
| Z14 | развилка идёт по диапазону, `599` читается как `5xx` | **подтверждено**: `599 → is5xxServerError()=true`, `299 → false`; `UnknownHttpStatusCodeException` — наследник `RestClientResponseException` |
| Z15 | `UnknownContentTypeException` попадает в широкий хвост | **подтверждено**: прямой предок — `RestClientException`, к `RestClientResponseException` класс **не** приводится |
| Z17 | конструктор `OAuth2AuthorizedClient` пустого токена не принимает | **подтверждено**: `IllegalArgumentException: accessToken cannot be null`; пробел `G1` стои́т верно |
| Z21 | пустой и отсутствующий идентификатор регистрации отказывают до менеджера | **подтверждено**: `IllegalArgumentException: clientRegistrationId cannot be empty` на обоих входах, из `withClientRegistrationId` |
| Z11 | сборщик без модуля времени отказывает на `OffsetDateTime` | **подтверждено**: `InvalidDefinitionException: Java 8 date/time type not supported by default` |
| Z22 | обёртка переносит контекст и очищает его после исполнения | **подтверждено**: задача под `holder` видит `holder`, следующая при пустом контексте вызывающего видит пустоту; голый исполнитель контекста не переносит (контроль `U11.2` верен) |

**Восемь из восьми звеньев библиотек подтверждены.** Пометка «прогоном не
проверено» у автора была честной, и снимать её — работа под-шага 3, не эта.

### Ось 5. Тождество копий сверено `diff`, а не чтением

Клетка `U12.6` — единственная в документе, чьё утверждение о **тексте**
копий проверяемо одной командой. Прогнана:

```bash
diff services/trading-core/src/main/java/com/example/tradingcore/integration/internal/api/PeerCall.java \
     services/strategies/src/main/java/com/example/strategies/integration/internal/api/PeerCall.java
diff services/trading-core/src/main/java/com/example/tradingcore/config/AsyncActorContextConfigurer.java \
     services/strategies/src/main/java/com/example/strategies/config/AsyncActorContextConfigurer.java
diff services/trading-core/src/main/java/com/example/tradingcore/integration/internal/api/ServiceTokenProvider.java \
     services/strategies/src/main/java/com/example/strategies/integration/internal/api/ServiceTokenProvider.java
```

Исход — **клетка неверна на двух формах из трёх**, и это `N-1`.
## Находки и их классы

Гейтящих (`невалидность`) — **три класса на трёх кейсах**; ни один
**не правлен** этим кругом: правит автор (`.claude/skills/test-review.md`
§«Петля с tester»). `пробел дока` — **три новые находки владельцам**
(`F8`-`F10`), каждая с заведённой секцией бэклога; семь находок автора
(`F1`-`F7`) проверены по носителям и подтверждены, пять припаркованных
долгов подтверждены тоже. `пробел покрытия` — раздел у документа есть
(`G1`-`G5`), новых не добавлено: `G1` проверен прогоном и стои́т верно.
`форма` — **семь**.

### `N-1` (`невалидность`, U12.6) — тождество текстов копий объявлено фактом, а `diff` его опровергает

Клетка ждёт, что тексты копий одной формы «за вычетом объявления пакета и
импортов **совпадают дословно** у `PeerCall` и у `AsyncActorContextConfigurer`;
у `ServiceTokenProvider` двух деревьев — кроме одной строки константы».
Прогнан `diff` всех трёх пар (ось 5 выше).

| Пара | Что сверх пакета и импортов |
|---|---|
| `ServiceTokenProvider` `trading-core` ↔ `strategies` | **одна строка** — `PRINCIPAL_NAME`. Клетка верна |
| `PeerCall` `trading-core` ↔ `strategies` | **два абзаца javadoc целиком**: у ядра — перечень своих вызовов и раздел «Коннектор сюда не входит», у `strategies` — свой перечень и раздел «Недоступность соседа отвергает создание». Клетка неверна |
| `AsyncActorContextConfigurer` `trading-core` ↔ `strategies` | **два абзаца переписаны и один добавлен** (у `strategies` — «Записи с актором на асинхронной тропе сегодня нет»). Клетка неверна |

Дефект не в формулировке, а в **диспозиции**: клетка предъявлена как
утверждение о сегодняшнем состоянии — пометки «**ожидание из дома**»,
которой документ последовательно метит свои семь расхождений
(§«Ожидание берётся из дома, даже когда сегодня оно не исполнено»), у неё
нет, и в таблице самопроверки автора она стои́т классом «носителя охраны
нет → пробел `G4`», а не классом расхождения. Отсюда два следствия:

- пробел `G4` описан как «охраны у текстового расхождения нет», тогда как
  **расхождение уже есть** — и охрана, которой нет, обнаружила бы его
  немедленно; кандидат охраны в бэклоге читается как профилактика, а не как
  предъявление;
- **клетка снимает половину предмета группы.** Группа `U12` заведена ради
  того, что «расходятся копии правкой одного дерева, и ни один прогон
  корпуса этого не мерит». Копии **уже разошлись** — на том самом носителе
  (javadoc), который у форм периметра и несёт дом поведения: `U8.1` и `U8.2`
  берут ожидание из javadoc `PeerCall` **у `trading-core`**, а прогоняются в
  обоих деревьях, и у второй копии такого пассажа нет вовсе.

Что нужно автору: перевести `U12.6` в форму, которую диктует сам документ —
либо «ожидание из дома» с расхождением, названным поимённо по двум парам,
либо честный факт (совпадает одна пара из трёх) плюс исход расхождения; и
согласовать с ней `G4` и дом ожидания групп `U8`, `U12`.

### `N-2` (`невалидность`, U3.2) — носитель ожидания назван цитатой, которой в нём нет

Клетка ждёт, что на анонимной аутентификации резолвер отдаёт класс контура,
и называет носителем `docs/models/domain/other/Auditable.md` §«Область
значений актора» с цитатой «неудостоверённое присутствие актором не
становится». **Пассаж такой цитаты не несёт, и слова «аноним» нет ни в одном
доке корпуса:**

```bash
export LC_ALL=C.UTF-8
grep -c 'неудостоверённое присутствие' docs/models/domain/other/Auditable.md
grep -rn 'анонимн\|Аноним\|AnonymousAuth' docs/models/domain/other/Auditable.md \
  docs/models/domain/other/AccessDenial.md docs/rules/api-access-policy.md
```

Обе команды печатают ноль попаданий. Область значений актора называет два
класса значений и признак «внешнего инициатора нет»; третьей формы
неудостоверённого — предъявленного, но не удостоверённого присутствия — она
не разбирает.

**Ожидание при этом верное** — его несёт javadoc `ActorProvider`
(«Анонимная аутентификация значением не является… субъекта, которого контур
не удостоверил») и код ветви. Неверен **носитель**: клетка утверждает, что
дом это говорит, а дом молчит. Признак 2 критерия читается буквально —
«у каждого ожидания назван носитель, **и ожидание ему соответствует**;
при молчании дока — с кодом как источником факта (файл и звено названы)»;
здесь не сделано ни первое, ни второе.

Сверх того клетка **скрывает пробел дома**, который иначе был бы виден:
клауза о неудостоверённом присутствии числится принятой (`Д539`,
`.claude/work/history/2026-09-04-phase-1-step-9-security/phase-1-step-9-sync-docs.md`
строка `A2` — «`Auditable.md` §«Область значений актора» — клауза о
неудостоверённом присутствии»), в доме её нет, а опираются на неё ветвь
общего артефакта и две живые пробы (`AccessDenialActorTest` у `audit` и у
`statistics`). Находка владельцу заведена — `F8`.

Что нужно автору: либо назвать носителем javadoc `ActorProvider` как звено
(тогда клетка валидна как помеченная, «ожидание не выведено из дока»), либо
дождаться клаузы по `F8`. Соседние клетки `U3.1`, `U3.3` цитируют носители
верно и правки не требуют.

### `N-3` (`невалидность`, U11.5) — наблюдение требует переиспользуемого треда, а базовая сборка группы его не даёт

Клетка ждёт: «задача бросает исключение → контекст после неё **очищен**:
следующая задача **того же треда** актора не наследует». Базовая сборка
группы — `AsyncActorContextConfigurer.propagating(new SimpleAsyncTaskExecutor())`.
Этот исполнитель пулом не является: он поднимает **новый тред на каждую
задачу**, и «тот же тред» состоянием не строится ничем.

```text
N-3 SimpleAsyncTaskExecutor t1=SimpleAsyncTaskExecutor-1 t2=SimpleAsyncTaskExecutor-2 sameThread=false
```

Следствие — тавтология: вторая задача видит пустой контекст **независимо от
того, очищает ли обёртка**, потому что у свежего треда его и не было.
Проверено прогоном: и обёрнутый исполнитель, и голый дают второй задаче
пустоту (ось 4, звено Z22). Кейс, объявленный прогоняемым, зелен при любом
поведении предмета — тот же класс, что `N-2` круга `bff-perimeter-logic`
(«состояние не строится ничем, а кейс объявлен прогоняемым»).

Соседняя клетка `U11.3` то же состояние строит честно — она называет «пул в
один тред», то есть **другой** исполнитель; но называет его в колонке входа,
а базовая сборка группы о втором исполнителе молчит (`Ф-4`).

Что нужно автору: либо дать `U11.5` тот же пул в один тред, что и `U11.3`
(тогда наблюдаемо), либо снять у неё вторую половину ожидания и оставить
первую — «исключение задачи проходит наружу, обёртка его не подменяет», —
которая на базовой сборке наблюдаема.

### `Ф-1` (`форма`, U7.2-U7.5, U7.7, U7.10, U12.1, §«Поверхность предмета») — ярусный предикат процитирован не из того раздела дома

Шесть клеток группы `U7` называют носителем
`docs/rules/runtime-error-classification.md` §«Отказ соседа по ярусу — свой
класс, и сделку в ошибку он не уводит» с цитатами «недоступностью названы
таймаут, обрыв и `5xx`» и «осознанный отказ соседа (`4xx`) — наш дефект».
Раздел прочитан целиком: он говорит о **реакции** (пропуск прохода, а не
ребро в `ERROR`), о **доводе** и об **области** («сосед по ярусу домена, а
не всякий чужой процесс») — и состава недоступности не называет.

Состав живёт **этажом выше**, в §«Правило», строкой таблицы
`PEER_SERVICE_UNAVAILABLE`: «таймаут, обрыв, `5xx`; плюс молчащий
коннектор». Положительный класс для `4xx` **соседа** дом не называет вовсе:
`4xx` как «наш дефект» объявлен в §«Молчащий коннектор классифицируется этим
же классом» — про коннектор — и в javadoc обеих копий `PeerReadException`.

Существо ожиданий от этого не меняется: `5xx` — недоступность по таблице
дома, а `4xx` в перечень недоступности не входит. Меняется **адрес**: клетка
ведёт читателя в раздел, который её утверждения не несёт, — то самое
«указатель ведёт не к тому дому», которого не мерит ни один прогон
(`.claude/rules/edit-kind-obligations.md` §«Цена принята и названа»).
Отдельно — `F10`: положительный класс `4xx` соседа дома в `docs/**` не имеет.

### `Ф-2` (`форма`, U6.1) — названная точка наблюдения не исполнима: тело в свой DTO не разбирается

Клетка говорит: «тело **разбирается** как error-DTO и несёт ровно четыре
имени поля». Разбор — не наблюдение, а утверждение о форме, и оно неверно.
`ErrorApiResponse` собран `@Getter @Builder` на `private final`-полях, без
`record`, без бина и без `@Jacksonized`; у класса **ноль публичных
конструкторов** и ни одного creator'а. Прогон (ось 4, та же проба):

```text
F9 serialized={"code":"ACCESS_UNAUTHENTICATED","reason":null,"message":"Unauthorized","occurredAt":null} fieldNames=4 publicCtors=0
THROWS   F9 readValue(json, Err.class) -> IllegalStateException: InvalidDefinitionException (no Creators)
```

**Обе половины клетки при этом верны по существу:** имён поля ровно четыре,
и `reason` едет `null`, а не опускается — у каркасного сборщика умолчание
включения не тронуто, `NON_NULL` в дереве ставят только конвертеры навеса
своим `copy()`. Наблюдается это **деревом JSON**, а не разбором в класс.

Тот же прогон дал находку владельцу — `F9`: единый error-DTO внешней
поверхности собран формой, которую читатель не восстанавливает.

### `Ф-3` (`форма`, U2.2, U2.3) — ненаблюдаемое клеймо стои́т в колонке выхода

Обе клетки ждут состояния **карты замков** («в карте замков `N` записей», «замок
в карте **один**»). Карта — `private final Map<String, ReentrantLock> locks`
без аксессора; поверхностью класса она не наблюдается, и проба читала бы её
рефлексией, то есть внутренностью, а не выходом. Наблюдаемая половина у обеих
клеток есть («все `N` тел исполнены»), и предмет — рост карты и
неповторное заведение замка — выражается через неё: разные имена не держат
друг друга, одно имя держит. Класс тот же, что `Ф-6` круга
`bff-perimeter-logic`.

### `Ф-4` (`форма`, U11.3, базовая сборка группы `U11`) — сборка группы молчит о втором исполнителе

Базовая сборка называет один исполнитель (`SimpleAsyncTaskExecutor`) и его же
без обёртки как контроль. Клетка `U11.3` вводит третий —
«обёрнутый исполнитель с пулом в один тред», — и он не деталь входа, а
**другая сборка**: у `SimpleAsyncTaskExecutor` пула нет по построению.
Преамбула группы обязана назвать оба, иначе следующий читатель соберёт `U11.3`
на объявленной сборке и получит зелёный кейс ни о чём (`N-3`).

### `Ф-5` (`форма`, U3.3) — цитата взята из соседнего раздела того же дока

Клетка называет `docs/models/domain/other/AccessDenial.md` §Инварианты с
цитатой «непринятые креды — тот же класс, что непредъявленные». §Инварианты
её не несёт; пассаж живёт в §«Енум `Outcome`» («**Непринятые креды — тот же
класс, что и непредъявленные**»). Ожидание верно, адрес — нет.

### `Ф-6` (`форма`, U9.8) — звено не несёт того, что клетка из него выводит

Клетка ждёт, что два вызова подряд спрашивают менеджер дважды («своего кэша
у провайдера нет»), и называет носителем звено Z17. Z17 описывает **охрану**
(`isNull(client) || isNull(client.getAccessToken())`) и о кэше не
высказывается. Носитель ожидания — тело метода целиком: у класса одно поле
(менеджер), и запоминания значения в нём нет ни в каком виде.

### `Ф-7` (`форма`, U1.1, §«Поверхность предмета») — эллипсис в цитате не отмечен

Цитата «пока один запуск джобы активен, перекрывающий пропускается»
приведена как дословная, а в `.claude/rules/codestyle.md` §Джобы внутри
неё стои́т вставка: «перекрывающий **(затянувшийся предыдущий тик или ручной
триггер параллельно расписанию)** пропускается». Пропуск законен, но
отмечается многоточием — иначе механическая сверка цитат печатает ложное
попадание, а следующий читатель ищет в доме строку, которой там нет в таком
виде.
## Находки автора: проверены, не приняты на слово

| Находка | Что проверено | Исход |
|---|---|---|
| `F1` — актором записей `market-data` пишется имя сервиса | `JpaAuditConfig#auditorAware` у `market-data` отдаёт константу `WRITER = "market-data"` без обращения к поставщику; у пяти остальных сервисов с аудитом тот же бин делегирует в `ActorProvider`. Ручной триггер достижим: пять фасадов в `domain/jobs/facade`. Переносчика контекста в дереве нет — `find` печатает его только у `trading-core` и `strategies`. Дом запрещает прямо: «значение отвечает на „который из двух КЛАССОВ“, а не „какой сервис“» (javadoc `Constants.Audit.SYSTEM_PRINCIPAL`) | **подтверждена целиком**, включая вторую половину о переносчике |
| `F2` — неразбираемое тело читается как недоступность соседа | `UnknownContentTypeException` — прямой наследник `RestClientException`, к `RestClientResponseException` **не** приводится (прогон, ось 4): оба класса отказа разбора попадают во второй `catch` и уезжают `PeerServiceUnavailableException`. Состав недоступности в доме — «таймаут, обрыв, `5xx`» (§«Правило», строка `PEER_SERVICE_UNAVAILABLE`), и неразбираемого тела там нет | **подтверждена**; адрес цитаты правится по `Ф-1` |
| `F3` — клейм «пустой токен — отказ» держится одной ветвью из четырёх | прогоном подтверждены обе библиотечные опоры: конструктор `OAuth2AuthorizedClient` пустого токена не принимает (`accessToken cannot be null`), `withClientRegistrationId` на пустом и отсутствующем идентификаторе отказывает `IllegalArgumentException` **до** менеджера. Третья ветвь (`ClientAuthorizationException` от точки токенов) прочитана в исходниках, прогоном не бралась — пометка автора честна | **подтверждена** |
| `F4` — аноним на тропе отказа авторизации уходит в строку именем | `AccessDenialHandler#acceptedPrincipal` — `isNull(authentication) ? null : getName()`; анонима не отсекает. `ActorProvider#currentActor` отсекает три формы. Дом строки: `principal` — «**Принятый** принципал… Заявленное, но не удостоверенное имя сюда не пишется» (§Структура) | **подтверждена** |
| `F5` — javadoc порта называет `ObjectProvider`, поле объявлено `Optional` | javadoc `AccessDenialRecorder`: «точки входа отказа спрашивают его через `ObjectProvider`»; поле `AccessDenialHandler` — `private final Optional<AccessDenialRecorder> recorder` | **подтверждена** |
| `F6` — третья копия провайдера отказывает классом чужого дома | `ServiceTokenProvider` у `market-data` бросает `ExchangeReadException`, чей javadoc описывает отказ **чтения площадки через коннектор**; недобытый служебный токен под его ветви не подходит | **подтверждена** |
| `F7` — у `bff` обработчик отказа авторизации не проведён в ресурс-сервер | `SecurityConfig` периметра: `oauth2ResourceServer(...).authenticationEntryPoint(denialHandler)` — и **без** `accessDeniedHandler`; у `audit`, `statistics`, `strategies` обе точки проведены в обе стороны | **подтверждена** |
| пять припаркованных долгов | `U13.2` — точки входа проведены ровно у четырёх сервисов из восьми (команда оси 3); `U1.6` — `runExclusively` объявлен `void`, исхода вызывающему не отдаёт; `U10.3` — `ConnectorProperties` умолчаний не несёт, а незаданная регистрация роняет `IllegalArgumentException` библиотеки; `U13.9` — javadoc `JobController` у `market-data` адресует `com.example.marketdata.domain.jobs.JobExecutionGuard`, класс живёт в `com.example.platform.jobs`; парковка о тестовых деревьях — `services/common/platform/src` печатает ровно `main` | **подтверждены все пять** |

## Новые находки владельцам

| Метка | Находка | Владелец | Секция бэклога |
|---|---|---|---|
| `F8` | клауза о неудостоверённом присутствии в доме области значений актора отсутствует: `Auditable.md` §«Область значений актора» об анонимной аутентификации не говорит ничего, слова «аноним» нет ни в одном доке корпуса, — при том что решение `Д539` числит эту клаузу принятой, а опираются на неё ветвь `ActorProvider`, обе точки входа отказа и две живые пробы | `solution-designer` | §«Клауза о неудостоверённом присутствии в доме области значений актора отсутствует» |
| `F9` | единый error-DTO внешней поверхности собран формой, которую читатель не восстанавливает: `ErrorApiResponse` — `@Getter @Builder` на `private final` без `record`, бина и `@Jacksonized`; Jackson отказывает `InvalidDefinitionException (no Creators)`. Читатель у формы есть по построению — кейсы ящиков четырёх сервисов разбирают тело отказа | `code-writer` | §«Единый error-DTO поверхности читателем не восстанавливается» |
| `F10` | положительный класс отказа соседа по ярусу на `4xx` дома в `docs/**` не имеет: дом называет состав **недоступности** (таймаут, обрыв, `5xx`) и трактует `4xx` только у коннектора; «осознанный отказ соседа — наш дефект» живёт javadoc'ом двух копий `PeerReadException` и `PeerCall` | `solution-designer` | §«Класс отказа соседа по ярусу на `4xx` дома в `docs/**` не имеет» |

`F10` — того же рода, что `F5` автора и `U13.9`: ограничение, живущее только
в коде, домом не является (`.claude/rules/parking-address.md`), и правило,
которое система исполняет, обязано иметь дом в `docs/**`
(`.claude/rules/measurement-commands.md` §«Команда засчитывается проверкой
после прогона на двух состояниях»).

## Вердикт

**Апрува нет. Круг первый из трёх.**

Гейтит **невалидность трёх кейсов** — `U12.6`, `U3.2`, `U11.5`, — и все три
одного корня: **утверждение о предмете не сверено с тем деревом, которое
документ ему назначил.** `U12.6` утверждает тождество текстов, не прогнав
`diff`; `U3.2` называет доковый пассаж, не открыв его; `U11.5` наблюдает
переиспользование треда на исполнителе, который тредов не переиспользует.
Ось, введённая самим документом («у предмета нет дерева прогона, и его выбор
— часть кейса»), к собственным клеткам применена не везде — тот же класс, что
круг `bff-perimeter-logic` записал формой захода.

Остальные **93 кейса валидны** по четырём признакам: пять полей на месте и
колонка «Факт» пуста; ожидания сверены с носителями — восемь звеньев
библиотек подтверждены прогоном, а не чтением; отсутствующие выходы названы
(вся группа `U14` плюс клетки «не вызван», «не логируется», «не изменён»);
достижимость помечена честно — девять недостижимых состояний несут механизм
недостижимости, и все девять проверены.

**Документ возвращается автору** (`tester`): три клетки правит он,
семь находок класса `форма` исполняет тем же заходом без нового круга
(`.claude/skills/test-review.md` §«Что апрув не блокирует»). Новые находки
владельцам (`F8`-`F10`) припаркованы этим кругом и работы автора не ждут.

## Что этот круг добавляет к форме захода

Две оси сверх тех, что перечисляли заходы 37-59:

- **утверждение документа о СОСТОЯНИИ дерева ревью прогоняет командой, а не
  читает.** У соседей по уровню предмет сверялся с домом и с кодом класса;
  здесь документ впервые делает предметом кейса **состав деревьев** —
  тождество копий, взятие формы потребителем, место дерева прогона. Такое
  утверждение выглядит как ожидание, а является **замером**, и замер,
  написанный по памяти, неотличим от замера, снятого прогоном: `U13` из
  девяти клеток сошёлся с командами целиком, а `U12.6` — единственная
  клетка, которой автор команды не дал, — разошёлся с `diff` на двух парах
  из трёх. Проверяемая форма: у каждой клетки о составе дерева спросить,
  какой командой она снимается, и прогнать её;
- **звено библиотеки с пометкой «прогоном не проверено» ревью ПРОГОНЯЕТ, и
  стои́т это дёшево.** Восемь звеньев трёх библиотек подняты одним
  classpath'ом из локального репозитория и проверены четырьмя пробами за
  один заход; все восемь подтвердились, но подтвердились **измерением**, а
  не согласием читателя с читателем. Тот же прогон, стоивший десяти строк,
  дал `F9` — дефект формы единого error-DTO, которого не видит ни чтение
  класса, ни чтение дока: он предъявляется только попыткой прочитать то, что
  форма пишет.

## Связи

- Документ кейсов — `.claude/tests/cases/platform-shared-logic.md`.
- Передача автора и его самопроверка —
  `.claude/work/progress/phase-2-step-12-hand-test-platform-shared-logic.md`.
- Критерий и петля — `.claude/skills/test-review.md`.
- Перечень предметов — `.claude/decisions/test-contour-design-pass.md`
  §«Перечень предметов и порядок работы».
- Предыдущий круг уровня 2 —
  `.claude/work/progress/phase-2-step-12-review-test-trading-core-calc.md`.
- Находки круга — `.claude/work/backlog.md` §«Клауза о неудостоверённом
  присутствии в доме области значений актора отсутствует», §«Единый error-DTO
  поверхности читателем не восстанавливается», §«Класс отказа соседа по ярусу
  на `4xx` дома в `docs/**` не имеет».
