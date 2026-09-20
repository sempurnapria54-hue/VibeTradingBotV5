# Команды и факты среды

## На какой вопрос отвечает этот файл

Как исполнять команды сборки, прогона и доступа к секретам в этой среде.

## Когда применять

Всякий раз, когда заходу нужна сборка, прогон тестов, подъём локального
контура данных или токен Vault. **Это дом фактов среды:** команда сборки,
лаунчер Python и пути JDK/Maven живут здесь одной редакцией; прочие
носители ссылаются, а не пересказывают. Правило заведено потому, что
расходящихся редакций было три на четыре носителя, и две из них
предписывали команды, которых в среде того момента не существовало, —
падение громкое, но дефект предписания остаётся дефектом.

**Действующие имена команд — только в таблице и в §«Воспроизводимые
команды»; проза о них не высказывается.** Всякое имя, приведённое прозой
как «несуществующее», живёт ровно до следующей смены среды и затем
противоречит таблице.

## Факты среды — проверено прогоном, не выведено

Редакция проверена прогоном из среды, установившейся 2026-08-31: хост —
Windows 11 (`MINGW64_NT-10.0-26200`; IDEA 2026.1, Git Bash / PowerShell),
Vault в не-dev режиме.

| Что нужно | Состояние | Чем проверено |
|---|---|---|
| JDK 25 | **есть** — `~/.jdks/corretto-25.0.3`, Corretto 25.0.3.9.1 | `~/.jdks/corretto-25.0.3/bin/java -version` |
| Maven | **есть** — встроенный в IDEA: `C:/Program Files/JetBrains/IntelliJ IDEA 2026.1/plugins/maven/lib/maven3`, 3.9.11; на `PATH` нет, `mvnw` в репозитории нет | `"$MAVEN_HOME/bin/mvn" -v` → 3.9.11 на JDK 25.0.3 |
| Docker + локальный контур данных | **есть** — контейнеры `vibetradingbotv5-{vault,postgres,postgres-test}` | `docker ps` |
| Vault `127.0.0.1:8200` | **не-dev режим**: file storage, shamir 1/1; listener в контейнере — `18200`, наружу проброшен `8200`; после каждого рестарта контейнера **запечатан** — нужен `vault operator unseal` (ключ у держателя) | `vault status` → `sealed:false`, `storage:file`; CLI внутри контейнера требует `VAULT_ADDR=http://127.0.0.1:18200` |
| Postgres-test `5441` | **слушает** (БД `tradingbot_test`) | запуск приложения профиля `test` из IDEA |
| Сеть до биржи | **есть** | `GET /api/v5/public/time` → `HTTP 200`, `code=0` |
| Лаунчер Python | **`py -3`** → Python 3.11.9; `python3` и `python` в Git Bash — заглушки WindowsApps: печатают `Python` без версии, код не исполняют | `py -3 -c "print(1)"` → `1`; `python3 -c "print(1)"` → исполнения нет |
| Сборка дерева | реактор из корня, обёрткой `bash tools/reactor-test.sh`; точечно — `mvn -o -am -pl <модуль> test` | прогон обёртки |
| Артефакты тестового контура | **есть с 2026-09-19** — Testcontainers `2.0.2` (`testcontainers-postgresql`, `-kafka`, `-vault`, `-junit-jupiter`), `spring-boot-testcontainers` `4.0.0`, WireMock `wiremock-standalone:3.13.1`; Awaitility приезжает со `spring-boot-starter-test`. Версию Testcontainers держит Boot: своя разошлась бы с `testcontainers-bom` каркаса | прайминг §«Воспроизводимые команды»; `ls ~/.m2/repository/org/testcontainers/` |
| Образы субстрата ящика | **есть** — `timescale/timescaledb-ha:pg17.10-ts2.29.2` (тот же тег, что в `deploy/base`) и `hashicorp/vault:1.15` с 2026-09-19; `apache/kafka:4.1.1` с 2026-09-20 (версия клиента `kafka-clients` дерева — манифест стенда версии брокера не называет вовсе, её выбирает оператор). Образ базы весит около 4 GB на диске и приезжает **разово**, как и jar'ы: прогон ящика его не тянет | `docker images`; пробы совпадения тегов — `SubstrateImagePinTest` деревьев `auth` и `trading-core` |
| Демо-ключ OKX | **доказан отрицанием**: тот же ключ **без** заголовка `x-simulated-trading` → `HTTP 401`, `50101 APIKey does not match current environment`; с заголовком → `code=0` | прогон 2026-08-31 |

**Проверка демо-контура — падающая проба, а не флаг конфигурации.** Клейм
«ключ демо-контура» доказывается тем, что боевой контур этот ключ
**отвергает**, а не тем, что профиль выставляет `okx.simulated=1`. Второе —
заявление о нашей настройке, первое — свойство самого ключа.

## Воспроизводимые команды

```bash
export JAVA_HOME="$HOME/.jdks/corretto-25.0.3"
export MAVEN_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2026.1/plugins/maven/lib/maven3"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
# ВНИМАНИЕ: MAVEN_HOME здесь — в POSIX-форме (/c/...), и это обязательно.
# Драйв-буквенная форма («C:/Program Files/…») в PATH не работает: разделитель
# PATH в Git Bash — двоеточие, поэтому «C:» отрезается как отдельный элемент и
# `mvn` не находится («mvn: command not found»).

source .env.vault.test.local                 # VAULT_TOKEN профиля test
docker compose up -d vault postgres-test     # если контур опущен
docker exec -e VAULT_ADDR=http://127.0.0.1:18200 vibetradingbotv5-vault \
  vault operator unseal                      # после каждого рестарта контейнера Vault

# ПРАЙМИНГ тестовых артефактов: реактор ходит -o, поэтому новая зависимость
# кладётся в локальный репозиторий ОНЛАЙНОВЫМ прогоном ДО него
# (.claude/decisions/test-contour-design-pass.md, решение 10). Версии здесь
# не выдумываются: их закрепляет spring-boot-dependencies того же мажора.
mvn -q -B dependency:get -Dartifact=<группа>:<артефакт>:<версия>

# ПРАЙМИНГ образов субстрата ящика: тот же довод, что у jar'ов, — прогон
# ящика образа не тянет, и первый его подъём иначе платил бы минутами
# скачивания внутри теста.
docker pull timescale/timescaledb-ha:pg17.10-ts2.29.2   # тег — как в deploy/base
docker pull hashicorp/vault:1.15
docker pull apache/kafka:4.1.1                          # версия клиента kafka-clients дерева

bash tools/reactor-test.sh                   # компиляция всех деревьев + весь unit-набор
mvn -o -am -pl services/trading-core test -Dtest='<Класс>[,<Класс>]' \
  -Dsurefire.failIfNoSpecifiedTests=false    # точечно; разделитель классов — запятая
```

**Инструменты корпуса зовутся через `py`**, не через `python3`
(`.claude/rules/measurement-commands.md` §«Ловушки среды»).

## Чего среда не даёт

- **`mvnw` в репозитории нет.** Прогон опирается на maven3, встроенный в
  IDEA, и на ручной `PATH`; автономного лаунчера сборки нет. Состав работ —
  `.claude/work/backlog.md` §«Средовой дефицит автономного прогона тестов».
- **Токен `.env.vault.test.local` тестовыми путями не ограничен** —
  `policies=["root"]`, `ttl=0`. Ограничение области — задача держателя
  (`.claude/work/backlog.md`, секция области токена).
- **База в автономном прогоне тестов есть с 2026-09-19** — её поднимает
  Testcontainers у ящика сервиса (`auth` — первый), вместе с контейнером
  хранилища и стабом провайдера идентичности; своего `docker-compose` прогон
  при этом не трогает. Прежняя запись «базы нет» снята: она описывала среду до
  ящика. Что осталось незакрытым — контекст поднимается **не у каждого**
  сервиса, а у тех, чей ящик написан; дом задачи —
  `.claude/work/backlog.md` §«Контекстный тест сервиса — по появлению базы
  в прогоне».

## Связи

- Гейт статуса и реактор — `.claude/skills/update-roadmap-progress.md`.
- Локальный стенд кластера — `.claude/skills/local-stand.md`.
- Нормы проверочных команд и ловушки среды —
  `.claude/rules/measurement-commands.md`.
