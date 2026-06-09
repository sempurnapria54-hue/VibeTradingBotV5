# Дистилляция торговой библиотеки — прогресс

## На какой вопрос отвечает этот файл

На каком шаге задача дистилляции торговой библиотеки в рабочее
знание (`.claude/library/trading/distilled/`).

## Задача

6 PDF корпуса → компактный дистиллят по 4 пиллерам + манипуляции
кросс-каттингом. Тезисы со ссылками книга+глава+страница. Только
то, что в книгах есть; пробелы помечать явно; расхождения
фиксировать. Агента/фокус/процесс не трогать.

## Выходные файлы

- `.claude/library/trading/distilled/corpus-map.md` — карта корпуса
  (книги, издания, офсеты PDF↔печатная страница).
- `.claude/library/trading/distilled/risk-and-sizing.md`
- `.claude/library/trading/distilled/system-design.md`
- `.claude/library/trading/distilled/strategy-patterns.md`
- `.claude/library/trading/distilled/microstructure.md`
- Манипуляции — секции внутри microstructure (стакан/исполнение)
  и risk-and-sizing (каскады/хвосты), не пятый файл.

## Техника

- Read PDF недоступен (нет poppler) → текст извлечён pypdf:
  `C:\Users\RomanKrd\AppData\Local\Temp\pdfx\<short>.txt`
  с маркерами `=== [PDF p.N] ===`. Скрипт:
  `C:\Users\RomanKrd\AppData\Local\Temp\pdfx_extract.py`
  (перезапуск: `py pdfx_extract.py [short...]`).
- Короткие имена: vince, tharp, carver-st, carver-afts, kaufman,
  harris.
- Поиск по книгам — Grep по txt, чтение — Read с offset/limit.

## Статус шагов

- [x] Экстракция текста (все 6 книг; Harris — через PyMuPDF,
      битый xref для pypdf)
- [x] ToC-карта: главы по пиллерам + офсеты страниц на книгу
      (corpus-map.md создан)
- [x] Пиллер «риск и сайзинг»: Vince (введение, гл. 1-2, 4-5,
      7-8), Tharp (гл. 6, 9, 11-12), Carver ST (гл. 9-10), Kaufman
      (гл. 23-24), AFTS (тактика 4). Секция 12 (хвосты/каскады)
      заполнится из Harris на пиллере микроструктуры.
- [x] Пиллер «системный дизайн»: Carver ST (гл. 2-4, 7-8, 11-12),
      AFTS (тактика 1), Kaufman (гл. 1, 21), Tharp (гл. 4, 8, 10).
- [x] Пиллер «паттерны стратегий»: AFTS (каталог 30 стратегий +
      правила ST гл. 7), Kaufman (семейства гл. 1, 5, 8-20), Tharp
      (гл. 5 концепты).
- [x] Пиллер «микроструктура» + манипуляции: Harris (гл. 4, 11-14,
      19, 28); каскады/хвосты заполнены и в risk-and-sizing §12.
- [x] Финализация: corpus-map создан; gitignore-исключение для
      distilled/ (проверено check-ignore: PDF игнорируются,
      distilled версионируется); строка distilled/ в structure.md;
      все 5 файлов git add (staged).

## Статус: завершено. Готово к ручному ревью в IDEA.

5 файлов дистиллята в `.claude/library/trading/distilled/`:
corpus-map, risk-and-sizing, system-design, strategy-patterns,
microstructure. Каждый — со ссылками книга+глава+страница, секциями
расхождений и пробелов корпуса (крипто-специфика помечена как
пробел, не достроена). Границы задачи соблюдены: агент/фокус/процесс
не тронуты, корпус не расширен, «сбалансированность» не определена.

Временные txt-экстракты — в `%TEMP%\pdfx\` (вне репо); скрипты
`pdfx_extract.py` / `pdfx_harris.py` там же, для возможной досверки.

## Решения по ходу

- Дом дистиллята: `.claude/library/trading/distilled/` — тот же
  вопрос «Что говорит торговый первоисточник?», та же строка
  structure.md («одно знание — один дом»). PDF позже перенесены в
  `raw/`; в `.gitignore` гитигнорится только `raw/`, дистиллят —
  версионируемое знание.
- Указатели страниц: печатная страница книги (как в колонтитулах);
  офсет PDF = печатная + N фиксируется в corpus-map.md per книга.
  Для epub-конверта (AFTS), если печатной пагинации нет, — PDF-стр.
