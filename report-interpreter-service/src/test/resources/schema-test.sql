CREATE TABLE IF NOT EXISTS report_job (
  report_id        VARCHAR(64)  NOT NULL PRIMARY KEY,
  account_id       VARCHAR(64)  NOT NULL,
  patient_id       VARCHAR(64)  NOT NULL,
  idempotency_key  VARCHAR(64)  NOT NULL,
  status           VARCHAR(32)  NOT NULL,
  epoch            BIGINT       NOT NULL,
  created_at       TIMESTAMP(3) NOT NULL,
  updated_at       TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_report_job_idem UNIQUE (account_id, idempotency_key)
);
