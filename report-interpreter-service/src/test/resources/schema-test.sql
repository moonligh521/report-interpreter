CREATE TABLE IF NOT EXISTS report_job (
  report_id        VARCHAR(64)  NOT NULL PRIMARY KEY,
  account_id       VARCHAR(64)  NOT NULL,
  patient_id       VARCHAR(64)  NOT NULL,
  idempotency_key  VARCHAR(64)  NOT NULL,
  status           VARCHAR(32)  NOT NULL,
  epoch            BIGINT       NOT NULL,
  report_type      VARCHAR(32),
  request_hash     VARCHAR(64),
  source_text      CLOB,
  created_at       TIMESTAMP(3) NOT NULL,
  updated_at       TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_report_job_idem UNIQUE (account_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS report_parse_result (
  id                VARCHAR(64)   NOT NULL PRIMARY KEY,
  report_id         VARCHAR(64)   NOT NULL,
  parse_version     INT           NOT NULL,
  active            TINYINT       NOT NULL,
  report_type       VARCHAR(32)   NOT NULL,
  parse_confidence  DECIMAL(5,4)  NOT NULL,
  status            VARCHAR(32)   NOT NULL,
  request_key       VARCHAR(64),
  created_at        TIMESTAMP(3)  NOT NULL,
  updated_at        TIMESTAMP(3)  NOT NULL,
  CONSTRAINT uk_parse_report_version UNIQUE (report_id, parse_version),
  CONSTRAINT uk_parse_request_key UNIQUE (report_id, request_key)
);

CREATE TABLE IF NOT EXISTS report_indicator (
  id                    VARCHAR(64)    NOT NULL PRIMARY KEY,
  parse_result_id       VARCHAR(64)    NOT NULL,
  report_id             VARCHAR(64)    NOT NULL,
  parse_version         INT            NOT NULL,
  raw_name              VARCHAR(128)   NOT NULL,
  raw_value             VARCHAR(64)    NOT NULL,
  raw_unit              VARCHAR(32),
  canonical_code        VARCHAR(32),
  specimen_type         VARCHAR(32),
  normalized_value      DECIMAL(20,6),
  normalized_unit       VARCHAR(32),
  normalization_status  VARCHAR(32)    NOT NULL,
  ref_low               DECIMAL(20,6),
  ref_high              DECIMAL(20,6),
  conf_ocr              DECIMAL(5,4)   NOT NULL,
  conf_structure        DECIMAL(5,4)   NOT NULL,
  conf_extraction       DECIMAL(5,4)   NOT NULL,
  conf_normalization    DECIMAL(5,4)   NOT NULL,
  conf_name             DECIMAL(5,4)   NOT NULL,
  conf_value            DECIMAL(5,4)   NOT NULL,
  conf_unit             DECIMAL(5,4)   NOT NULL,
  conf_ref_range        DECIMAL(5,4)   NOT NULL
);

CREATE TABLE IF NOT EXISTS report_source_evidence (
  id            VARCHAR(64)  NOT NULL PRIMARY KEY,
  indicator_id  VARCHAR(64)  NOT NULL,
  page_index    INT          NOT NULL,
  raw_text      VARCHAR(2000) NOT NULL,
  region_json   VARCHAR(512)
);