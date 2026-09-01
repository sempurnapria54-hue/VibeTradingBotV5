# -*- coding: utf-8 -*-
"""Предмет проверки: снятая редакция не живёт в носителях, а пришедшая — дошла.

ЗАЧЕМ. Правило `.claude/rules/carrier-levels.md` («стыковая истина живёт один
раз; копия в доке деталей — дефект носителя») и `.claude/rules/policy-home.md`
(«один дом на политику») энфорсера не имели ни одного: механической проверки,
что правка политики ДОШЛА ДО ПОТРЕБИТЕЛЕЙ, не существовало. Измерено: 27 из
39 гейтящих находок одного прогона — непройденный обход потребителей, причём
дом правила противоречил сам себе через тридцать строк. Правка верна
поодиночке и не сходится вместе.

ЗАЧЕМ ВТОРАЯ ОСЬ (2026-08-31). Свип по ФОРМЕ ТЕРМИНА не видит носителя,
выражающего снятую редакцию ДРУГИМИ СЛОВАМИ, — и это не редкий случай: снятая
редакция дедупа отчёта жила в шести носителях при зелёном прогоне этой
команды, а канон гейта держал снятый исход глаголом закрытия ПЕРЕД термином.
Поэтому запись реестра называет ещё и ПОПУЛЯЦИЮ НОСИТЕЛЕЙ — места, где снятая
редакция стоя́ла и где ПРИШЕДШАЯ обязана появиться. Свип идёт по популяции, а
не только по форме: носитель, названный в популяции и не предъявивший
пришедшей редакции, — дефект, как бы он ни был написан.

ФОРМЫ ПРЕДМЕТА, КОТОРЫЕ ДЕТЕКТОР ВИДИТ (объявлено, доказано осями батареи):
  1. дословный термин снятой редакции в живом носителе;
  2. тот же термин, разорванный переносом строки (свип идёт по плоскому
     тексту: построчный проход фразу через перенос не видит);
  3. вхождение в РАБОЧЕМ файле (`backlog.md`, `open-questions.md`, roadmap) —
     оно опаснее докового: рабочий файл адресован исполнителю следующего
     под-шага;
  4. носитель ПОПУЛЯЦИИ, не предъявивший пришедшей редакции, — включая
     носитель, выражающий снятое другими словами (встречная форма);
  5. строка популяции, указывающая на носитель, которого в корпусе нет;
  6. свой шаблон пришедшей редакции у отдельного носителя популяции — там, где
     на месте снятого встало разное (контрпример у одного, нота величины-дома
     с переехавшим клеймом у другого);
  7. ОТРИЦАНИЕ рядом с термином (ключ `unless`): «слот не закрывает»,
     «закрывающей силы у него нет» — это ДЕЙСТВУЮЩАЯ редакция, а не снятая.
     Без этой формы дом действующей редакции попадал бы в находки, и гасить
     его пришлось бы allow-листом — то есть allow-лист работал бы подавителем
     ложных срабатываний, а не разрешением называть снятое.
Чего детектор НЕ мерит: носитель, который выражает снятую редакцию другими
словами и в популяции НЕ НАЗВАН. Против этого класса стои́т предмет 2 свипа
закрытия («свип идёт по конструкции, а не только по снимаемым терминам») и
построение популяции до правки (`.claude/skills/closure-population.md`), а не
эта команда: перечислить то, о чём закрытие не знает, механически нельзя.

РЕЕСТР. Одна запись — одна снятая редакция: опознавательный шаблон снятой
редакции, шаблон ПРИШЕДШЕЙ, дата снятия, решение-источник, перечень мест, где
снятый термин стоя́ть ВПРАВЕ, и ПОПУЛЯЦИЯ носителей. Запись живёт, пока живо
знание о снятии; снимается вместе с закрытием шага. Запись без шаблона
пришедшей редакции или без популяции ОТКАЗЫВАЕТ проверку целиком (код 2):
неполная запись давала бы зелёный прогон на неизмеренной оси.

ЧТО МОЖЕТ ПОПАСТЬ В РАЗРЕШЁННЫЕ МЕСТА. Только носители пайплайна
(`.claude/decisions/`, дом процесса, исходник инструмента, этот файл): там
снятая редакция называется затем, чтобы сказать, чем она заменена. Файл
продуктового корпуса (`docs/**`) в разрешённые места НЕ попадает никогда —
`docs/concept.md` §«Опровержение правит на месте» запрещает слой опровержения
в корпусе: знание, оказавшееся неверным, переписывается, а не сопровождается
пометкой «прежняя редакция снята».

ЧЕГО ЭТА КОМАНДА НЕ ЛОВИТ В ДОМЕ. Внутри разрешённого места вхождения снятого
термина не проверяются вовсе, поэтому дом, который на одной строке фиксирует
снятие, а через тридцать строк утверждает снятое, оси 1-3 не поймают: их
предмет — ПОТРЕБИТЕЛИ. Но дом, названный в популяции, обязан предъявить
пришедшую редакцию, и это ось 4.

ОБЛАСТЬ СВИПА. Живые носители: `CLAUDE.md`, `README.md`, `docs/**` (md, json),
`src/**` (java, json, sql, yml, yaml, properties), `tools/**` (py, sh, txt) и
`.claude/**` (md) за вычетом архива, истории, библиотеки и отчётов
прогонов (`progress/` цитирует снятые редакции как находки — это их предмет,
а не рецидив). Число просмотренных носителей печатает сам прогон: область,
которая у́же, чем полагает автор, — тот же ложный зелёный.

Запуск (из корня репозитория):  python3 tools/retired-check.py
Код возврата: 0 — снятых редакций в живых носителях нет и популяции
предъявили пришедшую редакцию; 1 — есть дефекты; 2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ
(ось не доказана, реестр пуст либо неполон, корпус пуст).
"""
import glob
import io
import os
import re
import sys
import tempfile

# Печать не зависит от кодировки консоли вызывающего: на cp1251-консоли
# объявленной среды вывод падал UnicodeEncodeError (класс описан в backlog
# у anchor-check; тот же ремонт исполнимости, DOCS_CHECK_33 узел 9).
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SKIP = ('/.claude-archive/', '/.claude/work/history/', '/.claude/work/progress/',
        '/.claude/library/', '/target/', '/.git/')

# Область приведена к ОБЪЯВЛЕННОЙ: прежний перечень не брал миграции,
# конфиги (yaml/yml), реестры инструментов (txt) и сам план запросов
# (json коллекции) — то есть шапка обещала «src/**, tools/**», а свип шёл
# по трём расширениям из семи. Носитель, не попавший в область, зелёного
# прогона не портит и потому невидим.
ROOTS = ('CLAUDE.md', 'README.md',
         'docs/**/*.md', 'docs/**/*.json',
         'src/**/*.java', 'src/**/*.json', 'src/**/*.sql',
         'src/**/*.yml', 'src/**/*.yaml', 'src/**/*.properties',
         '.claude/**/*.md',
         'tools/*.py', 'tools/*.sh', 'tools/*.txt')

# --- РЕЕСТР СНЯТЫХ РЕДАКЦИЙ ---------------------------------------------------
# Ключи записи:
#   name       — как редакция называется в отчёте;
#   pattern    — опознавательный шаблон СНЯТОЙ редакции (плоский текст);
#   arrived    — опознавательный шаблон ПРИШЕДШЕЙ редакции;
#   date       — дата снятия;
#   source     — решение-источник;
#   allowed    — где снятый термин стоя́ть вправе (носители пайплайна);
#   population — носители, где снятая редакция стоя́ла и где пришедшая обязана
#                стоять. Пара «носитель, свой шаблон пришедшей»; None во втором
#                поле означает «шаблон записи». Свой шаблон обязателен там, где
#                на месте снятого встало РАЗНОЕ: у одного носителя — контрпример,
#                у другого — нота величины-дома, несущая переехавший клейм.
#                Пустой популяции не бывает: редакция, не стоявшая нигде, не
#                снималась.
RETIRED = [
    {
        'name': 'невозрастающая база риска',
        'pattern': r'невозраста\w+\s*(?:баз\w*)?|база\s+риска\s+не\s+(?:растёт|растет|возрастает)'
                   r'|риск-баз\w*\s+не\s+возрастает|база\s+расти\s+сама\s+не\s+должна'
                   r'|вверх\s+баз\w*\s+автоматически\s+не\s+ходит',
        'arrived': r'следует\s+за\s+(?:свободным\s+остатком|балансом)\s+в\s+обе\s+стороны'
                   r'|в\s+обе\s+стороны',
        'date': '2026-08-30',
        'source': 'решение держателя: база риска следует за балансом в обе стороны',
        'allowed': ('.claude/decisions/risk-base-follows-balance.md',
                    '.claude/work/decision-digest.md',
                    '.claude/knowledge-tree.md'),
        # Форма «невозраста-ЕМОСТЬ» шаблоном «невозраста-ЮЩ» не ловилась, и
        # носитель-исполнитель в популяции не стоя́л вовсе: зелёный прогон
        # означал «форм из перечня нет», а не «снятой редакции нет».
        'population': (('docs/rules/risk-policy.md', None),
                       ('docs/models/domain/core/Exchange.md', None),
                       ('docs/spec/risk-limits.json',
                        r'вверх\s+автоматически\s+не\s+ходит'),
                       ('docs/components/RefreshBalanceExecutor.md',
                        r'решает\s+её\s+дом|risk-base-follows-balance')),
    },
    {
        'name': 'закрывающая сила исхода OBSERVED_ABSENT',
        # Шаблон ПОРЯДКО-НЕЗАВИСИМ: снятая редакция встретилась формой
        # «глагол закрытия ПЕРЕД термином» («слоты закрыты … либо исходом
        # OBSERVED_ABSENT»), которой прежний порядок слов не видел.
        # Шаблон ПОРЯДКО-НЕЗАВИСИМ: снятая редакция встречается и формой
        # «глагол закрытия ПЕРЕД термином». Отрицание снимается ключом
        # `unless` — действующая редакция («слот не закрывает»,
        # «закрывающей силы у него нет») снятой не является, и гасить её
        # allow-листом значило бы использовать allow-лист подавителем.
        'pattern': r'закрыт\w*[^.\n]{0,90}OBSERVED_ABSENT'
                   r'|OBSERVED_ABSENT[^.\n]{0,90}закрыва\w+',
        'unless': r'не\s+закрыва|закрывающ\w+\s+сил\w+|не\s+наблюдение\s+факта'
                  r'|неприменим|гейт\s+не\s+закрыт',
        'arrived': r'OBSERVED_ABSENT[^.\n]{0,120}(?:не\s+закрыва|закрывающей\s+силы\s+не)'
                   r'|закрывающ\w+\s+сил\w+\s+(?:исхода\s+)?«?не\s+наступило»?\s+снят\w*'
                   r'|исходом\s+«не\s+наступило»\s+не\s+закрыва\w+',
        'date': '2026-08-30',
        'source': 'решение держателя: гейт исходом «не наступило» не закрывается',
        # Носители пайплайна, где снятая редакция названа затем, чтобы
        # сказать, что её больше нет: скилл-гейт (действующая редакция),
        # дайджест решений (запись самого снятия) и план контура (хроника
        # двух снятий подряд).
        # Разрешённые места — носители пайплайна, где снятая редакция названа
        # затем, чтобы сказать, чем заменена: дайджест фиксирует само снятие,
        # план контура ведёт хронику двух снятий подряд. Дом ДЕЙСТВУЮЩЕЙ
        # редакции (скилл-гейт) сюда не входит: его гасит `unless`, а не
        # allow-лист.
        'allowed': ('.claude/work/decision-digest.md',
                    '.claude/tests/source-api/okx/plan.md'),
        # На месте снятого встало РАЗНОЕ: скилл-гейт держит действующую
        # редакцию дословно; канон процесса перечень способов закрытия
        # СНЯЛ и делегировал реестру; дом-реестр формулирует своими
        # словами. Отсюда свой шаблон у двух носителей из трёх.
        'population': (('.claude/skills/update-roadmap-progress.md', None),
                       ('.claude/processes/roadmap-step-execution.md',
                        r'Чем\s+именно\s+слот\s+закрывается\s+—\s+предмет\s+реестра'),
                       ('.claude/tests/source-api/okx/code-preconditions.md',
                        r'закрывающей\s+силы\s+у\s+него\s+нет')),
    },
    {
        'name': 'величина-теорема с ключом provenBy',
        'pattern': r'provenBy|величин\w*-теорем\w*|косвенн\w+\s+проб\w+',
        'arrived': r'unreachable',
        'date': '2026-08-30',
        'source': 'дизайн-проход приёмки: класс упразднён, охранный инвариант '
                  'доказывается предъявленным контрпримером',
        'allowed': ('.claude/decisions/acceptance-by-measurement.md',
                    '.claude/work/decision-digest.md',
                    '.claude/processes/roadmap-step-execution.md',
                    'src/test/java/com/example/tradingbot/spec/Spec.java',
                    'src/test/java/com/example/tradingbot/spec/SpecMutation.java',
                    'tools/retired-check.py'),
        # Популяция добыта по состоянию корпуса ДО снятия (git show HEAD:<файл>
        # | grep -c provenBy): пять спек и одно правило. На месте снятого встало
        # РАЗНОЕ, поэтому у половины носителей свой шаблон: где инвариант ложен
        # на достижимом состоянии — контрпример `unreachable`; где он оказался
        # переобъявлением — нота величины-дома с носителями клейма.
        'population': (('.claude/rules/structure.md', None),
                       ('docs/spec/protection-coverage.json', None),
                       ('docs/spec/loss-streak-halt.json', r'носител\w*\s+клейма'),
                       ('docs/spec/manual-halt.json', r'носител\w*\s+клейма'),
                       ('docs/spec/order-sizing.json', r'носител\w*\s+клейма'),
                       ('docs/spec/strategy-walkthrough.json', r'носител\w*\s+клейма')),
    },
    {
        'name': 'дедуп STATE-отчёта поиском незавершённого',
        # Шаблон берёт УТВЕРДИТЕЛЬНУЮ форму: «дедуп/идемпотентность держится
        # незавершённым», «ключ/индекс поиска незавершённого». Отрицание
        # («индекс незавершённых строк ей не нужен») снятой редакцией не
        # является и в шаблон не попадает.
        'pattern': r'поиска?\s+незавершённого'
                   r'|держится\s+незавершённым\s+статусом'
                   r'|индекс\s+незавершённых\s+`?STATE`?',
        'arrived': r'стоящ\w+\s+состояни\w+\s+объекта|состояни\w+\s+ОБЪЕКТА|ключу\s+состояния',
        'date': '2026-08-31',
        'source': 'закрытие GAPS_CLOSE_31: операнд дедупа STATE — стоящее '
                  'состояние объекта радиуса, а не статус отчёта',
        # Разрешённых мест нет: оба дома выражают ДЕЙСТВУЮЩУЮ редакцию, а
        # шаблон снятой в них не встречается. Файл docs/** в allowed не
        # попадает никогда — это проверяет базовый гейт ниже.
        'allowed': (),
        'population': (('docs/models/domain/other/AnomalyReport.md', None),
                       ('docs/rules/error-handling-policy.md', None),
                       ('docs/rules/loss-streak-halt.md', r'дедуп\w*\s+по\s+стоящему\s+состоянию'),
                       ('docs/rules/manual-halt.md', None),
                       ('docs/spec/manual-halt.json', None),
                       ('.claude/work/backlog.md', None),
                       ('.claude/work/questions/open-questions.md', None)),
    },
    {
        'name': 'эпизод адресуется одним externalId',
        'pattern': r'наблюдение\s+того\s+же\s+идентификатора\s+обновляет\s+свою\s+строку'
                   r'|эпизод\w*\s+адресу\w+\s+одним\s+`?externalId`?',
        'arrived': r'externalCreatedAt',
        'date': '2026-08-30',
        'source': 'закрытие: адресуемая единица эпизода — пара externalId + externalCreatedAt',
        'allowed': (),
        'population': (('docs/models/domain/core/Position.md', None),
                       ('docs/models/mapping/Position.md', None),
                       ('docs/lifecycles/Position.md', None)),
    },
    {
        'name': 'величина-счётчик покрытия популяции',
        # Механизм такой величины не производит и никогда не производил:
        # покрытие считает раннер по ключу populations. Предписание жило в
        # ТРЁХ носителях сразу и было неисполнимо в каждом.
        'pattern': r'величин\w*-счётчик\w*\s+покрытия|счётчик\w*\s+покрытия',
        'arrived': r'ключ\w*\s+`?populations`?|`rule`|проверк\w+\s+правила\s+на\s+нём',
        'date': '2026-08-31',
        'source': 'решение держателя: происхождение перечня и критерий покрытия '
                  '(.claude/decisions/population-origin-and-code-gate.md)',
        'allowed': ('.claude/decisions/population-origin-and-code-gate.md',
                    '.claude/work/decision-digest.md'),
        'population': (('.claude/processes/roadmap-step-execution.md', None),
                       ('.claude/skills/closure-population.md', None),
                       ('.claude/skills/update-roadmap-progress.md', None),
                       ('.claude/decisions/closure-completeness-by-population.md', None)),
    },
    {
        'name': 'гейт CODE — чистый DOCS_CHECK',
        # Утвердительная форма: «гейт CODE — чистый прогон», «прогон прошёл
        # чисто ⇒ статус CODE». Действующая редакция говорит о КЛАССАХ
        # находок и о нуле незакрытых в двух из них.
        'pattern': r'[Гг]ейт\s+`?CODE`?\s*[—:-]\s*чист\w+'
                   r'|чист\w+\s+`?DOCS_CHECK`?[^.\n]{0,40}(?:услови\w+|гейт)'
                   r'|до\s+чистого\s+прогона\s+статус\s+`?CODE`?',
        'arrived': r'классы?\s+«?`?КОД`?»?\s+и\s+«?`?РИСК`?»?|code-gate-check|в\s+ноль\s+незакрытых',
        'date': '2026-08-31',
        'source': 'решение держателя: исполнимый критерий выхода в CODE по классам '
                  '(.claude/decisions/population-origin-and-code-gate.md)',
        'allowed': ('.claude/decisions/population-origin-and-code-gate.md',
                    '.claude/work/decision-digest.md'),
        'population': (('.claude/processes/roadmap-step-execution.md',
                        r'в\s+ноль\s+незакрытых'),
                       ('.claude/skills/update-roadmap-progress.md',
                        r'code-gate-check'),
                       ('.claude/skills/classify-code-blocking.md', None)),
    },
    {
        'name': 'пятый источник цикла добычи',
        # Закрытие развело добычу заявки и добычу материализованной защиты на
        # ДВА цикла; редакция «пятая нога первого» жила в трёх носителях, а
        # счёт «пять источников» стоя́л над таблицей из четырёх строк.
        'pattern': r'пят\w+\s+(?:источник\w*|ног\w+)\s+цикла\s+добычи|пять\s+источников',
        'unless': r'не\s+пят\w+\s+ног\w+|а\s+не\s+пятая',
        'arrived': r'цикл\w*\s+добычи\s+материализованной\s+защиты|четыре\s+источника|второй\s+цикл',
        'date': '2026-08-31',
        'source': 'закрытие GAPS_CLOSE_32: цикл добычи материализованной защиты — '
                  'второй цикл, а не пятая нога первого',
        'allowed': ('.claude/work/decision-digest.md',),
        'population': (('docs/models/mapping/Order.md', None),
                       ('docs/components/AttachedAlgoOrderStateResolver.md', None),
                       ('docs/models/integrations/okx/AlgoOrderOkxResponse.md', None)),
    },
    {
        'name': 'пункт 10 реестра предусловий гейтит CODE',
        # Гейтящий статус снят решением держателя 2026-08-30: эпизод ADL
        # инициирует биржа, на demo он не заказуем. Слот выведен из-под гейта
        # и строки в таблице реестра не имеет.
        'pattern': r'п\.\s*10[^.\n]{0,60}(?:гейт\w+|предуслови\w+)\s+`?CODE`?'
                   r'|предусловие\s+`?CODE`?\s+п\.\s*10'
                   r'|пп\.\s*[\d,\s]*\b10\b[\d,\s]*\)',
        'arrived': r'выведен\w*\s+из-под\s+гейта|гейтящ\w+\s+статус\s+снят',
        'date': '2026-08-30',
        'source': 'решение держателя: неупорядочиваемый факт закрывается заменителями '
                  '(.claude/decisions/unorderable-fact-substitutes.md)',
        'allowed': ('.claude/decisions/unorderable-fact-substitutes.md',
                    '.claude/work/decision-digest.md'),
        'population': (('.claude/tests/source-api/okx/plan.md',
                        r'выведен\w*\s+из-под\s+гейта'),
                       ('.claude/tests/source-api/okx/coverage-manifest.md',
                        r'выведен\w*\s+из-под\s+гейта'),
                       ('.claude/tests/source-api/okx/code-preconditions.md', None)),
    },
    {
        'name': 'Postman-коллекция — обязательный аппрув-артефакт контура',
        # Утвердительная форма: аппрув на ПАРУ, ревью плана И коллекции,
        # коллекция как ревью/аппрув-артефакт. Действующая редакция:
        # аппрув-артефакт — план, коллекция необязательна.
        'pattern': r'пар\w+\s+«?план\s*\+\s*коллекц\w+'
                   r'|план\s*\+\s*коллекци\w+\s+(?:прошли|уход\w+|аппрув\w+)'
                   r'|коллекци\w+\s+—\s+ревью/аппрув-\s*артефакт'
                   r'|аппрув\w*\s+плана\s*\+\s*коллекции',
        'arrived': r'аппрув-артефакт\w*\s+(?:контура\s+)?—\s+план|коллекци\w+\s+необязательн\w+'
                   r'|аппрув-артефактом[^.\n]{0,24}не\s+(?:являетс|служ)\w+'
                   r'|необязательн\w+\s+справк\w+',
        'date': '2026-08-31',
        'source': 'решение держателя: коллекция не аппрув-артефакт '
                  '(.claude/decisions/collection-not-approval-artifact.md)',
        'allowed': ('.claude/decisions/collection-not-approval-artifact.md',
                    '.claude/work/decision-digest.md'),
        'population': (('.claude/processes/source-api-testing.md', None),
                       ('.claude/skills/test-collection.md', None),
                       ('.claude/skills/test-review.md', None),
                       ('.claude/skills/test-code.md', None),
                       ('.claude/skills/test-design.md', None),
                       ('.claude/agents/tester.md', None),
                       ('.claude/tests/source-api/okx/plan.md', None)),
    },
    {
        'name': 'две ноги истории цикла добычи защиты',
        # Перечень ног был собран из пары state, которые имелись в виду,
        # а не из контракта эндпоинта, у которого терминальных state ТРИ:
        # order_failed не опрашивался, разбор «исчерпывался» на
        # существующем факте (сработала и не исполнилась). Встречная
        # форма «идёт двумя вызовами» у самостоятельной условной заявки
        # (mapping/AlgoOrder.md) законна, потому что третий терминал там
        # предъявляет точечная нога details, — негативный просмотр
        # пропускает носителя, называющего order_failed рядом.
        'pattern': r'ног\s+у\s+истории\s+две'
                   r'|истори\w+\s+ид[её]т\s+двумя\s+вызовами(?!.{0,400}order_failed)',
        'arrived': r'order_failed',
        'date': '2026-09-01',
        'source': 'C2 DOCS_CHECK_33: перечень ног выводится из контракта '
                  'эндпоинта (терминалов три), узел 1 GAPS_CLOSE_33',
        'allowed': ('.claude/work/code-gate-ledger.json',),
        'population': (('docs/models/mapping/Order.md', None),
                       ('docs/models/mapping/AlgoOrder.md', None),
                       ('docs/lifecycles/Order.md', None),
                       ('docs/components/RefreshOrderExecutor.md', None)),
    },
    {
        'name': 'суррогат границы — биржевой момент создания сделки',
        # Снятая редакция: нижняя граница окна (линковки движений и
        # адресации истории позиций) при пустой колонке — «биржевой
        # момент создания сделки» БЕЗ оговорки тропы. Пришедшая: суррогат
        # берётся значением externalCreatedAt, куда восстановительная
        # тропа пишет биржевое время ОТКРЫТИЯ наблюдённой позиции
        # (решение держателя 2026-09-01). Фраза «биржевой момент создания
        # сделки» сама по себе законна у штатной тропы — шаблон ловит
        # только связку с пустой колонкой/суррогатом в снятом порядке.
        'pattern': r'пуст\w+\s+колонк\w+[^.]{0,80}биржевы?м?\s+момент\w*\s+создания\s+сделки'
                   r'|суррогат[^.]{0,60}биржев\w+\s+момент\w*\s+создания\s+сделки',
        'arrived': r'открыти[яе]\s+наблюдённой\s+позиции|ОТКРЫТИЯ\s+наблюдённой',
        'date': '2026-09-01',
        'source': 'решение держателя: граница — время открытия наблюдённой позиции '
                  '(.claude/decisions/recovered-deal-linkage-window-bound.md)',
        'allowed': ('.claude/decisions/recovered-deal-linkage-window-bound.md',
                    '.claude/work/decision-digest.md',
                    '.claude/work/code-gate-ledger.json'),
        'population': (('docs/models/domain/aggregate/Deal.md', None),
                       ('docs/components/DealOpeningService.md', None),
                       ('docs/components/RefreshBillsExecutor.md', None),
                       ('docs/components/RefreshPositionExecutor.md', None),
                       ('docs/lifecycles/Deal.md', None),
                       ('docs/components/SubmitOrderExecutor.md', None),
                       ('docs/integrations/okx/contracts/server-time.md', None),
                       ('docs/spec/cash-flow-linkage.json', None)),
    },
    {
        'name': 'радиус шире инструмента — набором строк',
        # Встречная форма снятой редакции «групповой И БИРЖЕВОЙ радиусы
        # выражаются набором строк»: «шире инструмента» покрывает и
        # биржу, для которой клауза ложна — у неё своё значение
        # HoldScope.EXCHANGE, своя строка статуса и своя строка отчёта
        # (а на ручной поверхности — один вызов, не набор). Пришедшая
        # редакция разводит: набором строк/вызовов — только групповой,
        # биржевой — своим значением, своей строкой, одним вызовом.
        # Шаблон ловит и форму «набор вызовов» — четвёртый носитель нашла
        # мини-петля после свипа по «набором строк».
        'pattern': r'радиус\w*\s+шире\s+инструмента\s+выража\w+\s+набором'
                   r'|шире\s+инструмента\s+—\s+набор\w*\s+(строк|вызовов)',
        'arrived': r'групповой\s+радиус[^.]{0,120}набор\w*\s+(строк|вызовов)'
                   r'|биржев\w+\s+радиус\s+сюда\s+не\s+относится',
        'date': '2026-09-01',
        'source': 'B1 DOCS_CHECK_33: встречная форма снятой редакции, '
                  'узел 5 GAPS_CLOSE_33',
        'allowed': ('.claude/work/code-gate-ledger.json',),
        'population': (('docs/rules/error-handling-policy.md', None),
                       ('docs/rules/instrument-hold.md', None),
                       ('docs/components/models/HoldSignal.md', None),
                       ('docs/models/domain/other/AnomalyReport.md', None),
                       ('docs/rules/manual-halt.md', None)),
    },
    {
        'name': 'после FAILED новая строка законна безусловно',
        # Снятая редакция: отказавшая надобность повторяется новой строкой
        # без гейта («новая строка законна») — петля «N попыток -> FAILED ->
        # новая строка» без предела. Пришедшая: повтор гейтится стоящей
        # ступенью радиуса и возобновляется её снятием.
        # Ограничение негативного просмотра объявлено: возрождение,
        # упоминающее «ступень»/«гейт» в радиусе 200 символов в ЛЮБОМ
        # качестве («…законна, ступень ни при чём»), шаблон пропустит —
        # его держит популяция носителей (пришедшая редакция обязана
        # стоять в каждом), как у записей «двух ног» и суррогата.
        'pattern': r'нов\w+\s+строк\w+\s+законн(?!.{0,200}(ступен|гейт))',
        'arrived': r'гейт\w*\s+по\s+стоящей\s+ступени|стоящ\w+\s+ступень\w*|stepRetryGated'
                   r'|после\s+снятия\s+ступени',
        'date': '2026-09-01',
        'source': 'B3 DOCS_CHECK_33: судьба надобности после FAILED — гейт '
                  'по стоящей ступени, узел 5 GAPS_CLOSE_33',
        'allowed': ('.claude/work/code-gate-ledger.json',),
        'population': (('docs/rules/strategy-step-once-per-episode.md', None),
                       ('docs/components/StrategyActionOrchestrator.md', None),
                       ('docs/rules/instrument-hold.md',
                        r'стоящ\w+\s+ступень\w*|гейт\w*\s+по\s+стоящей'),
                       ('docs/lifecycles/DealActionState.md', None),
                       ('docs/spec/strategy-walkthrough.json', None)),
    },
    {
        'name': 'популяция предиката стороны — только после входа',
        # Снятая редакция: primaryStopOnLossSide охраняет «первичную
        # защитную заявку ПОСЛЕ входа», а «на входной тропе первым
        # отказывает сайзинг» безусловно; рубежи «охраняют РАЗНЫЕ
        # популяции». В полосе между якорем и безубытком (ширина
        # ~2f*P/(1-f)) знаменатель положителен, сайзинг проходит, и
        # исключённый из популяции вход не охранял никто. Пришедшая:
        # популяция предиката — вся первичная постановка с объявленным
        # уровнем, вход включая; рубежи различаются предметом.
        # Широта шаблона объявлена: альтернации — родовые фразы, и
        # будущий законный текст о ДРУГОМ предмете может их произнести
        # без возрождения снятой редакции; ложное срабатывание тогда
        # разбирается вручную (та же цена, что у записей «двух ног»,
        # суррогата и гейта повтора, — популяция носителей важнее
        # точности шаблона).
        'pattern': r'защитн\w+\s+заявк\w+\s+после\s+входа'
                   r'|охраня\w+\s+РАЗНЫЕ\s+популяции'
                   r'|разные\s+популяции\s+действий',
        'arrived': r'вход\w*\s+включая|включает\s+\**вход|полос\w+\s+(между|у)\s+якор'
                   r'|клетку\s+«вход',
        'date': '2026-09-01',
        'source': 'D-G1 DOCS_CHECK_33: клетка «вход × полоса» без охраны, '
                  'узел 6 GAPS_CLOSE_33',
        'allowed': ('.claude/work/code-gate-ledger.json',),
        'population': (('docs/rules/risk-policy.md', None),
                       ('docs/spec/order-sizing.json', None),
                       ('docs/components/SizeCalculator.md', None),
                       ('docs/spec/stop-distance.json',
                        r'ПОЛОСЕ\s+между\s+якорем|полос\w+\s+между\s+якорем')),
    },
]


def flat_text(path):
    with io.open(path, encoding='utf-8', errors='replace') as handle:
        return re.sub(r'\s*\n\s*', ' ', handle.read())


def carriers(roots):
    found = []
    for pattern in roots:
        for path in glob.glob(pattern, recursive=True):
            if any(skip in '/' + path.replace(os.sep, '/') for skip in SKIP):
                continue
            found.append(path)
    return sorted(set(found))


def registry_incomplete(registry):
    """Базовый гейт реестра: запись без пришедшей редакции или без популяции
    оставляет ось 4 неизмеренной, а прогон — зелёным. Это ложный зелёный."""
    for entry in registry:
        for key in ('pattern', 'arrived', 'population'):
            if not entry.get(key):
                return 'запись «%s» не несёт «%s» — ось популяции не измерима' % (
                    entry.get('name', '?'), key)
        for place in entry['allowed']:
            if place.replace(os.sep, '/').startswith('docs/'):
                return ('запись «%s» держит в разрешённых местах файл корпуса «%s»: '
                        'docs/concept.md запрещает слой опровержения в корпусе, '
                        'и allow-лист там означал бы подавитель, а не разрешение'
                        % (entry.get('name', '?'), place))
    return None


def scan(registry, roots):
    if not registry:
        return None, 'реестр снятых редакций пуст — проверять нечего'
    incomplete = registry_incomplete(registry)
    if incomplete:
        return None, incomplete
    files = carriers(roots)
    if not files:
        return None, 'ни одного живого носителя не найдено — проверять нечего'
    hits = []
    self_name = os.path.basename(__file__)
    # --- оси 1-3: снятая редакция жива в потребителе
    for path in files:
        normalized = path.replace(os.sep, '/')
        if os.path.basename(normalized) == self_name:
            # Сам реестр — разрешённое место для КАЖДОЙ своей записи: он их и
            # объявляет. Перечислять его в allowed у каждой строки значило бы
            # держать одно и то же знание в N местах.
            continue
        text = flat_text(path)
        for entry in registry:
            if normalized in entry['allowed']:
                continue
            unless = entry.get('unless')
            for match in re.finditer(entry['pattern'], text, re.I):
                if unless:
                    window = text[max(0, match.start() - 160):match.end() + 160]
                    if re.search(unless, window, re.I):
                        # Действующая редакция: термин стои́т рядом с отрицанием.
                        continue
                hits.append((path, entry['name'], 'снятая редакция жива',
                             match.group(0)[:60], entry['date'], entry['source']))
    # --- оси 4-5: популяция носителей предъявляет пришедшую редакцию
    population_checked = 0
    for entry in registry:
        for member, own_arrived in entry['population']:
            population_checked += 1
            expected = own_arrived or entry['arrived']
            if not os.path.exists(member):
                hits.append((member, entry['name'],
                             'строка популяции указывает на носитель, которого нет',
                             '', entry['date'], entry['source']))
                continue
            text = flat_text(member)
            if not re.search(expected, text, re.I):
                hits.append((member, entry['name'],
                             'пришедшая редакция не дошла до носителя популяции',
                             expected[:40], entry['date'], entry['source']))
    return (len(files), population_checked, hits), None


# --- батарея осей ------------------------------------------------------------

def battery():
    axes = []
    with tempfile.TemporaryDirectory() as work:
        def page(name, body):
            path = os.path.join(work, name)
            with io.open(path, 'w', encoding='utf-8') as handle:
                handle.write(body)
            return os.path.join(work, '*.md')

        def path_of(name):
            return os.path.join(work, name).replace(os.sep, '/')

        def probe(population, allowed=None):
            return [{
                'name': 'проба',
                'pattern': r'снят\w+\s+редакци\w+\s+живёт',
                'arrived': r'пришедш\w+\s+редакци\w+\s+стои́т',
                'date': '2026-08-31',
                'source': 'проба',
                'allowed': allowed if allowed is not None else (path_of('дом.md'),),
                'population': population,
            }]

        # Носитель популяции, всегда предъявляющий пришедшую редакцию: чтобы
        # оси 1-3 мерились без побочного срабатывания оси 4.
        page('популяция-ок.md', '# ПО\n\nЗдесь пришедшая редакция стои́т.\n')
        ok_population = ((path_of('популяция-ок.md'), None),)

        roots = page('потребитель.md', '# П\n\nЗдесь снятая редакция живёт до сих пор.\n')
        result, _ = scan(probe(ok_population), [roots])
        axes.append(('1. дословный термин в живом носителе',
                     any(kind == 'снятая редакция жива' for _, _, kind, _, _, _ in result[2]),
                     'дефектов: %d' % len(result[2])))

        os.remove(os.path.join(work, 'потребитель.md'))
        roots = page('перенос.md', '# П\n\nЗдесь снятая\nредакция живёт до сих пор.\n')
        result, _ = scan(probe(ok_population), [roots])
        axes.append(('2. термин, разорванный переносом строки',
                     any(kind == 'снятая редакция жива' for _, _, kind, _, _, _ in result[2]),
                     'дефектов: %d' % len(result[2])))

        os.remove(os.path.join(work, 'перенос.md'))
        roots = page('дом.md', '# Д\n\nПрежняя снятая редакция живёт — здесь она названа '
                               'затем, чтобы сказать, что её больше нет.\n')
        result, _ = scan(probe(ok_population), [roots])
        axes.append(('3. контроль: дом, фиксирующий снятие, разрешён', not result[2],
                     'дефектов: %d' % len(result[2])))

        os.remove(os.path.join(work, 'дом.md'))
        roots = page('чисто.md', '# Ч\n\nДействующая редакция и ничего больше.\n')
        result, _ = scan(probe(ok_population), [roots])
        axes.append(('4. контроль: чистый носитель находки не даёт', not result[2],
                     'дефектов: %d' % len(result[2])))

        # --- ось популяции: встречная форма
        page('встречная.md', '# В\n\nТо же самое, сказанное совершенно другими словами: '
                             'незавершённое ищется поиском.\n')
        result, _ = scan(probe(((path_of('встречная.md'), None),)), [roots])
        axes.append(('5. носитель популяции без пришедшей редакции (встречная форма)',
                     any(kind.startswith('пришедшая') for _, _, kind, _, _, _ in result[2]),
                     'дефектов: %d' % len(result[2])))

        page('перенос-пришедшей.md', '# ПП\n\nЗдесь пришедшая\nредакция стои́т.\n')
        result, _ = scan(probe(((path_of('перенос-пришедшей.md'), None),)), [roots])
        axes.append(('6. контроль: пришедшая редакция через перенос строки — не дефект',
                     not result[2], 'дефектов: %d' % len(result[2])))

        result, _ = scan(probe(((path_of('нет-такого-носителя.md'), None),)), [roots])
        axes.append(('7. строка популяции указывает на несуществующий носитель',
                     any(kind.startswith('строка популяции') for _, _, kind, _, _, _ in result[2]),
                     'дефектов: %d' % len(result[2])))

        result, _ = scan(probe(ok_population), [roots])
        axes.append(('8. контроль: носитель популяции с пришедшей редакцией — не дефект',
                     not result[2], 'дефектов: %d' % len(result[2])))

        page('свой-шаблон.md', '# СШ\n\nЗдесь стои́т переехавший клейм: носители клейма — '
                                'примеры ниже.\n')
        result, _ = scan(probe(((path_of('свой-шаблон.md'), None),)), [roots])
        own_missing = bool(result[2])
        result, _ = scan(probe(((path_of('свой-шаблон.md'), r'носител\w*\s+клейма'),)), [roots])
        axes.append(('9. свой шаблон носителя перекрывает шаблон записи',
                     own_missing and not result[2],
                     'по шаблону записи дефект есть, по своему — нет: %s'
                     % (own_missing and not result[2])))

        page('отрицание.md', '# О\n\nЗдесь снятая редакция живёт до сих пор — впрочем, нет: '
                              'действующая редакция отрицает её прямо.\n')
        neg = probe(ok_population)
        neg[0]['unless'] = r'действующая\s+редакция\s+отрицает'
        result, _ = scan(neg, [os.path.join(work, '*.md')])
        axes.append(('14. отрицание рядом с термином находкой не считается',
                     not any(k == 'снятая редакция жива' for _, _, k, _, _, _ in result[2]),
                     'дефектов: %d' % len(result[2])))
        result, _ = scan(probe(ok_population), [os.path.join(work, '*.md')])
        axes.append(('15. контроль оси 14: без unless тот же текст — находка',
                     any(k == 'снятая редакция жива' for _, _, k, _, _, _ in result[2]),
                     'дефектов: %d' % len(result[2])))
        os.remove(os.path.join(work, 'отрицание.md'))

        # --- отказы
        _, refusal = scan([], [roots])
        axes.append(('16. пустой реестр — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        _, refusal = scan(probe(ok_population), [os.path.join(work, 'нет-такого', '*.md')])
        axes.append(('17. корпус пуст — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        broken = probe(ok_population)
        broken[0]['population'] = ()
        _, refusal = scan(broken, [roots])
        axes.append(('18. запись без популяции — проверка отказывает', bool(refusal),
                     refusal or 'проверка отчиталась'))
        broken = probe(ok_population)
        broken[0]['arrived'] = ''
        _, refusal = scan(broken, [roots])
        axes.append(('19. запись без шаблона пришедшей редакции — проверка отказывает',
                     bool(refusal), refusal or 'проверка отчиталась'))
    return axes


def main():
    axes = battery()
    print('--- батарея осей детектора (исполняется той же командой)')
    for title, passed, observed in axes:
        print('  %s: %s — %s' % ('доказана' if passed else 'НЕ ДОКАЗАНА', title, observed))
    broken = [title for title, passed, _ in axes if not passed]
    if broken:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: недоказанных осей %d — чистый свип '
              'ничего не удостоверял бы' % len(broken))
        return 2

    result, refusal = scan(RETIRED, ROOTS)
    if refusal:
        print('ПРОВЕРКА НЕ ПРОВОДИТСЯ: ' + refusal)
        return 2
    checked, population_checked, hits = result
    print('живых носителей просмотрено: %d; записей реестра: %d; '
          'строк популяции проверено: %d; ДЕФЕКТОВ: %d'
          % (checked, len(RETIRED), population_checked, len(hits)))
    for path, name, kind, sample, date, source in hits:
        tail = ('; вхождение: «%s…»' % sample) if sample else ''
        print('  ДЕФЕКТ [%s]: %s — «%s» (снято %s: %s)%s'
              % (kind, path, name, date, source, tail))
    return 1 if hits else 0


if __name__ == '__main__':
    sys.exit(main())
