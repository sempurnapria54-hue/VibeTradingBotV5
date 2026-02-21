ALTER TABLE position
    ALTER COLUMN u_time TYPE TIMESTAMPTZ USING CASE
        WHEN u_time IS NULL THEN NULL
        ELSE to_timestamp(u_time::double precision / 1000)
        END;

ALTER TABLE "order"
    ALTER COLUMN c_time TYPE TIMESTAMPTZ USING CASE
        WHEN c_time IS NULL THEN NULL
        ELSE to_timestamp(c_time::double precision / 1000)
        END,
    ALTER COLUMN u_time TYPE TIMESTAMPTZ USING CASE
        WHEN u_time IS NULL THEN NULL
        ELSE to_timestamp(u_time::double precision / 1000)
        END;

ALTER TABLE algo_order
    ALTER COLUMN c_time TYPE TIMESTAMPTZ USING CASE
        WHEN c_time IS NULL THEN NULL
        ELSE to_timestamp(c_time::double precision / 1000)
        END,
    ALTER COLUMN u_time TYPE TIMESTAMPTZ USING CASE
        WHEN u_time IS NULL THEN NULL
        ELSE to_timestamp(u_time::double precision / 1000)
        END;
