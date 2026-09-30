-- Сжатие старых чанков невосполнимых срезов (docs/architecture/data-ownership.md
-- §«Временные ряды»).
--
-- ПОЧЕМУ СРЕЗЫ, А НЕ ВСЕ РЯДЫ. Невосполнимое не чистится никогда
-- (docs/rules/market-data-retention.md), и его объём растёт без предела —
-- сжатие здесь единственный рычаг. Писатель срезов пишет только МОМЕНТ
-- прохода: строка ложится в текущий чанк, и в старые чанки вставок нет вовсе,
-- как нет и правок. Свечи сюда не входят: бэкфилл и починка дыр пишут в
-- произвольно старые недели, а чанк свечей общий для всех групп — новое
-- требование ложилось бы в сжатый чанк на каждом бэкфилле, и окна записи,
-- старше которого можно сжимать, у свечей нет.
--
-- ВРЕМЯ — ЦЕЛОЕ (UTC-миллисекунды, V1), поэтому политике нужен источник
-- «сейчас» в той же единице: без него TimescaleDB не знает, какой чанк стар.
--
-- ПОРОГ СЖАТИЯ — 7 суток (604800000 мс). Величина калибровочная и
-- провизорная: писатель — владелец market-data, пересмотр — по наблюдаемому
-- объёму ряда. Выведена так: окно записи — секунды у текущего момента, чанк —
-- сутки (V1); порог в неделю оставляет несжатыми семь суточных чанков, то
-- есть запас на задержку прохода и расхождение часов на порядки больше
-- самого окна, а цена запаса — неделя ряда без сжатия. Направление ошибки
-- названо: позже, чем раньше — поздно сжатый чанк стоит объёма, а вставка в
-- сжатый стоит декомпрессии сегмента на горячей тропе.
--
-- ПЕРИОД ЗАПУСКА — 12 часов: порог мерится сутками, чаще проверять нечего.
-- Первый запуск — через тот же период после миграции, а не сразу: свежая база
-- (стенд, чёрный ящик) пишет срезы с историческими биржевыми моментами, и
-- немедленный проход сжал бы чанк, в который тот же прогон ещё пишет.
--
-- Сегмент — инструмент: чтения срезов идут по инструменту (последний срез,
-- история для детекторов), и столбцы первичного ключа покрыты сегментом и
-- порядком, как требует сжатие гипертаблицы с уникальным ключом.

create function market_data_now_millis() returns bigint
    language sql
    stable
as
$$
select (extract(epoch from now()) * 1000)::bigint
$$;

comment on function market_data_now_millis() is 'Текущий момент в UTC-миллисекундах — единица времени гипертаблиц market-data; источник «сейчас» для политик TimescaleDB';

select set_integer_now_func('order_book_snapshots', 'market_data_now_millis');
select set_integer_now_func('ticker_snapshots', 'market_data_now_millis');

alter table order_book_snapshots set (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'instrument_id',
    timescaledb.compress_orderby = 'external_timestamp desc'
);

alter table ticker_snapshots set (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'instrument_id',
    timescaledb.compress_orderby = 'external_timestamp desc'
);

select add_compression_policy('order_book_snapshots',
                              compress_after => 604800000::bigint,
                              schedule_interval => interval '12 hours',
                              initial_start => now() + interval '12 hours');
select add_compression_policy('ticker_snapshots',
                              compress_after => 604800000::bigint,
                              schedule_interval => interval '12 hours',
                              initial_start => now() + interval '12 hours');
