-- Индекс под ключ дедупа и подтверждения гистерезиса.
--
-- Каждая находка каждого детектора с гистерезисом спрашивает на КАЖДОМ
-- тике «стоит ли отчёт по этому ключу в окне» (AnomalyReportRepository
-- .existsStanding). Без индекса это скан всей таблицы журнала, а журнал
-- растёт: связка «чем хуже состояние контура, тем больше строк и тем
-- дороже проверка» кормила бы сама себя.
--
-- Порядок колонок — от самых селективных равенств к диапазону по
-- времени: равенства ключа сужают, created_at отсекает окно.
-- instrument_id и subject_external_id nullable, и предикат сравнивает
-- пустое с пустым, поэтому в индекс они входят наравне с остальными.
create index ix_anomaly_report_standing
    on anomaly_reports (exchange_id, code, severity, instrument_id, subject_external_id, created_at desc);

comment on index ix_anomaly_report_standing is
    'Ключ дедупа отчёта-состояния и операнд подтверждения гистерезиса: '
    'объект радиуса + сущность-предмет + code + severity, окно по created_at '
    '(docs/models/domain/other/AnomalyReport.md).';
