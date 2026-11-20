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
  structure_json    CLOB,
  specimen_time     TIMESTAMP(3),
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
  conf_ref_range        DECIMAL(5,4)   NOT NULL,
  sub_report_id         VARCHAR(64),
  report_time           TIMESTAMP(3)
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
  acknowledged_at      TIMESTAMP(3),
  created_at           TIMESTAMP(3) NOT NULL,
  updated_at           TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_critical_outbox_idem UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS report_question (
  answer_id         VARCHAR(64)   NOT NULL PRIMARY KEY,
  report_id         VARCHAR(64)   NOT NULL,
  question          VARCHAR(2000) NOT NULL,
  focus_codes_json  VARCHAR(2000),
  idempotency_key   VARCHAR(64)   NOT NULL,
  response_json     CLOB,
  result_code       INT,
  created_at        TIMESTAMP(3)  NOT NULL,
  CONSTRAINT uk_report_question_idem UNIQUE (report_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS report_correction (
  id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
  report_id            VARCHAR(64)  NOT NULL,
  parse_version_base   INT          NOT NULL,
  new_parse_version    INT          NOT NULL,
  reviewer_id          VARCHAR(64)  NOT NULL,
  patches_json         CLOB         NOT NULL,
  idempotency_key      VARCHAR(64)  NOT NULL,
  created_at           TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_report_correction_idem UNIQUE (idempotency_key)
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

CREATE TABLE IF NOT EXISTS report_kb_document (
  doc_id           VARCHAR(64)  NOT NULL PRIMARY KEY,
  title            VARCHAR(256) NOT NULL,
  kb_version       VARCHAR(32)  NOT NULL,
  status           VARCHAR(16)  NOT NULL,
  authority_level  VARCHAR(16)  NOT NULL,
  url              VARCHAR(512),
  effective_from   TIMESTAMP(3) NOT NULL,
  effective_to     TIMESTAMP(3),
  created_at       TIMESTAMP(3) NOT NULL,
  updated_at       TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS report_kb_chunk (
  chunk_id         VARCHAR(64)  NOT NULL PRIMARY KEY,
  doc_id           VARCHAR(64)  NOT NULL,
  content          VARCHAR(2000) NOT NULL,
  title            VARCHAR(256),
  indicator_code   VARCHAR(32),
  specialty        VARCHAR(32),
  url              VARCHAR(512)
);

CREATE TABLE IF NOT EXISTS report_interpretation (
  interpretation_id  VARCHAR(64)  NOT NULL PRIMARY KEY,
  report_id          VARCHAR(64)  NOT NULL,
  parse_version      INT          NOT NULL,
  idempotency_key    VARCHAR(64)  NOT NULL,
  status             VARCHAR(32)  NOT NULL,
  result_code        INT,
  kb_version         VARCHAR(32)  NOT NULL,
  rule_version       VARCHAR(32)  NOT NULL,
  prompt_version     VARCHAR(32)  NOT NULL,
  parser_version     VARCHAR(32)  NOT NULL,
  schema_version     VARCHAR(32)  NOT NULL,
  created_at         TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_interpretation_idem UNIQUE (report_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS report_interpretation_block (
  id                 VARCHAR(64)   NOT NULL PRIMARY KEY,
  interpretation_id  VARCHAR(64)   NOT NULL,
  seq                INT           NOT NULL,
  block_id           VARCHAR(64)   NOT NULL,
  claim_type         VARCHAR(32)   NOT NULL,
  content            VARCHAR(2000) NOT NULL,
  evidence_ids       VARCHAR(1000),
  validation_status  VARCHAR(32)   NOT NULL
);

CREATE TABLE IF NOT EXISTS report_interpretation_evidence (
  id                 VARCHAR(64)   NOT NULL PRIMARY KEY,
  interpretation_id  VARCHAR(64)   NOT NULL,
  evidence_id        VARCHAR(80)   NOT NULL,
  doc_id             VARCHAR(64)   NOT NULL,
  title              VARCHAR(256),
  snippet            VARCHAR(1000),
  url                VARCHAR(512),
  CONSTRAINT uk_interpretation_evidence UNIQUE (interpretation_id, evidence_id)
);

CREATE TABLE IF NOT EXISTS report_event (
  event_id      VARCHAR(64)  NOT NULL PRIMARY KEY,
  report_id     VARCHAR(64)  NOT NULL,
  job_id        VARCHAR(64)  NOT NULL,
  sequence      BIGINT       NOT NULL,
  event_type    VARCHAR(32)  NOT NULL,
  epoch         BIGINT       NOT NULL,
  payload_json  CLOB         NOT NULL,
  created_at    TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_report_event_job_seq UNIQUE (job_id, sequence)
);

CREATE TABLE IF NOT EXISTS report_archive_outbox (
  outbox_id        VARCHAR(64)  NOT NULL PRIMARY KEY,
  report_id        VARCHAR(64)  NOT NULL,
  event_id         VARCHAR(64)  NOT NULL,
  schema_version   VARCHAR(32)  NOT NULL,
  status           VARCHAR(16)  NOT NULL,
  attempt_count    INT          NOT NULL,
  last_error_code  VARCHAR(64),
  payload_json     CLOB         NOT NULL,
  created_at       TIMESTAMP(3) NOT NULL,
  updated_at       TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_archive_outbox_event UNIQUE (event_id)
);

CREATE TABLE IF NOT EXISTS report_archive_record (
  event_id           VARCHAR(64)  NOT NULL PRIMARY KEY,
  report_id          VARCHAR(64)  NOT NULL,
  interpretation_id  VARCHAR(64)  NOT NULL,
  patient_id         VARCHAR(64)  NOT NULL,
  parse_version      INT          NOT NULL,
  identity_status    VARCHAR(16)  NOT NULL,
  indicator_count    INT          NOT NULL,
  created_at         TIMESTAMP(3) NOT NULL
);
