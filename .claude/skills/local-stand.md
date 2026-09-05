# Локальный стенд

## На какой вопрос отвечает этот файл

Как поднять, вести и снять локальный стенд платформы.

## Что такое стенд

Полная платформа и все построенные сервисы, поднятые на машине держателя
как **тестовое окружение** (решение держателя 2026-09-05; дом —
`docs/architecture/platform.md` §Развёртывание). Манифесты те же, что у
любого окружения, — оверлей `dev`; стенд отличается только тем, ЧЕМ поднят
сам кластер: kind (Kubernetes в контейнере поверх Docker).

**Зачем он нужен, кроме тренировки:** на нём впервые наблюдается
потребление ресурсов, а по нему решается размещение `prod`
(`.claude/work/roadmap/prod-gate.md`, условие 2).

## Что должно быть в среде

| Что | Как проверить | Если нет |
|---|---|---|
| Docker | `docker info` | поставить Docker Desktop |
| kubectl | `kubectl version --client` | поставить kubectl |
| kind | `"$LOCALAPPDATA/kind/kind.exe" version` | скачать бинарь релиза kubernetes-sigs/kind в `%LOCALAPPDATA%\kind\kind.exe` и сверить sha256 |
| Python | `py -3 -c "print(1)"` | дом фактов среды — `.claude/tests/source-api/okx/code-preconditions.md` |
| JDK 25 и Maven | там же | там же |

Пути JDK и Maven команды стенда **назначают сами** (`STAND_JDK`,
`STAND_MAVEN`): в среде уже стои́т `JAVA_HOME` на JDK 11, и наследование
собирало бы проект не тем компилятором.

## Постановка

```bash
bash tools/stand/up.sh                 # кластер, Argo CD, операторы, окружение dev, Vault, реалм
bash tools/stand/deploy-services.sh    # сборка сервисов, образы, теги, применение
```

Обе команды идемпотентны: повторный прогон доводит до того же состояния.
Первая постановка занимает время выкачки образов (десятки минут на
холодном Docker), повторная — минуты.

**Что делает `up.sh` по шагам:** кластер kind → Argo CD (единственный
ручной ход GitOps: пока его нет, приводить кластер к манифестам некому) →
кластерный слой `deploy/base/platform` (операторы, cert-manager, права
сборщика логов) → секреты ролей базы (генерируются, в git не попадают) →
оверлей `deploy/dev` → распечатывание и настройка Vault → перенос секрета
клиента из провайдера идентичности в секрет кластера.

## Что поднимается

| Пространство имён | Что |
|---|---|
| `argocd` | Argo CD: он держит кластерный слой |
| `cert-manager`, `cnpg-system`, `kafka-system`, `elastic-system`, `keycloak-operator`, `vault-system`, `monitoring` | операторы и их инстансы |
| `dev` | окружение: Postgres+TimescaleDB, Kafka, Elasticsearch с Kibana и сборщиком логов, Jaeger, провайдер идентичности, `auth`, `connector-okx`, `market-data` |

## Доступ к интерфейсам

Ингресса на стенде нет (он приезжает вместе с `bff` и `web`), поэтому
доступ — прокидыванием порта. Каждая команда занимает терминал; закрывать
по Ctrl+C.

| Что | Команда | Учётные данные |
|---|---|---|
| Argo CD | `kubectl -n argocd port-forward svc/argocd-server 8082:80` → http://localhost:8082 | `admin`; пароль — `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' \| base64 -d` |
| Grafana | `kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80` → http://localhost:3000 | `admin`; пароль — `kubectl -n monitoring get secret kube-prometheus-stack-grafana -o jsonpath='{.data.admin-password}' \| base64 -d` |
| Prometheus | `kubectl -n monitoring port-forward svc/kube-prometheus-stack-prometheus 9090:9090` | — |
| Alertmanager | `kubectl -n monitoring port-forward svc/kube-prometheus-stack-alertmanager 9093:9093` | — |
| Kibana | `kubectl -n dev port-forward svc/platform-logs-ui-kb-http 5601:5601` | `elastic`; пароль — `kubectl -n dev get secret platform-logs-es-elastic-user -o jsonpath='{.data.elastic}' \| base64 -d` |
| Jaeger | `kubectl -n dev port-forward svc/platform-tracing 16686:16686` | — |
| Провайдер идентичности | `kubectl -n dev port-forward svc/platform-identity-service 8080:8080` | пользователь и пароль — `kubectl -n dev get secret platform-identity-initial-admin -o jsonpath='{.data.username}' \| base64 -d` и `…{.data.password}…` |
| Vault | `kubectl -n vault-system port-forward svc/vault 8200:8200` | корневой токен — в `%LOCALAPPDATA%\vibetrading-stand\vault-init.json` |
| Postgres | `kubectl -n dev port-forward svc/platform-postgres-rw 5432:5432` | по роли: `kubectl -n dev get secret postgres-role-market-data -o jsonpath='{.data.password}' \| base64 -d` |
| Поверхность сервиса | `kubectl -n dev port-forward svc/market-data 8080:8080` | открыта только проба живости `/actuator/health`; остальное требует токена провайдера |

**Ключ распечатывания Vault и корневой токен лежат вне репозитория** — в
`%LOCALAPPDATA%\vibetrading-stand\vault-init.json`. Это единственный
файл стенда, потеря которого невосстановима: без ключа распечатать
хранилище нечем.

## Выкладка новой сборки сервиса

```bash
bash tools/stand/deploy-services.sh market-data      # один сервис
bash tools/stand/deploy-services.sh                  # все три
```

Команда исполняет ровно то, что в целевой конструкции делает CI: собирает
образ **один раз**, кладёт его в узел кластера и переставляет тег в
манифесте окружения (`tools/deploy-set-image-tag.sh`). Тег неизменяем и
выводится из коммита; пока дерево грязное, к тегу добавляется метка
времени — иначе один тег означал бы разное содержимое.

## Остановка без потери данных

Стенд живёт в контейнере узла. Остановить и вернуть его, не теряя базу,
ряды и хранилище:

```bash
docker stop vibetrading-control-plane      # остановить
docker start vibetrading-control-plane     # вернуть
bash tools/stand/vault-setup.sh            # Vault после каждого подъёма запечатан — распечатать
```

**Vault после рестарта запечатан всегда** — это свойство не-dev режима, а
не поломка.

## Снятие

```bash
bash tools/stand/down.sh
```

Удаляет кластер целиком вместе со всеми данными. Файл распечатывания
Vault остаётся: удаляет его держатель, потому что удалять ключевой
материал молча — не дело команды.

## Что стенд НЕ даёт

Названо, а не умолчано:

- **ингресса нет** — маршрут и сертификат приезжают вместе с `bff` и `web`;
  наружу стенд не опубликован вовсе;
- **вторая площадка и боевой контур не проверены**: окружение допускает
  только `DEMO` (`deploy/dev/env.yaml`);
- **`stage` и `prod` на стенде не разворачивались**: они — те же
  манифесты, но их прогон отдельным ходом;
- **потребления под нагрузкой нет**: числа снимаются на холостом ходу и на
  синке листинга, а не на торговле.

## Связи

- Цепочка сессий, которая выкладывает на стенд без держателя —
  `.claude/skills/session-chain.md`.
- Где система развёрнута и как наблюдается — `docs/architecture/platform.md`.
- Раскладка манифестов — `deploy/README.md`.
- Команды и факты среды — `.claude/tests/source-api/okx/code-preconditions.md`.
- Хроника первой постановки и её находки —
  `.claude/work/history/2026-09-05-local-stand.md`.
