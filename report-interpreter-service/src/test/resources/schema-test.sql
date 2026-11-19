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
  expected_patient_name VARCHAR(128),
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

CREATE TABLE IF NOT EXISTS report_rule_execution (
  id                     VARCHAR(64)  NOT NULL PRIMARY KEY,
  report_id              VARCHAR(64)  NOT NULL,
  parse_version          INT          NOT NULL,
  rule_version           VARCHAR(32)  NOT NULL,
  indicator_id           VARCHAR(64),
  canonical_code         VARCHAR(32),
  reference_rule_id      VARCHAR(64),
  critical_rule_id       VARCHAR(64),
  context_snapshot_json  CLOB         NOT NULL,
  reference_result       VARCHAR(32)  NOT NULL,
  critical_result        VARCHAR(32)  NOT NULL,
  applicability_result   VARCHAR(32)  NOT NULL,
  created_at             TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS report_critical_event (
  event_id         VARCHAR(64)  NOT NULL PRIMARY KEY,
  report_id        VARCHAR(64)  NOT NULL,
  parse_version    INT          NOT NULL,
  canonical_code   VARCHAR(32)  NOT NULL,
  critical_level   VARCHAR(16)  NOT NULL,
  event_status     VARCHAR(32)  NOT NULL,
  detection_time   TIMESTAMP(3) NOT NULL,
  idempotency_key  VARCHAR(190) NOT NULL,
  CONSTRAINT uk_critical_event_idem UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report_critical_notification_outbox (
  outbox_id            VARCHAR(64)  NOT NULL PRIMARY KEY,
  event_id             VARCHAR(64)  NOT NULL,
  notification_status  VARCHAR(32)  NOT NULL,
  delivery_channel     VARCHAR(32)  NOT NULL,
  idempotency_key      VARCHAR(190) NOT NULL,
  attempt_count        INT          NOT NULL,
  next_retry_at        TIMESTAMP(3),
  last_error_code      VARCHAR(64),
  delivered_at         TIMESTAMP(3),
  created_at           TIMESTAMP(3) NOT NULL,
  updated_at           TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_critical_outbox_idem UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report_review_task (
  review_task_id   VARCHAR(64)  NOT NULL PRIMARY KEY,
  report_id        VARCHAR(64)  NOT NULL,
  reason           VARCHAR(512) NOT NULL,
  priority         VARCHAR(16)  NOT NULL,
  status           VARCHAR(16)  NOT NULL,
  idempotency_key  VARCHAR(64)  NOT NULL,
  created_at       TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_review_task_idem UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report_rule_summary (
  report_id               VARCHAR(64)  NOT NULL,
  parse_version           INT          NOT NULL,
  rule_version            VARCHAR(32)  NOT NULL,
  identity_status         VARCHAR(16)  NOT NULL,
  identity_detail         VARCHAR(256),
  coverage_status         VARCHAR(16)  NOT NULL,
  coverage_evidence_json  CLOB         NOT NULL,
  outcome_status          VARCHAR(32)  NOT NULL,
  archive_blocked         TINYINT      NOT NULL,
  safe_normal_allowed     TINYINT      NOT NULL,
  block_code              INT,
  created_at              TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (report_id, parse_version)
);